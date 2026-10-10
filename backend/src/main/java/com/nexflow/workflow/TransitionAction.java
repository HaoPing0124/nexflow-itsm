package com.nexflow.workflow;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransitionAction {
    CLASSIFY("classify", "分类"),
    ASSIGN("assign", "分配"),
    AUTO_ASSIGN("auto_assign", "自动分配"),
    START("start", "开始处理"),
    RESOLVE("resolve", "提交解决"),
    CONFIRM("confirm", "确认关闭"),
    REOPEN("reopen", "重新打开"),
    CANCEL("cancel", "取消工单");

    private final String code;
    private final String label;

    public static TransitionAction fromCode(String code) {
        for (TransitionAction action : values()) {
            if (action.getCode().equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知操作: " + code);
    }
}
