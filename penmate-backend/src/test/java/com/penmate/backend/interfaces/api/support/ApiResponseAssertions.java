package com.penmate.backend.interfaces.api.support;

import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public final class ApiResponseAssertions {

    private ApiResponseAssertions() {
    }

    public static ResultActions assertSuccessMeta(ResultActions actions, String traceId) throws Exception {
        return actions
                .andExpect(jsonPath("$.meta.traceId").value(traceId))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    public static ResultActions assertErrorEnvelope(ResultActions actions,
                                                    int status,
                                                    String errorCode,
                                                    String traceId) throws Exception {
        return actions
                 .andExpect(jsonPath("$.data.status").value(status))
                 .andExpect(jsonPath("$.data.errorCode").value(errorCode))
                 .andExpect(jsonPath("$.meta.traceId").value(traceId))
                 .andExpect(jsonPath("$.meta.timestamp").exists());
    }
}
