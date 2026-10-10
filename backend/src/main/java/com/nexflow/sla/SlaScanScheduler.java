package com.nexflow.sla;

import com.nexflow.notification.NotificationService;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.workflow.TicketState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class SlaScanScheduler {

    private final TicketRepository ticketRepository;
    private final SlaService slaService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 */15 * * * *")
    public void scanRisk() {
        List<Ticket> open = ticketRepository.findByStatusNotIn(
                List.of(TicketState.CLOSED, TicketState.CANCELED));
        for (Ticket ticket : open) {
            if (ticket.getStatus() != TicketState.ASSIGNED && ticket.getStatus() != TicketState.IN_PROGRESS) {
                continue;
            }
            SlaStatus status = slaService.slaStatus(ticket);
            if (status != SlaStatus.OK && ticket.getAssigneeId() != null) {
                String type = status == SlaStatus.AT_RISK ? "SLA_RISK" : "SLA_BREACH";
                notificationService.publish(List.of(ticket.getAssigneeId()), type,
                        status == SlaStatus.AT_RISK ? "SLA 风险提醒" : "SLA 已超时",
                        "工单 " + ticket.getTicketNo() + " " + (status == SlaStatus.AT_RISK
                                ? "剩余时间不足20%，请尽快处理" : "已超出解决时限"),
                        "/app/tickets/" + ticket.getId());
            }
        }
    }
}
