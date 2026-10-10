package com.nexflow.ticket;

import com.nexflow.sla.SlaStatus;
import com.nexflow.workflow.TicketState;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketListItemDto {

    private Long id;
    private String ticketNo;
    private String title;
    private Long typeId;
    private String typeName;
    private Priority priority;
    private TicketState status;
    private Long assigneeId;
    private String assigneeName;
    private Long deptId;
    private String deptName;
    private Long creatorId;
    private String creatorName;
    private LocalDateTime slaDueAt;
    private SlaStatus slaStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean demo;
}
