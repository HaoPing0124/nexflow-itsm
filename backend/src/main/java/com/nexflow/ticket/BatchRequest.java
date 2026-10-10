package com.nexflow.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchRequest(
        @NotBlank String action,
        @NotEmpty List<Long> ids,
        Long assigneeId) {

    public record FailedItem(Long id, String message) {
    }

    public record BatchResult(List<Long> success, List<FailedItem> failed) {
    }
}
