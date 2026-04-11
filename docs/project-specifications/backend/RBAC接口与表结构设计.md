

# **RBAC后端架构**

## **1.0 数据库结构**

### **1.1 核心表结构（用户、角色、权限）**

此分节定义了身份与访问控制的基础表。

#### **users 表**

此表是用户身份和认证凭证的中心。

**表结构**

SQL

CREATE TABLE users (  
    id BIGINT UNSIGNED AUTO\_INCREMENT PRIMARY KEY COMMENT '用户唯一标识符，自增整数',  
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '用户的电子邮箱，作为登录和通信的主要标识',  
    password\_hash VARCHAR(255) NOT NULL COMMENT '使用bcrypt算法加密的密码哈希（包含盐）',  
    first\_name VARCHAR(100) COMMENT '用户的名字',  
    last\_name VARCHAR(100) COMMENT '用户的姓氏',  
    status ENUM('active', 'inactive', 'suspended') NOT NULL DEFAULT 'active' COMMENT '用户账户状态',  
    auth\_method ENUM('local', 'google', 'github') NOT NULL DEFAULT 'local' COMMENT '认证方式',  
    created\_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT\_TIMESTAMP COMMENT '记录创建时间，使用UTC时区',  
    updated\_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT\_TIMESTAMP ON UPDATE CURRENT\_TIMESTAMP COMMENT '记录最后更新时间，使用UTC时区',  
    last\_login\_at TIMESTAMP WITH TIME ZONE COMMENT '用户最后登录时间'  
) COMMENT '存储所有用户的核心身份和认证信息';

**技术修正：使用bcrypt进行密码存储和加盐**

* **问题识别**: 原始文档指出了使用强哈希算法（如bcrypt）的必要性，但未明确其加盐机制，这可能导致不安全的实现，例如创建一个独立且可能被重用的password\_salt列 1。  
* **解决方案**: 明确规定**不应**创建单独的salt列。现代密码哈希函数（如bcrypt）会自动为每个密码生成一个唯一的、密码学安全的盐，并将其嵌入到最终生成的哈希字符串中 2。一个标准的bcrypt哈希值，例如  
  $2b$12$abcdefghijklmnopqrstuv.wxyz...，其自身就包含了算法版本、计算成本（cost factor）、盐值和最终的哈希摘要 4。  
* **实施指令**: password\_hash列的数据类型应为VARCHAR(255)，足以存储完整的bcrypt输出。应用程序逻辑应使用标准库函数（例如，Node.js中的bcrypt.hash(password, saltRounds)），该函数在内部处理盐的生成 2。密码验证则通过  
  bcrypt.compare(plaintextPassword, storedHash)函数完成，该函数会自动从存储的哈希值中提取嵌入的盐进行比较 6。这种设计从根本上杜绝了盐的重用，简化了验证逻辑，并显著提升了系统的安全性。

#### **roles 与 permissions 表**

这两个表定义了RBAC模型的组成部分。将“角色”（权限的集合）与“权限”（原子操作，如users:create）分离，是构建灵活RBAC系统的基石 1。这种解耦设计允许权限在不同场景下复用（例如，使用同一个

users:create权限来保护UI上的“创建用户”菜单项及其对应的API端点），避免了为权限的微小变动而创建大量新角色。

**表结构**

SQL

CREATE TABLE roles (  
    id INT UNSIGNED AUTO\_INCREMENT PRIMARY KEY COMMENT '角色唯一标识符',  
    name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色的唯一名称 (例如: Admin, Editor, Viewer)',  
    description TEXT COMMENT '角色的详细描述'  
) COMMENT '定义系统中的用户角色';

CREATE TABLE permissions (  
    id INT UNSIGNED AUTO\_INCREMENT PRIMARY KEY COMMENT '权限唯一标识符',  
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '权限的唯一机器可读标识 (例如: users:create)',  
    description TEXT COMMENT '权限的人类可读描述'  
) COMMENT '定义系统中的所有原子权限';

### **1.2 连接表结构 (user\_roles, role\_permissions)**

这些表用于实现RBAC模型核心的多对多关系。

**表结构**

SQL

CREATE TABLE user\_roles (  
    user\_id BIGINT UNSIGNED NOT NULL,  
    role\_id INT UNSIGNED NOT NULL,  
    PRIMARY KEY (user\_id, role\_id),  
    FOREIGN KEY (user\_id) REFERENCES users(id) ON DELETE CASCADE,  
    FOREIGN KEY (role\_id) REFERENCES roles(id) ON DELETE CASCADE  
) COMMENT '用户与角色的多对多关联表';

CREATE TABLE role\_permissions (  
    role\_id INT UNSIGNED NOT NULL,  
    permission\_id INT UNSIGNED NOT NULL,  
    PRIMARY KEY (role\_id, permission\_id),  
    FOREIGN KEY (role\_id) REFERENCES roles(id) ON DELETE CASCADE,  
    FOREIGN KEY (permission\_id) REFERENCES permissions(id) ON DELETE CASCADE  
) COMMENT '角色与权限的多对多关联表';

**增强：优化的索引策略**

* **问题识别**: 原始设计中的复合主键，如(user\_id, role\_id)，仅能高效处理以user\_id开头的查询（例如，查找某用户的所有角色）。它无法高效处理反向查询（例如，查找拥有某角色的所有用户），这将导致数据库进行全表扫描，在数据量大时性能极低 7。  
* **解决方案**: 为每个连接表添加一个列顺序相反的二级索引，以优化双向查询性能。  
  * **user\_roles 表**:  
    SQL  
    CREATE INDEX idx\_user\_roles\_role\_id ON user\_roles (role\_id, user\_id);

  * **role\_permissions 表**:  
    SQL  
    CREATE INDEX idx\_role\_permissions\_permission\_id ON role\_permissions (permission\_id, role\_id);

* **实施理由**: 关系型数据库的查询优化器依赖索引来快速定位数据。一个索引的有效性取决于查询条件是否与索引的前导列匹配 9。为满足“查找拥有特定角色的所有用户”这类管理需求，必须建立一个以  
  role\_id为前导列的索引。增加这个反向索引是确保系统在所有关键访问模式下都能保持高性能的必要措施，而非一个可有可无的优化。

### **1.3 层级化菜单表结构 (menus)**

此表采用混合模型，以高效地存储和检索层级菜单数据。

**表结构**

SQL

CREATE TABLE menus (  
    id INT UNSIGNED AUTO\_INCREMENT PRIMARY KEY COMMENT '菜单项唯一标识符',  
    name VARCHAR(100) NOT NULL COMMENT '菜单显示文本',  
    icon VARCHAR(50) COMMENT '菜单图标',  
    path VARCHAR(255) COMMENT '前端路由或URL链接',  
    parent\_id INT UNSIGNED COMMENT '父菜单ID (邻接列表)，NULL为根节点',  
    sort\_order INT NOT NULL DEFAULT 0 COMMENT '同级菜单项的显示顺序',  
    lft INT NOT NULL COMMENT '嵌套集的左值',  
    rgt INT NOT NULL COMMENT '嵌套集的右值',  
    required\_permission\_id INT UNSIGNED COMMENT '访问此菜单项所需的权限ID',  
    INDEX idx\_parent\_id (parent\_id),  
    INDEX idx\_lft\_rgt (lft, rgt),  
    FOREIGN KEY (parent\_id) REFERENCES menus(id) ON DELETE SET NULL,  
    FOREIGN KEY (required\_permission\_id) REFERENCES permissions(id) ON DELETE SET NULL  
) COMMENT '存储层级化菜单信息，采用邻接列表和嵌套集混合模型';

**实施说明：维护嵌套集完整性**

* **挑战**: “邻接列表 \+ 嵌套集”混合模型虽然读取性能极高，但其写入操作的复杂性也相应增加。每当菜单项被添加、删除或移动时，多个节点的lft和rgt值必须被重新计算和更新。  
* **规定逻辑**: 应用程序必须实现一个函数，在对menus表执行任何写入操作后，重建整个菜单树的嵌套集值。为保证数据一致性，此过程应在单个数据库事务中完成。  
* **算法概述**:  
  1. 启动数据库事务。  
  2. 在邻接列表列（parent\_id, sort\_order）上执行写入操作（INSERT, UPDATE, DELETE）。  
  3. 执行一个递归函数或通用表表达式（CTE）查询，从根节点（parent\_id IS NULL）开始，按sort\_order遍历整个树。  
  4. 在遍历过程中，使用一个全局计数器为每个节点依次分配lft和rgt值。  
  5. 提交事务。  
* **理由**: 尽管这增加了写入操作的开销，但菜单数据的读取频率远高于写入频率 10。因此，牺牲写入性能以换取极致的读取性能，是针对此应用场景的正确工程权衡。提供明确的实现路径，可以确保开发团队正确地处理这一复杂逻辑。

### **1.4 综合模式参考表**

下表汇总了所有核心表的结构，作为开发团队实施数据库构建工作的最终技术规范。

| 表名 | 列名 | 数据类型 | 约束/索引 | 描述 |
| :---- | :---- | :---- | :---- | :---- |
| **users** | id | BIGINT UNSIGNED | PRIMARY KEY, AUTO\_INCREMENT | 用户唯一标识符 |
|  | email | VARCHAR(255) | NOT NULL, UNIQUE | 登录邮箱，唯一 |
|  | password\_hash | VARCHAR(255) | NOT NULL | 加密后的密码哈希（含盐） |
|  | first\_name | VARCHAR(100) |  | 名字 |
|  | last\_name | VARCHAR(100) |  | 姓氏 |
|  | status | ENUM(...) | NOT NULL, DEFAULT 'active' | 用户状态 |
|  | auth\_method | ENUM(...) | NOT NULL, DEFAULT 'local' | 认证方式 |
|  | created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT NOW() | 创建时间 (UTC) |
|  | updated\_at | TIMESTAMP WITH TIME ZONE | NOT NULL, DEFAULT NOW() | 更新时间 (UTC) |
|  | last\_login\_at | TIMESTAMP WITH TIME ZONE |  | 最后登录时间 |
| **roles** | id | INT UNSIGNED | PRIMARY KEY, AUTO\_INCREMENT | 角色唯一标识符 |
|  | name | VARCHAR(50) | NOT NULL, UNIQUE | 角色名称 |
|  | description | TEXT |  | 角色描述 |
| **user\_roles** | user\_id | BIGINT UNSIGNED | PRIMARY KEY, FOREIGN KEY | 用户ID |
|  | role\_id | INT UNSIGNED | PRIMARY KEY, FOREIGN KEY, INDEX | 角色ID |
| **permissions** | id | INT UNSIGNED | PRIMARY KEY, AUTO\_INCREMENT | 权限唯一标识符 |
|  | name | VARCHAR(100) | NOT NULL, UNIQUE | 权限机器可读名称 |
|  | description | TEXT |  | 权限描述 |
| **role\_permissions** | role\_id | INT UNSIGNED | PRIMARY KEY, FOREIGN KEY | 角色ID |
|  | permission\_id | INT UNSIGNED | PRIMARY KEY, FOREIGN KEY, INDEX | 权限ID |
| **menus** | id | INT UNSIGNED | PRIMARY KEY, AUTO\_INCREMENT | 菜单项唯一标识符 |
|  | name | VARCHAR(100) | NOT NULL | 菜单显示文本 |
|  | icon | VARCHAR(50) |  | 菜单图标 |
|  | path | VARCHAR(255) |  | 菜单链接路径 |
|  | parent\_id | INT UNSIGNED | FOREIGN KEY, INDEX | 父菜单ID (邻接列表) |
|  | sort\_order | INT | NOT NULL, DEFAULT 0 | 同级排序 |
|  | lft | INT | NOT NULL, INDEX | 嵌套集左值 |
|  | rgt | INT | NOT NULL, INDEX | 嵌套集右值 |
|  | required\_permission\_id | INT UNSIGNED | FOREIGN KEY | 访问所需权限ID |

## **2.0 安全架构与实施**

本节详细阐述保护API所需的具体安全机制。

### **2.1 认证：基于JWT的流程**

1. 用户通过POST /api/v1/auth/login端点提交凭证（邮箱和密码）。  
2. 服务器验证凭证的有效性。  
3. 验证通过后，服务器生成一个签名的JWT。该JWT的载荷（payload）必须包含标准声明（iss \- 签发者, aud \- 受众, sub \- 主题/用户ID, exp \- 过期时间），以及一个唯一的令牌标识符jti。  
4. 服务器将JWT返回给客户端。  
5. 客户端在后续所有对受保护资源的请求中，必须在Authorization请求头中携带此JWT，格式为Bearer \<token\>。

### **2.2 JWT撤销：使用Redis的拒绝列表实现**

* **问题识别**: 纯粹无状态的JWT在其过期前无法被撤销，这在令牌泄露、用户登出或权限变更等场景下会构成严重的安全风险 1。  
* **解决方案**: 采用一个基于Redis（高性能内存缓存）的“拒绝列表”（Denylist）来追踪已被撤销的令牌。此方案以最小的状态开销，解决了关键的安全缺陷 14。  
* **实施细节**:  
  * **Redis数据结构**: 使用简单的键值对存储。  
  * **键命名约定**: 键应使用JWT的jti声明，并添加命名空间前缀，例如：jwt:denylist:c25276e4-2253-40d0-b153-986c75a13f4c。  
  * **值**: 值可以是简单的标记，如字符串"revoked"。  
  * **自动过期**: 当一个令牌被加入拒绝列表时（例如用户登出），必须使用Redis的SETEX命令。该命令在设置键的同时为其指定一个生存时间（TTL）。这个TTL必须被精确计算为该JWT的exp（过期时间戳）与当前时间戳之差。此机制确保了拒绝列表中的条目会与它所对应的JWT同步失效，从而构成一个自清理系统，避免了列表无限增长导致的性能和存储问题 16。

### **2.3 授权：中间件逻辑流程**

保护API端点的授权中间件必须遵循以下严格的逻辑流程：

1. 拦截进入受保护端点的请求。  
2. 从Authorization请求头中提取Bearer令牌。  
3. 执行JWT的基础验证：检查签名、是否过期、签发者和受众是否符合预期。  
4. 从令牌载荷中提取jti声明。  
5. 查询Redis：EXISTS jwt:denylist:\<jti\>。如果键存在，立即中断请求，返回401 Unauthorized错误。  
6. 从令牌载荷中提取sub声明（用户ID）。  
7. 根据用户ID加载其拥有的所有权限集合（此步骤结果应被缓存以提高性能）。  
8. 将用户权限集合与端点所需的权限（例如，POST /users需要users:create权限）进行比对。  
9. 如果用户拥有所需权限，则将请求传递给下一个处理程序（控制器）。  
10. 如果用户不具备所需权限，则中断请求，返回403 Forbidden错误。

## **3.0 RESTful API规范 (v1)**

本节为开发团队提供完整的“API契约”，其详尽程度远超原始文档和参考图片。

### **3.1 API约定**

* **版本控制**: 所有端点均以/api/v1/为前缀。  
* **标准化的分页列表响应格式**: 所有返回资源列表的端点（例如GET /users）必须使用以下JSON结构，以便客户端能正确渲染分页控件 18。  
  JSON  
  {  
    "data": \[  
      { "id": 1, "email": "user1@example.com" },  
      { "id": 2, "email": "user2@example.com" }  
    \],  
    "pagination": {  
      "totalItems": 127,  
      "totalPages": 13,  
      "currentPage": 2,  
      "pageSize": 10  
    }  
  }

* **标准化的错误响应格式**: 所有错误响应（4xx和5xx状态码）必须遵循统一的格式，以便客户端进行标准化的错误处理 21。  
  JSON  
  {  
    "status": 404,  
    "errorCode": "RESOURCE\_NOT\_FOUND",  
    "message": "User with ID 123 not found.",  
    "details": null  
  }

  对于验证错误（400 Bad Request），details字段将包含一个包含字段级问题的数组：  
  JSON  
  {  
    "status": 400,  
    "errorCode": "VALIDATION\_ERROR",  
    "message": "Input validation failed.",  
    "details": \[  
      { "field": "email", "message": "Email address is not valid." },  
      { "field": "password", "message": "Password must be at least 8 characters long." }  
    \]  
  }

* **HTTP状态码使用指南**:

| 状态码 | 含义 | 使用场景 |
| :---- | :---- | :---- |
| 200 OK | 请求成功 | GET, PUT, PATCH 成功，或不创建新资源的POST成功。 |
| 201 Created | 资源已创建 | POST请求成功创建新资源。 |
| 204 No Content | 无内容 | DELETE请求成功，或PUT/PATCH成功但无需返回实体。 |
| 400 Bad Request | 错误的请求 | 请求体格式错误、参数无效或输入验证失败。 |
| 401 Unauthorized | 未认证 | 缺少、无效或已过期的JWT。 |
| 403 Forbidden | 禁止访问 | 用户已认证，但缺少执行该操作所需的权限。 |
| 404 Not Found | 未找到资源 | 请求的URI对应的资源不存在。 |
| 409 Conflict | 冲突 | 尝试创建已存在的唯一资源（例如，重复的邮箱）。 |
| 500 Internal Server Error | 服务器内部错误 | 服务器端发生未预期的错误。 |

### **3.2 资源：用户 (/api/v1/users)**

#### **GET /api/v1/users \- 获取用户列表**

* **描述**: 获取一个分页的用户列表，支持过滤和排序。  
* **所需权限**: users:list  
* **请求**:  
  * **查询参数**:  
    * page (integer, optional, default: 1): 请求的页码。  
    * pageSize (integer, optional, default: 10): 每页的项目数。  
    * sortBy (string, optional, default: 'id'): 排序字段 (例如: email, created\_at)。  
    * sortOrder (string, optional, default: 'asc'): 排序顺序 (asc 或 desc)。  
    * filter\[status\] (string, optional): 按用户状态过滤 (active, inactive, suspended)。  
* **成功响应 (200 OK)**:  
  JSON  
  {  
    "data":,  
    "pagination": {  
      "totalItems": 50,  
      "totalPages": 5,  
      "currentPage": 1,  
      "pageSize": 10  
    }  
  }

* **错误响应**:  
  * **401 Unauthorized**:  
    JSON  
    { "status": 401, "errorCode": "UNAUTHENTICATED", "message": "Authentication token is missing or invalid.", "details": null }

  * **403 Forbidden**:  
    JSON  
    { "status": 403, "errorCode": "INSUFFICIENT\_PERMISSIONS", "message": "User does not have permission 'users:list'.", "details": null }

#### **POST /api/v1/users \- 创建新用户**

* **描述**: 创建一个新用户。  
* **所需权限**: users:create  
* **请求**:  
  * **请求体**:  
    JSON  
    {  
      "email": "new.user@example.com",  
      "password": "aVeryStrongP@ssw0rd\!",  
      "first\_name": "New",  
      "last\_name": "User",  
      "roles":   
    }

* **成功响应 (201 Created)**:  
  * **Headers**: Location: /api/v1/users/103  
  * **响应体**:  
    JSON  
    {  
      "id": 103,  
      "email": "new.user@example.com",  
      "first\_name": "New",  
      "last\_name": "User",  
      "status": "active",  
      "created\_at": "2023-10-27T12:00:00Z"  
    }

* **错误响应**:  
  * **400 Bad Request (Validation Error)**:  
    JSON  
    {  
      "status": 400,  
      "errorCode": "VALIDATION\_ERROR",  
      "message": "Input validation failed.",  
      "details": \[  
        { "field": "email", "message": "Email format is invalid." },  
        { "field": "password", "message": "Password must contain at least one uppercase letter, one number, and one special character." }  
      \]  
    }

  * **409 Conflict (Email Exists)**:  
    JSON  
    { "status": 409, "errorCode": "RESOURCE\_CONFLICT", "message": "A user with email 'new.user@example.com' already exists.", "details": null }

#### **GET /api/v1/users/{id} \- 获取指定ID的用户**

* **描述**: 获取单个用户的详细信息。  
* **所需权限**: users:read  
* **请求**:  
  * **路径参数**: id (integer, required): 用户的唯一ID。  
* **成功响应 (200 OK)**:  
  JSON  
  {  
    "id": 101,  
    "email": "jane.doe@example.com",  
    "first\_name": "Jane",  
    "last\_name": "Doe",  
    "status": "active",  
    "auth\_method": "local",  
    "created\_at": "2023-10-27T10:00:00Z",  
    "updated\_at": "2023-10-27T11:00:00Z",  
    "last\_login\_at": "2023-10-27T11:30:00Z",  
    "roles": \[  
      { "id": 1, "name": "Admin" },  
      { "id": 5, "name": "Editor" }  
    \]  
  }

* **错误响应**:  
  * **404 Not Found**:  
    JSON  
    { "status": 404, "errorCode": "RESOURCE\_NOT\_FOUND", "message": "User with ID 999 not found.", "details": null }

#### **PUT /api/v1/users/{id} \- 全量更新用户信息**

* **描述**: 替换一个现有用户的全部信息。  
* **所需权限**: users:update  
* **请求**:  
  * **路径参数**: id (integer, required): 用户的唯一ID。  
  * **请求体**:  
    JSON  
    {  
      "email": "jane.d.updated@example.com",  
      "first\_name": "Jane",  
      "last\_name": "Doe-Smith",  
      "status": "inactive"  
    }

* **成功响应 (200 OK)**:  
  JSON  
  {  
    "id": 101,  
    "email": "jane.d.updated@example.com",  
    "first\_name": "Jane",  
    "last\_name": "Doe-Smith",  
    "status": "inactive",  
    "updated\_at": "2023-10-27T14:00:00Z"  
  }

* **错误响应**:  
  * **404 Not Found**:  
    JSON  
    { "status": 404, "errorCode": "RESOURCE\_NOT\_FOUND", "message": "User with ID 999 not found.", "details": null }

#### **PATCH /api/v1/users/{id} \- 部分更新用户信息**

* **描述**: 更新一个现有用户的一个或多个字段。  
* **所需权限**: users:update  
* **请求**:  
  * **路径参数**: id (integer, required): 用户的唯一ID。  
  * **请求体**:  
    JSON  
    {  
      "status": "suspended"  
    }

* **成功响应 (200 OK)**:  
  JSON  
  {  
    "id": 101,  
    "email": "jane.d.updated@example.com",  
    "first\_name": "Jane",  
    "last\_name": "Doe-Smith",  
    "status": "suspended",  
    "updated\_at": "2023-10-27T14:30:00Z"  
  }

#### **DELETE /api/v1/users/{id} \- 删除用户**

* **描述**: 永久删除一个用户。  
* **所需权限**: users:delete  
* **请求**:  
  * **路径参数**: id (integer, required): 用户的唯一ID。  
* **成功响应 (204 No Content)**:  
  * **响应体**: (空)  
* **错误响应**:  
  * **404 Not Found**:  
    JSON  
    { "status": 404, "errorCode": "RESOURCE\_NOT\_FOUND", "message": "User with ID 999 not found.", "details": null }

#### **POST /api/v1/users/{id}/roles \- 为用户分配角色**

* **描述**: 为指定用户添加一个角色。  
* **所需权限**: users:assign\_roles  
* **请求**:  
  * **路径参数**: id (integer, required): 用户的唯一ID。  
  * **请求体**:  
    JSON  
    {  
      "role\_id": 2  
    }

* **成功响应 (200 OK)**:  
  JSON  
  {  
    "message": "Role assigned successfully.",  
    "current\_roles": \[  
      { "id": 1, "name": "Admin" },  
      { "id": 2, "name": "Viewer" }  
    \]  
  }

* **错误响应**:  
  * **409 Conflict (Role Already Assigned)**:  
    JSON  
    { "status": 409, "errorCode": "RESOURCE\_CONFLICT", "message": "User already has role with ID 2.", "details": null }

#### **DELETE /api/v1/users/{userId}/roles/{roleId} \- 从用户移除角色**

* **描述**: 从指定用户身上移除一个角色。  
* **所需权限**: users:assign\_roles  
* **请求**:  
  * **路径参数**:  
    * userId (integer, required): 用户的唯一ID。  
    * roleId (integer, required): 角色的唯一ID。  
* **成功响应 (204 No Content)**:  
  * **响应体**: (空)  
* **错误响应**:  
  * **404 Not Found**:  
    JSON  
    { "status": 404, "errorCode": "RESOURCE\_NOT\_FOUND", "message": "User-role association not found for user 101 and role 99.", "details": null }

### **3.3 资源：角色 (/api/v1/roles)**

#### **GET /api/v1/roles \- 获取角色列表**

* **描述**: 获取一个分页的角色列表。  
* **所需权限**: roles:list  
* **请求**:  
  * **查询参数**:  
    * page (integer, optional, default: 1): 请求的页码。  
    * pageSize (integer, optional, default: 10): 每页的项目数。  
* **成功响应 (200 OK)**:  
  JSON  
  {  
    "data":,  
    "pagination": {  
      "totalItems": 3,  
      "totalPages": 1,  
      "currentPage": 1,  
      "pageSize": 10  
    }  
  }

#### **POST /api/v1/roles \- 创建新角色**

* **描述**: 创建一个新角色。  
* **所需权限**: roles:create  
* **请求**:  
  * **请求体**:  
    JSON  
    {  
      "name": "Viewer",  
      "description": "只能查看数据，不能修改"  
    }

* **成功响应 (201 Created)**:  
  * **Headers**: Location: /api/v1/roles/4  
  * **响应体**:  
    JSON  
    {  
      "id": 4,  
      "name": "Viewer",  
      "description": "只能查看数据，不能修改"  
    }

* **错误响应**:  
  * **409 Conflict**:  
    JSON  
    { "status": 409, "errorCode": "RESOURCE\_CONFLICT", "message": "A role with name 'Viewer' already exists.", "details": null }

#### **GET /api/v1/roles/{id} \- 获取指定ID的角色**

* **描述**: 获取单个角色的详细信息，包括其拥有的权限。  
* **所需权限**: roles:read  
* **请求**:  
  * **路径参数**: id (integer, required): 角色的唯一ID。  
* **成功响应 (200 OK)**:  
  JSON  
  {  
    "id": 1,  
    "name": "Super Admin",  
    "description": "拥有系统所有权限",  
    "permissions": \[  
      { "id": 1, "name": "users:list" },  
      { "id": 2, "name": "users:create" }  
    \]  
  }

* **错误响应**:  
  * **404 Not Found**:  
    JSON  
    { "status": 404, "errorCode": "RESOURCE\_NOT\_FOUND", "message": "Role with ID 99 not found.", "details": null }

#### **PUT /api/v1/roles/{id} \- 更新角色信息**

* **描述**: 替换一个现有角色的信息。  
* **所需权限**: roles:update  
* **请求**:  
  * **路径参数**: id (integer, required): 角色的唯一ID。  
  * **请求体**:  
    JSON  
    {  
      "name": "Content Manager",  
      "description": "负责所有内容的创建和编辑"  
    }

* **成功响应 (200 OK)**:  
  JSON  
  {  
    "id": 2,  
    "name": "Content Manager",  
    "description": "负责所有内容的创建和编辑"  
  }

#### **DELETE /api/v1/roles/{id} \- 删除角色**

* **描述**: 永久删除一个角色。  
* **所需权限**: roles:delete  
* **请求**:  
  * **路径参数**: id (integer, required): 角色的唯一ID。  
* **成功响应 (204 No Content)**:  
  * **响应体**: (空)

#### **POST /api/v1/roles/{id}/permissions \- 为角色分配权限**

* **描述**: 为指定角色添加一个权限。  
* **所需权限**: roles:assign\_permissions  
* **请求**:  
  * **路径参数**: id (integer, required): 角色的唯一ID。  
  * **请求体**:  
    JSON  
    {  
      "permission\_id": 3  
    }

* **成功响应 (200 OK)**:  
  JSON  
  {  
    "message": "Permission assigned successfully.",  
    "current\_permissions": \[  
      { "id": 1, "name": "users:list" },  
      { "id": 2, "name": "users:create" },  
      { "id": 3, "name": "users:read" }  
    \]  
  }

#### **DELETE /api/v1/roles/{roleId}/permissions/{permissionId} \- 从角色移除权限**

* **描述**: 从指定角色身上移除一个权限。  
* **所需权限**: roles:assign\_permissions  
* **请求**:  
  * **路径参数**:  
    * roleId (integer, required): 角色的唯一ID。  
    * permissionId (integer, required): 权限的唯一ID。  
* **成功响应 (204 No Content)**:  
  * **响应体**: (空)

### **3.4 资源：权限 (/api/v1/permissions)**

#### **GET /api/v1/permissions \- 获取所有可用权限**

* **描述**: 获取系统中定义的所有原子权限列表，用于在UI中进行角色配置。  
* **所需权限**: permissions:list  
* **请求**:  
  * **查询参数**: (无)  
* **成功响应 (200 OK)**:  
  JSON  
  {  
    "data": \[  
      { "id": 1, "name": "users:list", "description": "Allows viewing a list of users." },  
      { "id": 2, "name": "users:create", "description": "Allows creating a new user." },  
      { "id": 3, "name": "users:read", "description": "Allows viewing a single user's details." },  
      { "id": 4, "name": "users:update", "description": "Allows updating a user." },  
      { "id": 5, "name": "users:delete", "description": "Allows deleting a user." },  
      { "id": 6, "name": "users:assign\_roles", "description": "Allows assigning/revoking roles for a user." },  
      { "id": 7, "name": "roles:list", "description": "Allows viewing a list of roles." }  
    \],  
    "pagination": {  
      "totalItems": 20,  
      "totalPages": 1,  
      "currentPage": 1,  
      "pageSize": 20  
    }  
  }

### **3.5 资源：菜单 (/api/v1/menus)**

#### **GET /api/v1/menus \- 获取完整菜单树**

* **描述**: 获取完整的层级化菜单结构，用于后台管理界面。  
* **所需权限**: menus:manage  
* **请求**: (无参数)  
* **成功响应 (200 OK)**:  
  JSON  
    },  
    {  
      "id": 2,  
      "name": "User Management",  
      "icon": "fas fa-users",  
      "path": null,  
      "children": \[  
        {  
          "id": 3,  
          "name": "User List",  
          "icon": null,  
          "path": "/users/list",  
          "children":  
        }  
      \]  
    }  
  \]

#### **POST /api/v1/menus \- 创建新菜单项**

* **描述**: 创建一个新的菜单项。此操作后服务器将重建嵌套集。  
* **所需权限**: menus:manage  
* **请求**:  
  * **请求体**:  
    JSON  
    {  
      "name": "Create User",  
      "icon": "fas fa-user-plus",  
      "path": "/users/create",  
      "parent\_id": 2,  
      "sort\_order": 1,  
      "required\_permission\_id": 2  
    }

* **成功响应 (201 Created)**:  
  * **Headers**: Location: /api/v1/menus/4  
  * **响应体**:  
    JSON  
    {  
      "id": 4,  
      "name": "Create User",  
      "icon": "fas fa-user-plus",  
      "path": "/users/create",  
      "parent\_id": 2,  
      "sort\_order": 1,  
      "required\_permission\_id": 2  
    }

#### **PUT /api/v1/menus/{id} \- 更新菜单项**

* **描述**: 更新一个菜单项的详细信息。  
* **所需权限**: menus:manage  
* **请求**:  
  * **路径参数**: id (integer, required): 菜单项的唯一ID。  
  * **请求体**:  
    JSON  
    {  
      "name": "All Users",  
      "path": "/users/all"  
    }

* **成功响应 (200 OK)**:  
  JSON  
  {  
    "id": 3,  
    "name": "All Users",  
    "icon": null,  
    "path": "/users/all",  
    "parent\_id": 2,  
    "sort\_order": 0,  
    "required\_permission\_id": 1  
  }

#### **PATCH /api/v1/menus/{id}/move \- 移动菜单项**

* **描述**: 移动一个菜单项到新的父节点下或改变其排序。此操作后服务器将重建嵌套集。  
* **所需权限**: menus:manage  
* **请求**:  
  * **路径参数**: id (integer, required): 要移动的菜单项ID。  
  * **请求体**:  
    JSON  
    {  
      "new\_parent\_id": 1,  
      "new\_sort\_order": 0  
    }

* **成功响应 (200 OK)**:  
  JSON  
  {  
    "message": "Menu item 3 moved successfully."  
  }

#### **DELETE /api/v1/menus/{id} \- 删除菜单项**

* **描述**: 永久删除一个菜单项及其所有子项。此操作后服务器将重建嵌套集。  
* **所需权限**: menus:manage  
* **请求**:  
  * **路径参数**: id (integer, required): 菜单项的唯一ID。  
* **成功响应 (204 No Content)**:  
  * **响应体**: (空)

### **3.6 资源：用户资料 (/api/v1/profile)**

#### **GET /api/v1/profile/menus \- 获取当前用户的动态菜单**

* **描述**: 根据当前认证用户的权限，动态生成并返回一个为其量身定制的层级化菜单结构。  
* **所需权限**: (已认证即可)  
* **请求**: (无参数)  
* **成功响应 (200 OK)**:  
  JSON  
    },  
    {  
      "id": 2,  
      "name": "User Management",  
      "icon": "fas fa-users",  
      "path": "/users",  
      "children": \[  
        {  
          "id": 3,  
          "name": "User List",  
          "icon": null,  
          "path": "/users/list",  
          "children":  
        },  
        {  
          "id": 4,  
          "name": "Create User",  
          "icon": null,  
          "path": "/users/create",  
          "children":  
        }  
      \]  
    },  
    {  
      "id": 5,  
      "name": "Settings",  
      "icon": "fas fa-cogs",  
      "path": "/settings",  
      "children":  
    }  
  \]

* **错误响应**:  
  * **401 Unauthorized**:  
    JSON  
    { "status": 401, "errorCode": "UNAUTHENTICATED", "message": "Authentication token is missing or invalid.", "details": null }

## **附录A：初始数据植入**

为了确保系统在新部署后能够立即投入管理使用，以下数据应作为数据库的初始种子数据。

#### **默认角色**

| 名称 | 描述 |
| :---- | :---- |
| Super Admin | 拥有系统所有权限的超级管理员。 |
| Content Editor | 负责内容管理，可以创建和编辑相关资源，但不能管理用户和权限。 |
| Read Only User | 只能查看数据，不能进行任何修改操作。 |

#### **默认权限**

| 权限名称 | 描述 |
| :---- | :---- |
| users:list | 允许查看用户列表 |
| users:create | 允许创建新用户 |
| users:read | 允许查看单个用户详情 |
| users:update | 允许更新用户信息 |
| users:delete | 允许删除用户 |
| users:assign\_roles | 允许为用户分配或撤销角色 |
| roles:list | 允许查看角色列表 |
| roles:create | 允许创建新角色 |
| roles:update | 允许更新角色信息 |
| roles:delete | 允许删除角色 |
| roles:assign\_permissions | 允许为角色分配或撤销权限 |
| permissions:list | 允许查看所有可用权限列表 |
| menus:manage | 允许管理（增删改查移动）所有菜单项 |

#### **角色-权限映射**

| 角色 | 应分配的权限 |
| :---- | :---- |
| Super Admin | users:list, users:create, users:read, users:update, users:delete, users:assign\_roles, roles:list, roles:create, roles:update, roles:delete, roles:assign\_permissions, permissions:list, menus:manage |
| Content Editor | (根据具体业务定义，例如 posts:create, posts:update) |
| Read Only User | users:list, users:read, roles:list |

