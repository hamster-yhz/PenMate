package com.penmate.backend.interfaces.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
/**
 * TraceId 过滤器。
 * <p>从请求头透传或生成链路追踪 ID，并写入 MDC/请求属性/响应头，保证上下游日志可关联。</p>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * 在每次请求中注入 TraceId。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        MDC.put(TRACE_ID_KEY, traceId);
        request.setAttribute(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 解析最终使用的 TraceId。
     * <p>优先使用请求头中的 traceId，缺失时自动生成 UUID。</p>
     *
     * @param headerTraceId 请求头传入的 TraceId
     * @return 可用的 TraceId
     */
    private String resolveTraceId(String headerTraceId) {
        if (headerTraceId == null || headerTraceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return headerTraceId.trim();
    }
}

