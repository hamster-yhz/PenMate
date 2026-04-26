package com.penmate.backend.interfaces.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
/**
 * 业务日志切面。
 * <p>统一拦截接口层与应用服务层方法，记录进入、退出、耗时与异常，便于问题排查与性能观测。</p>
 */
public class BusinessLogAspect {

    /**
     * 环绕记录业务方法执行日志。
     *
     * @param joinPoint 被拦截的方法连接点
     * @return 原方法返回值
     * @throws Throwable 原方法执行过程中抛出的异常
     */
    @Around("execution(* com.penmate.backend.interfaces.api..*.*(..)) || execution(* com.penmate.backend.application..*ApplicationService.*(..))")
    public Object aroundBusiness(ProceedingJoinPoint joinPoint) throws Throwable {
        String signature = "unknown";
        try {
            Signature rawSignature = joinPoint.getSignature();
            if (rawSignature != null) {
                signature = rawSignature.toShortString();
            }
        } catch (Exception ignore) {
            // fallback to default signature
        }
        long start = System.currentTimeMillis();
//        log.info("[ENTER] method={}", signature);
        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            String resultType = result == null ? "null" : result.getClass().getSimpleName();
//            log.info("[EXIT] method={} costMs={} resultType={}", signature, cost, resultType);
            return result;
        } catch (Exception ex) {
            long cost = System.currentTimeMillis() - start;
            log.error("[FAIL] method={} costMs={} error={}", signature, cost, ex.getMessage(), ex);
            throw ex;
        }
    }
}

