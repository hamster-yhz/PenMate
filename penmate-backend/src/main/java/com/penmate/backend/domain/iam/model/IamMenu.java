package com.penmate.backend.domain.iam.model;

import lombok.Data;
@Data
public class IamMenu {
    private Long id;
    private Long parentId;
    private String title;
    private String path;
    private Integer sortOrder;
    private String permissionCode;
    private Boolean visible;

}

