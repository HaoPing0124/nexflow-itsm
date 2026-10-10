package com.nexflow.assignment;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "assignment_rule")
@Data
@NoArgsConstructor
public class AssignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "ticket_type_id")
    private Long ticketTypeId;

    @Column(name = "scope_dept_id")
    private Long scopeDeptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssignmentStrategy strategy = AssignmentStrategy.LEAST_WORKLOAD;

    @Column(name = "workload_limit", nullable = false)
    private int workloadLimit = 5;

    @Column(name = "skill_tag", length = 64)
    private String skillTag;
}
