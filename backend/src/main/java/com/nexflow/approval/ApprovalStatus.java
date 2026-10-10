package com.nexflow.approval;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ApprovalStatus {
    PENDING("待审批"),
    APPROVED("已通过"),
    REJECTED("已驳回");

    private final String label;
}
