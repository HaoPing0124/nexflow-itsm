package com.nexflow.assignment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AssignmentStrategy {
    ROUND_ROBIN("轮询"),
    LEAST_WORKLOAD("最少负载"),
    MOST_SKILLED("技能优先");

    private final String label;
}
