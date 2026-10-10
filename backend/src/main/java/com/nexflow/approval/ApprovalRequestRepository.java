package com.nexflow.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    long countByTicketIdAndStatus(Long ticketId, ApprovalStatus status);

    List<ApprovalRequest> findByTicketIdOrderByIdAsc(Long ticketId);

    List<ApprovalRequest> findByApproverIdOrderByRequestedAtDesc(Long approverId);

    long countByStatus(ApprovalStatus status);
}
