package com.nexflow.assignment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AssignmentMode {
    AUTO("自动"),
    MANUAL("手动");

    private final String label;
}
