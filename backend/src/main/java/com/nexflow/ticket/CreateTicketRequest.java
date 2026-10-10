package com.nexflow.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateTicketRequest(
        @NotNull Long typeId,
        @NotBlank String title,
        String description,
        Priority priority,
        List<CustomFieldValueRequest> customFields,
        List<Long> attachmentIds) {

    public record CustomFieldValueRequest(@NotNull Long fieldId, String value) {
    }
}
