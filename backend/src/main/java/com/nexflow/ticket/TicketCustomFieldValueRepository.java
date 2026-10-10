package com.nexflow.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCustomFieldValueRepository extends JpaRepository<TicketCustomFieldValue, Long> {

    List<TicketCustomFieldValue> findByTicketId(Long ticketId);
}
