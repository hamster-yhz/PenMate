package com.penmate.backend.domain.iam.model;

import lombok.Data;
@Data
public class IamPermission {
    private Long id;
    private String name;
    private String code;
    private String module;
    private String description;

}

