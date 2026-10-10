package com.nexflow.tickettype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomFieldDefRepository extends JpaRepository<CustomFieldDef, Long> {

    List<CustomFieldDef> findByTypeIdOrderBySortOrderAsc(Long typeId);
}
