package com.nexflow.sla;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByCode(String code);

    List<SlaPolicy> findAllByOrderByIdAsc();
}
