package com.penmate.backend.interfaces.api.style.dto;

import jakarta.validation.constraints.NotNull;

public class SwitchStyleDto {

    @NotNull
    private Long toStyleId;
    private Boolean warningConfirmed;
    private String reason;

    public Long getToStyleId() {
        return toStyleId;
    }

    public void setToStyleId(Long toStyleId) {
        this.toStyleId = toStyleId;
    }

    public Boolean getWarningConfirmed() {
        return warningConfirmed;
    }

    public void setWarningConfirmed(Boolean warningConfirmed) {
        this.warningConfirmed = warningConfirmed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

