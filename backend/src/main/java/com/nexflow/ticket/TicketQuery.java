package com.nexflow.ticket;

public record TicketQuery(
        String status,
        Long typeId,
        Long deptId,
        String priority,
        Long assigneeId,
        String keyword,
        String view,
        String sort,
        int page,
        int size) {
}
