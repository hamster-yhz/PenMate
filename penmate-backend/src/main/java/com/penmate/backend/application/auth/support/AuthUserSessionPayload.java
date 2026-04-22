package com.penmate.backend.application.auth.support;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AuthUserSessionPayload {

    private Long userId;

    private String email;

    private String displayName;

    private Integer status;

    private List<Map<String, Object>> roles;

    private List<Map<String, Object>> permissions;

    private String refreshJti;
}

