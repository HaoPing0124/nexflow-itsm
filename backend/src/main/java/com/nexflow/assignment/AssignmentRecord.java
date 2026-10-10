package com.nexflow.assignment;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_record")
@Data
@NoArgsConstructor
public class AssignmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "rule_name", length = 128)
    private String ruleName;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(name = "assigned_by_id")
    private Long assignedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssignmentMode mode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
