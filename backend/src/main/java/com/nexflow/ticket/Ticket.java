package com.nexflow.ticket;

import com.nexflow.workflow.TicketState;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket")
@Data
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", nullable = false, unique = true, length = 32)
    private String ticketNo;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketState status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "group_dept_id")
    private Long groupDeptId;

    @Column(name = "sla_first_due_at")
    private LocalDateTime slaFirstDueAt;

    @Column(name = "sla_resolve_due_at")
    private LocalDateTime slaResolveDueAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    /**
     * 乐观锁版本号。
     *
     * 必须保持未初始化（null）。Spring Data JPA 的 isNew() 在实体存在 @Version 时
     * 以 version == null 判定"新实体"；若在此初始化为 0L，新工单会被误判为已存在，
     * save() 将走 merge() 分支——merge 返回一个新的受管实例，原对象 id 仍为 null，
     * 导致后续以 ticket.getId() 写子表（自定义字段值等）时插入 NULL 而失败。
     * 交由 Hibernate 在首次 insert 时赋值为 0。
     */
    @Version
    @Column(name = "opt_version")
    private Long version;

    @Column(nullable = false)
    private boolean demo = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TicketState.CREATED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
