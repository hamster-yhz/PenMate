package com.penmate.backend.interfaces.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessLogAspectTest {

    @Test
    void should_not_throw_when_signature_is_null() throws Throwable {
        BusinessLogAspect aspect = new BusinessLogAspect();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        when(joinPoint.getSignature()).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.aroundBusiness(joinPoint);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_still_proceed_when_signature_to_short_string_throws() throws Throwable {
        BusinessLogAspect aspect = new BusinessLogAspect();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenThrow(new RuntimeException("signature-broken"));
        when(joinPoint.proceed()).thenReturn("ok-2");

        Object result = aspect.aroundBusiness(joinPoint);

        assertThat(result).isEqualTo("ok-2");
    }
}

