package com.nexflow.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {
    ACTIVE("启用"),
    DISABLED("停用");

    private final String label;
}
