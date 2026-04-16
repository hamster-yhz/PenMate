package com.penmate.backend.interfaces.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class BusinessLogAspect {

    @Around("execution(* com.penmate.backend.interfaces.api..*.*(..)) || execution(* com.penmate.backend.application..*ApplicationService.*(..))")
    public Object aroundBusiness(ProceedingJoinPoint joinPoint) throws Throwable {
        String signature = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();
        log.info("[ENTER] method={}", signature);
        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            String resultType = result == null ? "null" : result.getClass().getSimpleName();
            log.info("[EXIT] method={} costMs={} resultType={}", signature, cost, resultType);
            return result;
        } catch (Exception ex) {
            long cost = System.currentTimeMillis() - start;
            log.error("[FAIL] method={} costMs={} error={}", signature, cost, ex.getMessage(), ex);
            throw ex;
        }
    }
}

