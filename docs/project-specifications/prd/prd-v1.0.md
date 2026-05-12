你现在是资深 AI 应用架构师 + 后端工程师 + LLM Agent 系统工程师 + AI 小说创作系统架构师。

请基于现有代码，重构我的 AI 小说创作系统。

注意：

- 系统已经存在 Main Orchestrator 主编排相关类，不要推翻重写。
- 本次重构应围绕现有 Main Orchestrator 做增强、解耦和扩展。
- 当前项目后端主链路已落在 `application/agent/orchestration`、`application/agent/orchestration/preflight`、`application/agent/context`、`application/agent/tool` 等层内，必须在现有 DDD 分层下演进。
- 系统还没有完整 Story Bible 故事圣经模块，需要从零设计并接入，但接入方式必须适配现有单主编排架构，不得引入破坏现有架构边界的多 Agent 主控方案。
- 目标不是简单修 bug，而是升级成稳定、可扩展、可控、适合长篇小说创作的 Agent 系统。
- 本 PRD 以适配当前 PenMate 项目为目标，不是抽象的通用 Agent 平台方案。
- 严格遵守以下落地约束：
  - 改表直接改 SQL migration，不绕过现有 migration 体系。
  - 不保留兼容代码、不保留兼容接口、不做新旧实现混用；全链路切换后旧实现直接删除。
  - 不额外增加工程化内容，不引入与当前项目无关的平台化设施。
  - 关键类、关键方法、关键步骤要补充注释；关键业务步骤必须打日志。
  - 更改调用链时必须全链路同步，包括接口 DTO、应用服务、主编排、上下文快照、SSE/WebSocket 事件、前端运行态展示。
  - 新增 Story Bible、新增前置意图判断、新增工具、新增 skill 是主要目标；如果原始表述涉及多 Agent 协作中枢、额外 supervisor、并行多主控等破坏现有架构的内容，必须改写成适配当前主编排架构的实现。
  - 必须适配 DDD：domain 负责领域模型与规则，application 负责编排与用例，interfaces 负责 API 与 DTO，infrastructure 负责持久化/实时事件/外部网关。
  - 前端必须补充用户感知，让用户始终知道 agent 当前在做什么、卡在哪一步、是否在等待审批、是否在调用工具、是否在整理故事圣经。

请严格按以下需求进行分析、设计和改造。

1. 总体流程

目标流程：

用户输入
→ Agent Preflight / Task Profiler 前置判定任务画像
→ Prompt Composer 提示词组合器，动态注入 skill 系统提示词
→ Context Builder 上下文构建器，构建故事圣经上下文包
→ Main Orchestrator 主编排流程，复用已有相关类
→ 进入 Main Agent 执行
→ Main Agent 在单一 tool loop 内使用 Planner / Writer / Editor / Checker 等子能力提示词与工具
→ 子能力基于 tool / skill prompt / RAG / Story Bible Context 执行
→ Draft Generation 编辑正文工具
→ Quality Review 质量审查工具
→ Todo Planner 待办事项规划工具
→ Todo CRUD
→ Todo 落库到当前会话强关联表
→ 输出结果
→ 需要时生成 Story Bible 更新建议
→ 前端实时展示当前运行状态

当前项目中的适配要求：

- Main Orchestrator 对应现有 `AgentGenerationWorkflow` + `AgentGenerationWorkflowDispatcher` 主链路，继续作为唯一长流程协调中心。
- Task Profiler 不单独升级成第二个主控 Agent，而是演进现有 preflight 能力，使其成为主编排前置判定阶段。
- Prompt Composer 不独立夺权，而是演进现有 prompt 组装点与系统提示词提供器。
- Context Builder 不负责 prompt 注入，只负责 Story Bible / style / RAG / session context 的路由与裁剪。
- Main Agent 仍然是单执行入口，不改造成多主 Agent 竞争架构。
- Planner / Writer / Editor / Checker 以“skill prompt + tool 暴露策略 + execution profile”的方式接入，不要求引入新的常驻子 Agent 进程。
- Draft Generation、Quality Review、Todo Planner 都应作为明确工具能力接入现有 tool loop。
- Story Bible 作为长期创作知识库。
- Memory Store 作为短期运行状态。
- 所有模块职责要清晰，避免一个 Agent 什么都做。
- 前端必须消费主链路状态，至少覆盖 planning / executing / tool_call / waiting_approval / done / failed 等用户可见状态。

2. 核心模块

需要包含以下模块：

- Task Profiler：任务画像前置判定模块，演进现有 preflight
- Prompt Composer：提示词组合器
- Skill Prompt Injector：skill 系统提示词动态注入能力，可并入 Prompt Composer
- Story Bible Manager：故事圣经管理模块
- Story Bible Updater：故事圣经更新模块
- Context Builder：故事圣经上下文构建器
- Main Orchestrator：主编排器，复用现有类
- Main Agent：主执行 Agent，复用现有类
- Planner Skill / Tool：规划子能力
- Writer Skill / Tool：写作子能力
- Editor Skill / Tool：编辑子能力
- Quality Review Skill / Tool：检查子能力 / 质量审查
- Todo Planner Tool：待办规划工具
- Todo CRUD Service：待办事项增删改查
- Todo Persistence：待办事项持久化
- RAG Retriever：检索模块
- Memory Store：短期记忆状态
- Output Formatter：输出格式化
- Task Runtime Status Publisher：任务运行状态发布模块
- Workbench Runtime Presenter：前端工作台状态展示适配模块

当前项目落地约束：

- Main Orchestrator 落在 application 层，不允许把复杂流程回推到 controller。
- Tool 定义、tool schema 暴露、tool 治理策略必须沿用现有 tool definition / runtime / gateway 结构演进。
- Story Bible 首次引入时先作为新模块接入 context 路由扩展点，不破坏现有 session / turn / task 恢复能力。
- 新增 Todo 模块与 Story Bible 模块都必须通过 DDD 分层落地，不允许把 SQL、JSON 拼接、业务规则直接堆在 controller 或前端。

3. Task Profiler

Task Profiler 是用户输入后的第一步。

在当前项目中，它应视为现有 preflight 阶段的增强版，而不是新增第二套并行编排。

它负责把用户自然语言请求转换成结构化任务画像。

不要只判断一个简单 intent，而是要识别：

- 用户想做什么
- 是否是续写、改写、润色、审稿、生成大纲、生成角色、生成世界观、规划待办、管理待办、查询故事圣经、更新故事圣经等任务
- 是否有多个任务意图
- 是否有硬约束，比如不能崩人设、不能改剧情、不能提前剧透
- 是否需要启用 Planner、Writer、Editor、Checker 等 skill prompt 或工具暴露策略
- 是否需要启用 Draft Generation、Quality Review、Todo Planner 等工具
- 是否需要故事圣经上下文
- 是否需要更新故事圣经
- 是否需要 RAG、style、session memory、chapter context
- 用户期望的输出形式
- 当前请求是否存在高风险设定变更，需要审批或显式确认

要求：

- 支持多标签意图。
- 不要频繁打断用户。
- 歧义不影响执行时继续执行。
- 歧义严重影响结果时再请求澄清。
- Task Profiler 的输出要能被 Prompt Composer、Context Builder 和 Main Orchestrator 继续使用。
- Task Profiler 输出必须是稳定、可序列化、可落日志的结构化对象。
- Task Profiler 输出必须进入任务快照链路，便于 session recovery 与审批恢复后续跑。
- 关键判定步骤打日志，至少包括：行为类型、execution profile、是否启用 story bible、是否启用 rag、是否需要审批。
- 关键类、方法、步骤写注释，说明该模块只是主编排前置判定，不承担后续生成执行。

4. Prompt Composer

Prompt Composer 是 Task Profiler 之后的第二步。

在当前项目中，它是现有系统提示词加载与 prompt 装配能力的增强版，应围绕现有 execution profile、system prompt bundle、structured prompt block 继续演进。

它负责根据任务画像动态组合提示词，并注入对应的 skill 系统提示词。

职责：

- 根据任务画像选择需要启用的 prompt modules。
- 根据任务画像选择需要启用的 skills。
- 动态注入 Planner / Writer / Editor / Checker / Todo Planner 等 skill 系统提示词。
- 处理多个 prompt module 的优先级。
- 处理 prompt 冲突。
- 生成最终系统提示词结构。
- 与现有 execution profile 机制对齐，避免前置画像结果与执行 profile 脱节。

注意：

- Prompt Composer 不负责构建故事圣经上下文包。
- 故事圣经上下文包由 Context Builder 负责。
- Prompt Composer 不直接写库，不直接调用工具。
- Prompt Composer 必须复用现有 system prompt provider / prompt assembler 体系，不允许再平行造一套无关的 prompt 引擎。

skill 系统提示词需要模块化：

- Planner Skill：负责剧情规划、章节规划、场景 beat 拆分。
- Writer Skill：负责根据规划和上下文写正文。
- Editor Skill：负责润色、改写、节奏调整、情绪增强。
- Checker Skill：负责检查人设、剧情、世界观、时间线、伏笔等一致性。
- Todo Planner Skill：负责把复杂创作任务拆成待办事项。
- Story Bible Skill：负责故事圣经草案抽取、规范化建议与更新建议生成。

Prompt 冲突处理优先级：

用户明确要求
> 安全和合规规则
> 不可违背核心设定
> 人设一致性
> 剧情因果
> 当前任务目标
> skill 专项要求
> 文风要求
> 节奏和爽点优化
> 其他润色偏好

实现约束：

- 提示词必须模块化、版本化、可配置。
- 避免巨型 prompt 字符串直接硬编码在业务流程中。
- 重要 prompt 组合结果必须能落日志或快照，便于排障与回放。
- 关键组合步骤写注释，说明每个 block 的来源与用途。

5. Story Bible 故事圣经

新增 Story Bible Manager。

Story Bible 不是一个大文本，不允许整块塞入 prompt。

它应该是结构化、可检索、可更新、可版本化的小说长期知识库。

Story Bible 至少需要管理：

- 作品基础信息
- 核心创意
- 类型定位
- 主题
- 基调与文风
- 世界观
- 力量体系
- 组织 / 阵营
- 地点
- 角色档案
- 角色当前状态
- 人物关系
- 时间线
- 主线大纲
- 分卷大纲
- 章节大纲
- 场景摘要
- 伏笔 / 悬念 / 冲突
- 不可违背设定
- 读者已知信息
- 角色已知信息
- 文风指南
- 禁止事项
- 用户偏好

规则：

- 用户明确确认的内容才是 canon。
- 模型推断出的内容默认是 proposed，不是 canon。
- 高风险内容不得自动覆盖。
- 故事圣经要能支持后续查询、激活、更新和版本管理。
- Story Bible 是新增模块，但必须通过现有 context provider 扩展点接入，不破坏主编排架构。
- Story Bible 的领域规则应落在 domain/application 对应模块中，不要直接以 controller + SQL 拼接方式实现。
- Story Bible 查询和写入都要打业务日志。
- Story Bible 的关键类、关键方法、关键状态转换必须有注释。

6. Story Bible 初始化

需要支持从以下输入创建故事圣经：

- 一句话创意
- 已有大纲
- 已有章节正文
- 角色设定
- 世界观设定
- 零散笔记
- 混合输入

如果用户只有一句话创意，系统应能生成基础故事圣经草案。

如果用户提供已有章节，系统应能从正文中抽取角色、地点、事件、设定、伏笔、人物关系、读者已知信息、角色已知信息和文风特征。

初始化结果不要直接写死，应先作为 proposed_story_bible。

适配要求：

- 初始抽取可以通过 preflight 决策 + 专用 Story Bible 工具/skill 完成，但最终写入仍由主编排控制。
- 初始化草案必须保留来源信息，至少标明来源文本、抽取章节、推断等级。
- 初始化落地时不要与现有 cards / outline / chapter 强耦合成单表大对象，应保留独立 Story Bible 领域边界，同时允许引用现有 novel/card/chapter 数据作为来源。
- 若当前项目尚未建 Story Bible 存储表，则直接新增 SQL migration 建表，不保留兼容中间层。

7. Story Bible 更新

每次生成、改写、续写、审稿后，都应判断是否需要更新故事圣经。

Story Bible Updater 负责分析新内容，并生成更新建议。

要求：

- 不要盲目写入。
- 低风险信息可以自动记录。
- 高风险信息必须等待确认。
- 高风险信息包括改变主角核心人设、改变世界观底层规则、改变主线方向、改变重要人物关系、解决重大伏笔、让角色知道重大秘密等。
- 故事圣经更新要能区分“已确认设定”和“模型推断设定”。
- 更新建议必须进入主编排可追踪链路，不允许绕过 Main Orchestrator 直接写库。
- 如果涉及审批，前端必须显式展示“故事圣经更新待确认”。
- 更新步骤必须打日志，至少记录：update proposal 数量、风险级别、是否自动写入、是否等待确认。

8. Story Bible 版本化

关键故事设定必须支持版本化。

要求：

- 能区分某个设定在哪一章之前有效，在哪一章之后发生变化。
- 能避免旧设定污染新生成。
- 能根据当前章节、当前时间线选择正确版本。
- RAG 和 Context Builder 都必须尊重版本信息。

例如：

角色 A 在第 40 章之前不知道男主真实身份。
第 44 章之后角色 A 已经知道男主真实身份。

系统生成第 42 章时，不能让角色 A 知道秘密。
系统生成第 45 章时，可以让角色 A 知道秘密。

适配要求：

- 版本选择逻辑应归属于 Story Bible / Context Builder 领域规则，不要散落在前端或 controller。
- 如果现有章节 ID、session context、task context 已保存 chapter_id，则版本过滤必须复用该链路，不再另起上下文字段体系。
- 变更章节边界时必须全链路同步：上下文构建、RAG 过滤、工具检查、前端审批展示、最终落库。

9. Context Builder

Context Builder 是 Prompt Composer 之后的第三步。

在当前项目中，它应演进现有上下文路由与 Story Bible context provider 机制，而不是把 prompt 注入、tool 调度、状态发布都混进来。

它负责根据 TaskProfile、已激活 skills、Story Bible、RAG 结果构建故事圣经上下文包。

职责：

- 根据任务类型选择上下文。
- 根据已激活 skill 选择上下文。
- 根据当前章节选择有效版本。
- 控制上下文长度。
- 对上下文进行排序、裁剪、去重。
- 标记冲突信息。
- 标记缺失信息。
- 标记可能过期的信息。
- 不允许每次加载完整故事圣经。

上下文优先级：

用户本轮明确要求
> 硬约束
> 当前场景上下文
> 角色当前状态
> 核心不可违背设定
> 当前章节摘要
> 当前卷状态
> 相关伏笔
> 相关前文
> 世界观补充
> 文风样本
> 低相关资料

适配要求：

- Context Builder 只负责 context package，不负责 prompt 冲突处理。
- Context Builder 结果必须能进入 task context snapshot，便于恢复与回放。
- Context Builder 必须兼容“暂无 Story Bible”的项目状态，允许返回空或 noop 上下文，但要标记缺失来源。
- Story Bible context / style snapshot / rag snapshot / cards snapshot 需要统一治理，不允许新旧上下文来源并行混用。
- 关键上下文路由步骤必须打日志。
- 关键方法必须写注释，说明为什么包含/排除某类上下文。

10. Main Orchestrator

Main Orchestrator 复用现有类并增强能力。

在当前项目中，Main Orchestrator 的事实核心是现有生成工作流与异步分发链路，必须继续由 application/orchestration 层统一承担。

需要支持：

- 接收 TaskProfile
- 接收 Prompt Composer 结果
- 接收 Context Builder 结果
- 调度 Main Agent
- 调度 Planner / Writer / Editor / Checker skills
- 调度 Draft Generation Tool
- 调度 Quality Review Tool
- 调度 Todo Planner Tool
- 调度 Todo CRUD Service
- 处理异常和 fallback
- 输出最终结果
- 推送实时运行状态给前端工作台

默认工作流：

- 接收用户输入
- 执行 Task Profiler
- 执行 Prompt Composer
- 执行 Context Builder
- 进入 Main Agent
- 运行需要的 skills
- 需要时调用 Draft Generation
- 需要时调用 Quality Review
- 需要时调用 Todo Planner
- 需要时持久化 Todo
- 需要时生成 Story Bible 更新建议
- 推送运行状态 / 工具状态 / 审批状态
- 输出结果

不同任务可动态跳过或增加步骤。

强约束：

- 不引入第二主编排中心，不引入新的 Supervisor 主工作流。
- 所有写操作必须通过主编排可追踪地触发。
- 变更调用链时，接口 DTO、task snapshot、审批恢复、状态事件、前端状态展示必须一起修改，不能新旧实现混用。
- 编排关键节点必须打日志，至少包括 workflow start / preflight done / context routed / execution started / waiting approval / result published / failed。
- 编排关键类与长流程步骤必须补充注释。

11. Main Agent 与子能力

Main Agent 是执行层。

它不负责：

- 前置任务画像
- prompt 组合
- 故事圣经上下文构建

Main Agent 接收：

- TaskProfile
- 最终系统提示词
- Story Bible 上下文包
- 可用 skill / tool 列表
- Main Orchestrator 的执行指令

子能力职责：

Planner：

- 设计章节目标
- 拆分场景 beat
- 明确冲突、转折、情绪曲线
- 明确要推进的信息
- 明确结尾钩子
- 可生成待办事项候选

Writer：

- 根据规划和上下文生成正文
- 遵守故事圣经和硬约束
- 保持人物口吻、场景氛围、叙事风格
- 不擅自改核心设定
- 不擅自引入重大新设定

Editor：

- 优化语言
- 优化节奏
- 增强情绪
- 增强爽点、悬念、压迫感
- 删除重复表达
- 保持原意和核心剧情不变

Checker：

- 检查人设一致性
- 检查剧情逻辑
- 检查世界观设定
- 检查时间线
- 检查角色知识边界
- 检查读者信息边界
- 检查伏笔是否误删或提前暴露

适配要求：

- 子能力优先以 skill prompt / execution profile / tool policy 暴露，不默认要求创建多个相互调用的常驻子 Agent。
- 对外表现上仍是一个 Main Agent 执行当前 turn。
- 子能力切换对用户是可感知的，前端状态文案应能反映“正在规划 / 正在写作 / 正在审查 / 正在整理设定”等阶段。

12. Draft Generation Tool

新增 Draft Generation Tool，作为明确工具。

职责：

- 生成正文初稿
- 根据已有正文生成改写稿
- 根据编辑指令生成修订稿
- 根据章节 / 场景规划生成正文
- 根据编辑建议生成最终正文
- 保留用户硬约束
- 保留核心剧情结果
- 遵守故事圣经上下文包

要求：

- Draft Generation 只负责生成或编辑正文。
- 不负责质量评分。
- 质量评分由 Quality Review Tool 负责。
- 作为现有 tool loop 的明确工具能力接入。
- 工具调用过程必须产生实时状态事件，让前端知道当前在“生成正文 / 改写正文 / 套用修订”。
- 关键工具处理类与工具入参/出参写注释。
- 调用成功/失败/等待审批都要打日志。

13. Quality Review Tool

新增 Quality Review Tool，作为明确工具。

职责：

- 对正文、改写稿、章节内容或用户提供文本进行质量审查。
- 检查是否遵守用户要求。
- 检查是否遵守故事圣经。
- 检查人设、剧情、设定、节奏、文风。
- 判断是否需要修订。
- 给出修订建议。

要求：

- 质量审查不要只靠 Main Agent 自评。
- 审查结果需要能被后续修订流程使用。
- 修订时必须保留用户明确要求、核心剧情、角色设定、世界观规则和已确认故事圣经内容。
- 自动修订最多控制在有限轮次内，避免无限循环。
- 审查结果必须结构化，可持久化到任务结果或消息渲染块中。
- 前端应明确展示“正在审查质量 / 已发现问题 / 需要确认修订”。
- 审查关键步骤必须打日志。

14. Todo Planner Tool

新增 Todo Planner Tool。

职责：

- 把复杂小说创作任务拆解为待办事项。
- 把 Planner / Checker / Quality Review 发现的问题转成待办事项。
- 把用户明确提出的后续修改要求转成待办事项。
- 支持当前会话内任务追踪。

适用场景：

- 用户要求规划后续修改。
- 用户要求列修改待办。
- 用户要求规划接下来写什么。
- 质量审查发现多个待修复问题。
- 章节规划需要转成执行清单。
- 编排流程中存在未完成创作步骤。

要求：

- Todo Planner Tool 只生成 TodoPlan。
- 不直接写库。
- 写库由 Todo CRUD Service 负责。
- TodoPlanner 的生成结果应支持用户审阅后落库，避免未经确认的批量任务污染会话。
- 若触发自动建 Todo，必须可配置，并打日志。

15. Todo CRUD 与持久化

新增 Todo CRUD Service。

能力：

- 创建 Todo
- 批量创建 Todo
- 更新 Todo
- 删除 Todo
- 查询 Todo
- 标记完成
- 按状态筛选
- 按 session 查询

要求：

- Todo 必须和当前会话强关联。
- 当前项目若无 `session_todos` 或等价表，则直接新增 SQL migration 建表。
- Todo 不允许写回前端本地缓存作为唯一真实来源，必须以后端 SQL 为准。
- Todo 需要支持软删除。
- 当前会话结束后，Todo 仍能通过 session_id 找回。
- Todo 写操作由 Main Orchestrator 控制，避免 Main Agent 随意写库。
- 改表直接改 SQL migration，不做兼容表、不做双写、不做旧接口保留。
- 若引入新的 Todo API / DTO / repository / mapper，则旧链路同步删除，不能新旧实现混用。
- 所有写库失败必须有错误处理与日志。

16. RAG

小说 RAG 不应只是普通语义检索，需要支持结构化检索 + 向量检索混合。

RAG 应支持检索：

- 故事圣经条目
- 角色状态
- 伏笔状态
- 场景摘要
- 章节摘要
- 世界观设定
- 人物关系
- 时间线
- 文风样本

要求：

- 检索结果需要说明为什么被检索出来。
- 检索结果需要能判断是否过期。
- 检索结果需要能判断是否符合当前章节版本。
- 排序不能只看向量相似度，还要考虑任务类型、当前章节、已激活 skill、角色相关性、伏笔相关性、canon 重要性和用户明确提及内容。
- Story Bible 是结构化知识库，RAG 是检索机制。Story Bible 条目可以向量化进入 RAG，但最终进入 prompt 前，必须由 Context Builder 统一治理。
- RAG 仍需遵守现有 DDD 分层与既有 rag 领域，不要为 Story Bible 再平行造一套无边界检索体系。
- 检索步骤要打日志，记录 top_k、过滤条件、命中来源。

17. Memory Store

新增结构化 Memory Store。

Story Bible 与 Memory Store 的关系：

- Story Bible 管理稳定设定、世界观、角色档案、主线规划。
- Memory Store 管理运行时状态、最近上下文、临时约束、草稿版本、当前 Todo 摘要。
- Story Bible Updater 负责把重要 Memory 提炼进 Story Bible。

要求：

- 不要把所有短期信息都写入 Story Bible。
- 只有对后续创作长期有影响的信息才进入 Story Bible。
- Memory Store 应与现有 agent_tasks / agent_task_contexts / agent_messages / 恢复快照链路协同设计，优先复用已有运行态事实来源。
- Memory Store 不能成为新的“万能 JSON 垃圾箱”；字段要清晰、边界要清晰。
- 关键写入步骤必须打日志。

18. 数据结构

请定义清晰、可序列化的数据模型。

至少需要覆盖：

- TaskProfile
- PromptModule
- PromptPlan
- SkillPrompt
- StoryBible
- StoryBibleEntry
- StoryBibleVersion
- StoryBibleUpdateProposal
- ContextPackage
- WritingPlan
- DraftResult
- QualityReport
- RevisionResult
- TodoPlan
- TodoItem
- TodoCrudOperation
- CharacterProfile
- CharacterState
- RelationshipState
- PlotThread
- SceneSummary
- ChapterSummary
- TimelineEvent
- LoreEntry
- StyleGuide
- RuntimeStatusView
- ToolCallStatusView
- StoryBibleApprovalView

要求：

- 字段命名稳定。
- 不同模块不要用不同名字表达同一概念。
- 数据结构需要支持多项目、多会话、多章节。
- 重要中间产物要有类型定义。
- 与现有 session / turn / task / requestContext / approval / renderBlocks 结构命名保持一致，避免平行概念。
- 业务 ID 保持当前项目风格，接口层继续按字符串业务 ID 暴露，不新增数值/字符串双制兼容。

19. 配置化

不要把策略写死。

以下内容应支持配置：

- prompt module 列表
- skill prompt 列表
- prompt module 优先级
- skill 启用策略
- Story Bible section 激活策略
- RAG top_k
- 上下文长度预算
- 是否启用 Draft Generation Tool
- 是否启用 Quality Review Tool
- 是否启用 Todo Planner Tool
- 是否自动保存 Todo
- 是否启用自动修订
- 最大修订轮数
- 质量审查阈值
- task_type 对应 workflow
- Story Bible 是否自动写入
- 高风险更新是否必须人工确认
- 前端状态文案映射

要求：

- 配置化应尽量贴合现有 prompt provider / tool definition source / policy 结构。
- 不额外增加与当前项目无关的复杂工程化平台，不引入独立规则中心、独立工作流引擎等超额设计。

20. 兼容要求

要求：

- 保留现有 Main Orchestrator 相关类，并优先复用。
- Main Agent 保留，但需要新增接收 TaskProfile、PromptPlan、ContextPackage 的能力。
- 现有意图判断逻辑重构为 Task Profiler。
- 现有动态提示词加载逻辑重构为 Prompt Composer。
- 现有上下文路由能力重构为可承载 Story Bible 的 Context Builder。
- 无 Story Bible 时不能直接失败，应支持 fallback。

无 Story Bible fallback：

- 从用户输入中临时抽取上下文。
- 使用 session memory。
- 标记缺失上下文。
- 可生成 proposed_story_bible。
- 未确认内容不得直接写入 canon。

重要补充：

- “兼容要求”只指对现有主架构复用，不代表保留旧接口、旧表结构、旧调用链。
- 一旦新链路落地，旧逻辑、旧 DTO、旧 mapper、旧状态字段、旧前端 fallback 直接删除。
- 不允许为了平滑过渡而长期保留双实现。

21. 前端用户感知与运行态展示

前端必须增强用户感知，让用户始终知道 agent 正在做什么。

至少需要展示：

- 当前会话状态
- 当前任务状态
- 当前阶段文案，如“正在分析请求”“正在规划章节”“正在生成正文”“正在审查质量”“正在整理故事圣经”“等待审批”“已完成”“执行失败”
- 当前工具调用状态
- 当前审批阻塞状态
- 当前是否关联文风、插件、模型、章节
- 关键失败原因或需用户操作的下一步提示

要求：

- 复用现有 Workbench / chat / task runtime 展示结构增强，不推翻前端三栏工作台架构。
- 运行态展示必须对齐后端实时事件与任务状态，至少覆盖 generation.started / generation.status / generation.tool_call / generation.waiting_approval / generation.done / generation.failed。
- 状态文案、审批卡片、工具调用卡片、故事圣经更新确认卡片要统一风格。
- 当调用链发生变更时，前端状态展示、会话恢复、流式重连、审批后续跑必须全链路同步，不允许前端仍按旧事件或旧字段解析。
- 关键前端状态处理逻辑写注释，关键状态切换点打前端日志，便于联调。

22. 代码实现要求

要求：

- 先阅读现有项目结构和 Main Orchestrator 调用链。
- 不要大面积破坏式重写。
- 优先新增独立模块，再逐步替换现有逻辑。
- 每个模块职责清晰。
- 避免巨型函数。
- 避免巨型 prompt 字符串。
- prompt 模板化、版本化、模块化。
- skill 系统提示词模块化配置。
- 外部 LLM 调用需要封装，便于替换模型。
- toolcall / RAG / memory / story_bible / prompt_composer / todo_service 不要强耦合。
- Story Bible 要有独立存储层或抽象接口。
- Todo 要有明确数据模型和持久化逻辑。
- 所有数据库写入要有错误处理。
- 所有新增/修改调用链必须符合 DDD 分层。
- 关键类、关键方法、关键步骤写注释。
- 关键业务步骤打日志。
- 改表直接改 SQL migration。
- 不要额外增加工程化内容。
- 不能新旧实现混用。

23. 安全与稳定性

要求：

- 用户明确要求永远优先。
- 人设一致性、核心设定、剧情因果优先于爽点和文风。
- 不要自动覆盖高风险故事圣经内容。
- 不要让模型擅自把推断内容当成 canon。
- 所有推断内容必须标注为 proposed 或 assumption。
- 故事圣经缺失关键内容时，可以临时生成 assumption，但不得写入 canon，除非用户确认或配置允许。
- Todo CRUD、Story Bible Update 等写库工具必须有明确写入边界和错误处理。
- 审批恢复、会话恢复、流式重连都必须保持状态一致性。
- 发生失败时要能给前端明确状态反馈，不允许用户只看到“卡住了”。

24. SQL migration 与数据演进要求

要求：

- 所有表结构变更直接修改/新增 SQL migration。
- 不允许临时在代码里做 schema 兼容、字段兜底、双写过渡。
- Story Bible、Todo、状态扩展、审批扩展如需新增表，直接纳入现有 migration 体系。
- migration 命名、注释风格、表注释、字段注释应延续当前项目规范。
- 新表与现有 `agent_sessions`、`agent_turns`、`agent_messages`、`agent_tasks`、`agent_task_contexts`、`agent_task_results`、审批链路保持清晰关联。
- 所有新增字段必须与恢复链路、编排链路、前端展示链路同步更新。

25. 关键原则

- 不要让一个 Agent 什么都做。
- Task Profiler 负责判定任务画像。
- Prompt Composer 负责组合提示词和注入 skill 系统提示词。
- Context Builder 负责构建故事圣经上下文包。
- Main Orchestrator 复用已有类，负责主流程调度。
- Main Agent 负责执行。
- Draft Generation 是工具。
- Quality Review 是工具。
- Todo Planner 是工具。
- Todo CRUD 负责待办事项增删改查。
- Todo 必须落库到当前会话强关联表。
- 不要把故事圣经全部塞进 prompt。
- 不要只靠向量检索管理长篇小说状态。
- 不要让 prompt module 无序拼接。
- 不要生成完就结束，必须支持质量审查和必要修订。
- 所有关键状态必须可追踪来源。
- 所有策略尽量配置化。
- Story Bible 中用户明确确认的内容才是 canon。
- 模型推断出的内容默认是 proposed，不是 canon。
- 改表直接改 SQL。
- 不保留兼容代码或兼容接口。
- 不额外增加工程化内容。
- 更改调用链必须全链路同步。
- 新增意图判断、Story Bible、tool、skill 是本次改造重点。
- 如与现有架构冲突，应优先适配现有单主编排架构，而不是引入多 Agent 主控。
- 必须适配 DDD。
- 必须让前端用户感知当前状态。
- 关键类、方法、步骤写注释，关键业务步骤打日志。

26. 最终目标

请把系统升级成：

一个以 TaskProfile 为入口，
以 Prompt Composer 为 skill 系统提示词动态注入中心，
以 Context Builder 为故事圣经上下文包构建中心，
以现有 Main Orchestrator 为流程调度核心，
以 Main Agent 为执行入口，
以 Planner / Writer / Editor / Checker 为专业子能力，
以 Draft Generation Tool 为正文生成与编辑工具，
以 Quality Review Tool 为质量审查工具，
以 Todo Planner Tool 为待办规划工具，
以 Todo CRUD Service 为待办事项管理能力，
以当前会话强关联 SQL 表为待办事项持久化载体，
以 Story Bible 为长期创作知识库，
以 Memory Store 为短期运行态知识缓存，
以运行状态实时发布 + Workbench 状态展示为用户感知出口的
AI 长篇小说创作 Agent 系统。

重点解决：

- Task Profiler 前置任务画像
- Prompt Composer 动态注入 skill 系统提示词
- Story Bible 从零建立
- Context Builder 构建故事圣经上下文包
- 复用现有 Main Orchestrator
- Main Agent 工具化执行
- Draft Generation 编辑正文工具
- Quality Review 质量审查工具
- Todo Planner 待办事项规划工具
- Todo CRUD
- Todo 落库到当前会话强关联 SQL
- 多意图任务画像
- 上下文治理
- prompt 冲突
- 叙事状态管理
- 角色状态管理
- 伏笔状态管理
- 世界观一致性
- 模块化与可扩展性
- DDD 下的清晰分层
- 用户可感知的 agent 当前状态展示
- 注释、日志、审批、恢复、流式链路的一致性