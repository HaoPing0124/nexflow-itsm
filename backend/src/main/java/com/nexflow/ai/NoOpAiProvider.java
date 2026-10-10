package com.nexflow.ai;

import com.nexflow.knowledge.FaqEntry;
import com.nexflow.knowledge.FaqEntryRepository;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketComment;
import com.nexflow.ticket.TicketCommentRepository;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.tickettype.TicketType;
import com.nexflow.tickettype.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NoOpAiProvider implements AiProvider {

    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketCommentRepository commentRepository;
    private final FaqEntryRepository faqEntryRepository;

    @Override
    public ClassifySuggestion classifySuggest(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        TicketType type = ticket != null ? ticketTypeRepository.findById(ticket.getTypeId()).orElse(null) : null;
        String suggestion = type != null ? "AI 未启用（规则演示）：建议类型「" + type.getName() + "」" : "AI 未启用（规则演示）";
        return new ClassifySuggestion(suggestion, 0.0, true);
    }

    @Override
    public Summary summarize(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return new Summary("工单不存在", true);
        }
        List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        String extra = comments.isEmpty() ? "" : "；已有 " + comments.size() + " 条评论";
        return new Summary("工单摘要：[" + ticket.getTicketNo() + "] " + ticket.getTitle() + extra, true);
    }

    @Override
    public List<FaqHit> faqRecommend(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return faqEntryRepository.findAllByOrderBySortOrderAsc().stream()
                .limit(3)
                .map(f -> new FaqHit(f.getId(), f.getQuestion()))
                .toList();
    }
}
