package com.nexflow.workflow;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TicketState {
    CREATED("已创建"),
    CLASSIFIED("已分类"),
    PENDING_APPROVAL("待审批"),
    ASSIGNED("已分配"),
    IN_PROGRESS("处理中"),
    RESOLVED("已解决"),
    CLOSED("已关闭"),
    CANCELED("已取消");

    private final String label;
}
