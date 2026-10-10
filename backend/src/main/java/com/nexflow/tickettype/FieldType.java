package com.nexflow.tickettype;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FieldType {
    TEXT("文本"),
    NUMBER("数字"),
    SELECT("下拉选择"),
    DATE("日期"),
    BOOLEAN("布尔"),
    USER("用户");

    private final String label;
}
