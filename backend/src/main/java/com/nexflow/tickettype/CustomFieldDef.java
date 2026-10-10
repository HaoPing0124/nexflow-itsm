package com.nexflow.tickettype;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "custom_field_def")
@Data
@NoArgsConstructor
public class CustomFieldDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(name = "field_code", nullable = false, length = 64)
    private String fieldCode;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private FieldType fieldType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "sort_order")
    private int sortOrder;
}
