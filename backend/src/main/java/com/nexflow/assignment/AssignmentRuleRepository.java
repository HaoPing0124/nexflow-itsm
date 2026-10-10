package com.nexflow.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRuleRepository extends JpaRepository<AssignmentRule, Long> {

    List<AssignmentRule> findByActiveTrueOrderByPriorityAsc();
}
