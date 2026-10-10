package com.nexflow.sla;

import com.nexflow.ticket.Ticket;
import com.nexflow.tickettype.TicketType;
import com.nexflow.tickettype.TicketTypeRepository;
import com.nexflow.workflow.TicketState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SlaService {

    private final TicketTypeRepository ticketTypeRepository;
    private final SlaPolicyRepository slaPolicyRepository;

    public SlaPolicy policyFor(Ticket ticket) {
        TicketType type = ticketTypeRepository.findById(ticket.getTypeId()).orElse(null);
        if (type == null || type.getSlaPolicyId() == null) {
            return null;
        }
        return slaPolicyRepository.findById(type.getSlaPolicyId()).orElse(null);
    }

    public SlaStatus slaStatus(Ticket ticket) {
        if (ticket.getSlaResolveDueAt() == null) {
            return SlaStatus.OK;
        }
        TicketState status = ticket.getStatus();
        if (status == TicketState.RESOLVED || status == TicketState.CLOSED) {
            // 已解决/已关闭：以实际解决时间判定是否超时
            LocalDateTime ref = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : ticket.getSlaResolveDueAt();
            return ref.isAfter(ticket.getSlaResolveDueAt()) ? SlaStatus.BREACHED : SlaStatus.OK;
        }
        if (status == TicketState.CANCELED) {
            return SlaStatus.OK;
        }
        SlaPolicy policy = policyFor(ticket);
        if (policy == null || policy.getResolveMinutes() <= 0) {
            return SlaStatus.OK;
        }
        long remaining = Duration.between(LocalDateTime.now(), ticket.getSlaResolveDueAt()).toMinutes();
        if (remaining < 0) {
            return SlaStatus.BREACHED;
        }
        double ratio = (double) remaining / policy.getResolveMinutes();
        if (ratio < 0.2) {
            return SlaStatus.AT_RISK;
        }
        return SlaStatus.OK;
    }
}
