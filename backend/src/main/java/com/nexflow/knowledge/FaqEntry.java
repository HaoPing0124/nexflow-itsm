package com.nexflow.knowledge;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "faq_entry")
@Data
@NoArgsConstructor
public class FaqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(length = 64)
    private String category;

    @Column(name = "sort_order")
    private int sortOrder;
}
