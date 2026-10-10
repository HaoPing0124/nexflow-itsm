package com.nexflow.sla;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sla_policy")
@Data
@NoArgsConstructor
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "first_response_minutes", nullable = false)
    private int firstResponseMinutes = 120;

    @Column(name = "resolve_minutes", nullable = false)
    private int resolveMinutes = 480;

    @Column(name = "business_calendar_json", columnDefinition = "TEXT")
    private String businessCalendarJson;
}
