package com.nexflow.ticket;

import com.nexflow.workflow.TicketState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findTopByTicketNoStartingWithOrderByTicketNoDesc(String prefix);

    long countByStatusNotIn(Collection<TicketState> statuses);

    long countByAssigneeIdIsNullAndStatusIn(Collection<TicketState> statuses);

    long countByCreatedAtAfter(LocalDateTime time);

    long countByResolvedAtAfter(LocalDateTime time);

    long countByAssigneeIdAndStatusIn(Long assigneeId, Collection<TicketState> statuses);

    List<Ticket> findByStatusNotIn(Collection<TicketState> statuses);

    List<Ticket> findByResolvedAtNotNull();

    List<Ticket> findByCreatedAtAfter(LocalDateTime time);

    List<Ticket> findByResolvedAtAfter(LocalDateTime time);
}
