package com.nexflow.portal;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.CurrentUser;
import com.nexflow.knowledge.FaqEntry;
import com.nexflow.knowledge.FaqEntryRepository;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.tickettype.TicketType;
import com.nexflow.tickettype.TicketTypeRepository;
import com.nexflow.user.UserPrincipal;
import com.nexflow.workflow.TicketState;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalHomeController {

    private final TicketTypeRepository ticketTypeRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final TicketRepository ticketRepository;

    @GetMapping("/home")
    public ApiResponse<PortalHomeDto> home() {
        UserPrincipal user = CurrentUser.get();
        PortalHomeDto dto = new PortalHomeDto();
        dto.setWelcome(greeting(user.getDisplayName()));

        List<TicketType> types = ticketTypeRepository.findByActiveTrueOrderByIdAsc();
        Map<String, String> icons = Map.of(
                "IT_SUPPORT", "Monitor",
                "ADMIN_SERVICE", "Boxes",
                "HR_SERVICE", "Users",
                "EQUIPMENT_MAINTENANCE", "Wrench",
                "ACCOUNT_PERMISSION", "KeyRound");
        List<PortalHomeDto.TemplateDto> templates = types.stream().map(t -> {
            PortalHomeDto.TemplateDto template = new PortalHomeDto.TemplateDto();
            template.setTypeId(t.getId());
            template.setName(t.getName());
            template.setIcon(icons.getOrDefault(t.getCode(), "ClipboardList"));
            template.setDesc(t.getDescription());
            return template;
        }).toList();
        dto.setTemplates(templates);

        List<PortalHomeDto.FaqDto> faqs = faqEntryRepository.findTop5ByOrderBySortOrderAsc().stream().map(f -> {
            PortalHomeDto.FaqDto faq = new PortalHomeDto.FaqDto();
            faq.setId(f.getId());
            faq.setQuestion(f.getQuestion());
            faq.setAnswer(f.getAnswer());
            return faq;
        }).toList();
        dto.setFaqs(faqs);

        PortalHomeDto.MyStats stats = new PortalHomeDto.MyStats();
        List<Ticket> myTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getCreatorId().equals(user.getUserId()))
                .filter(t -> t.getStatus() != TicketState.CLOSED && t.getStatus() != TicketState.CANCELED)
                .toList();
        stats.setMyPending(myTickets.size());
        long myOpen = ticketRepository.findAll().stream()
                .filter(t -> t.getAssigneeId() != null && t.getAssigneeId().equals(user.getUserId()))
                .filter(t -> t.getStatus() == TicketState.ASSIGNED || t.getStatus() == TicketState.IN_PROGRESS)
                .count();
        stats.setMyOpen(myOpen);
        dto.setStats(stats);
        return ApiResponse.ok(dto);
    }

    private String greeting(String name) {
        int hour = LocalTime.now().getHour();
        String prefix = hour < 6 ? "夜深了" : hour < 12 ? "早上好" : hour < 18 ? "下午好" : "晚上好";
        return prefix + "，" + name + "。让每一项服务请求，都有清晰的下一步。";
    }
}
