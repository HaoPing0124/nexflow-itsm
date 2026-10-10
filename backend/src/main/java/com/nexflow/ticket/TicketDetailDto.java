package com.nexflow.ticket;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TicketDetailDto extends TicketListItemDto {

    private String description;
    private Long version;
    private List<CustomFieldDto> customFields;
    private List<AttachmentDto> attachments;
    private List<CommentDto> comments;
    private List<StatusHistoryDto> statusHistory;
    private List<ApprovalBriefDto> approvals;
    private List<AssignmentTraceDto> assignmentTrace;
    private List<LegalActionDto> legalActions;

    @Data
    public static class CustomFieldDto {
        private Long fieldId;
        private String fieldName;
        private String fieldType;
        private String value;
    }

    @Data
    public static class AttachmentDto {
        private Long id;
        private String fileName;
        private Long size;
        private String contentType;
    }

    @Data
    public static class CommentDto {
        private Long id;
        private Long authorId;
        private String authorName;
        private String content;
        private boolean isInternal;
        private java.time.LocalDateTime createdAt;
    }

    @Data
    public static class StatusHistoryDto {
        private String fromStatus;
        private String toStatus;
        private String action;
        private String operatorName;
        private String note;
        private java.time.LocalDateTime createdAt;
    }

    @Data
    public static class ApprovalBriefDto {
        private Long id;
        private String status;
        private String approverName;
        private String comment;
        private java.time.LocalDateTime requestedAt;
        private java.time.LocalDateTime decidedAt;
    }

    @Data
    public static class AssignmentTraceDto {
        private String mode;
        private String ruleName;
        private String reason;
        private String assignedUserName;
        private java.time.LocalDateTime createdAt;
    }

    @Data
    public static class LegalActionDto {
        private String action;
        private String label;
    }
}
