package com.nexflow.tickettype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    Optional<TicketType> findByCode(String code);

    List<TicketType> findByActiveTrueOrderByIdAsc();

    List<TicketType> findAllByOrderByIdAsc();
}
