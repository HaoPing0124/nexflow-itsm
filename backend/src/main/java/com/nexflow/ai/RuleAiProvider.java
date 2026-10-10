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
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RuleAiProvider implements AiProvider {

    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketCommentRepository commentRepository;
    private final FaqEntryRepository faqEntryRepository;

    @Override
    public ClassifySuggestion classifySuggest(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return new ClassifySuggestion("工单不存在", 0.0, true);
        }
        String text = (ticket.getTitle() + " " + (ticket.getDescription() == null ? "" : ticket.getDescription()))
                .toLowerCase(Locale.ROOT);
        String[] keywords = {"网络", "wifi", "联网", "邮件", "邮箱", "系统", "软件", "电脑", "账号", "密码", "权限",
                "打印", "投影", "设备", "空调", "门禁", "办公", "文具", "会议室", "快递", "社保", "入职", "离职", "证明"};
        String hit = null;
        for (String kw : keywords) {
            if (text.contains(kw)) {
                hit = kw;
                break;
            }
        }
        String suggestion = hit == null
                ? "规则演示：未命中关键词，建议人工分类"
                : "规则演示：命中关键词「" + hit + "」，建议提交人工复核分类";
        return new ClassifySuggestion(suggestion, hit == null ? 0.0 : 0.85, true);
    }

    @Override
    public Summary summarize(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return new Summary("工单不存在", true);
        }
        List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        String latest = comments.isEmpty() ? "暂无评论" : "最新评论：" + comments.get(comments.size() - 1).getContent();
        return new Summary("规则演示摘要：" + ticket.getTitle() + "（" + latest + "）", true);
    }

    @Override
    public List<FaqHit> faqRecommend(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.toLowerCase(Locale.ROOT);
        return faqEntryRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(f -> f.getQuestion().toLowerCase(Locale.ROOT).contains(q)
                        || (f.getAnswer() != null && f.getAnswer().toLowerCase(Locale.ROOT).contains(q)))
                .limit(5)
                .map(f -> new FaqHit(f.getId(), f.getQuestion()))
                .toList();
    }
}
