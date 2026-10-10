package com.nexflow.sla;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SlaStatus {
    OK("正常"),
    AT_RISK("有风险"),
    BREACHED("已超时");

    private final String label;
}
