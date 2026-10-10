package com.nexflow.workflow;

public record TransitionRequest(String note, Long assigneeId, String resolution) {

    public static TransitionRequest of(String note) {
        return new TransitionRequest(note, null, null);
    }
}
