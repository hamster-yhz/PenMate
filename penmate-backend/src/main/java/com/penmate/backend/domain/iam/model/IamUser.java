package com.penmate.backend.domain.iam.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class IamUser {
    private Long id;
    private String email;
    private String passwordHash;
    private String displayName;
    private Integer status;
    private String authMethod;
    private LocalDateTime lastLoginAt;

}

