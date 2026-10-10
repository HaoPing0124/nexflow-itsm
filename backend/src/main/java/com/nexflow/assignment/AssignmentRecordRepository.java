package com.nexflow.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRecordRepository extends JpaRepository<AssignmentRecord, Long> {

    List<AssignmentRecord> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    long countByAssignedUserId(Long assignedUserId);
}
