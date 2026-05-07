package com.penmate.backend.interfaces.api.auth;

import com.penmate.backend.application.auth.AuthApplicationService;
import com.penmate.backend.application.auth.command.LoginCommand;
import com.penmate.backend.application.auth.command.RefreshCommand;
import com.penmate.backend.interfaces.api.auth.dto.LoginDto;
import com.penmate.backend.interfaces.api.auth.dto.RefreshDto;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证与会话接口控制器。
 * <p>负责接收登录、登出、令牌刷新与当前用户信息查询请求，并将 HTTP 入参映射为认证应用服务命令。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    /**
     * 用户登录并签发访问令牌。
     * <p><b>业务目的：</b>校验邮箱密码并创建会话，返回前端可直接使用的访问令牌与刷新令牌。</p>
     * <p><b>流程主线：</b>接收登录凭证 -> 组装 {@link LoginCommand} -> 调用应用服务认证 -> 封装响应。</p>
     * <p><b>关键调用：</b>{@code authApplicationService.login(command, traceId)} 负责认证、会话创建与 token 生成。</p>
     * <p><b>异常与分支：</b>账号不存在、密码错误或账号状态异常时由应用层抛出业务异常。</p>
     * <p><b>副作用：</b>创建或更新会话记录。</p>
     *
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginDto dto,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        LoginCommand command = new LoginCommand(dto.getEmail(), dto.getPassword());
        return ApiResponse.success(authApplicationService.login(command, traceId), traceId);
    }

    /**
     * 用户登出并失效当前会话。
     * <p><b>业务目的：</b>主动吊销当前访问凭证，阻止后续继续使用该会话令牌。</p>
     * <p><b>流程主线：</b>读取 Authorization 令牌 -> 调用应用服务执行登出 -> 返回操作确认结果。</p>
     * <p><b>关键调用：</b>{@code authApplicationService.logout(authorization, traceId)} 完成会话失效处理。</p>
     * <p><b>异常与分支：</b>令牌格式非法或会话不存在时按应用层规则返回错误或幂等成功。</p>
     * <p><b>副作用：</b>更新会话状态（失效/注销）。</p>
     *
     * @param authorization 入参：authorization
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/logout")
    public ApiResponse<String> logout(@RequestHeader("Authorization") String authorization,
                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        authApplicationService.logout(authorization, traceId);
        return ApiResponse.success("ok", traceId);
    }

    /**
     * 使用刷新令牌换取新的访问凭证。
     * <p><b>业务目的：</b>在访问令牌过期前后，通过有效 refreshToken 延长登录态。</p>
     * <p><b>流程主线：</b>接收刷新令牌 -> 组装 {@link RefreshCommand} -> 调用应用服务刷新令牌 -> 返回新凭证。</p>
     * <p><b>关键调用：</b>{@code authApplicationService.refresh(command, traceId)} 完成令牌校验与重签发。</p>
     * <p><b>异常与分支：</b>刷新令牌过期、伪造或会话失效时返回认证失败。</p>
     * <p><b>副作用：</b>更新会话令牌版本或过期时间。</p>
     *
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@Valid @RequestBody RefreshDto dto,
                                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        RefreshCommand command = new RefreshCommand(dto.getRefreshToken());
        return ApiResponse.success(authApplicationService.refresh(command, traceId), traceId);
    }

    /**
     * 查询当前登录用户信息。
     * <p><b>业务目的：</b>基于当前访问令牌返回用户基础信息与权限上下文，供前端初始化会话态。</p>
     * <p><b>流程主线：</b>读取 Authorization 令牌 -> 调用应用服务解析用户身份 -> 封装响应。</p>
     * <p><b>关键调用：</b>{@code authApplicationService.me(authorization)} 完成令牌解析与用户聚合。</p>
     * <p><b>异常与分支：</b>令牌无效或过期时返回未认证错误。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     *
     * @param authorization 入参：authorization
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader("Authorization") String authorization,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toAuthMeView(authApplicationService.me(authorization)), traceId);
    }

    private Map<String, Object> toAuthMeView(Map<String, Object> source) {
        Map<String, Object> data = new LinkedHashMap<>(source);
        Object id = data.containsKey("userId") ? data.get("userId") : data.get("id");
        data.put("id", id);
        data.remove("userId");
        return data;
    }
}

