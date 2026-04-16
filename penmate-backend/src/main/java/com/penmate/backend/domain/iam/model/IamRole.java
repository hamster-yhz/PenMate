package com.penmate.backend.domain.iam.model;

import lombok.Data;
@Data
public class IamRole {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isSystem;

}

