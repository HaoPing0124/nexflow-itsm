package com.nexflow.ticket;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ticket_custom_field_value",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ticket_id", "field_id"}))
@Data
@NoArgsConstructor
public class TicketCustomFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "value_json", columnDefinition = "TEXT")
    private String valueJson;

    public TicketCustomFieldValue(Long ticketId, Long fieldId, String valueJson) {
        this.ticketId = ticketId;
        this.fieldId = fieldId;
        this.valueJson = valueJson;
    }
}
