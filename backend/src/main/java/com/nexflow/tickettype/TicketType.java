package com.nexflow.tickettype;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ticket_type")
@Data
@NoArgsConstructor
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = false;

    @Column(name = "auto_assign", nullable = false)
    private boolean autoAssign = false;

    @Column(name = "sla_policy_id")
    private Long slaPolicyId;

    @Column(name = "allowed_dept_ids", length = 255)
    private String allowedDeptIds;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    private List<CustomFieldDef> fields = new ArrayList<>();
}
