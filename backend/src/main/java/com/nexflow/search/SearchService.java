package com.nexflow.search;

import com.nexflow.common.BusinessException;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.ErrorCode;
import com.nexflow.knowledge.FaqEntry;
import com.nexflow.knowledge.FaqEntryRepository;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.user.DataScopeService;
import com.nexflow.user.User;
import com.nexflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final TicketRepository ticketRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final UserRepository userRepository;
    private final DataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public List<SearchResult> search(String query, String scope) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();
        if (scope == null || scope.isBlank() || "ticket".equals(scope)) {
            results.addAll(searchTickets(q));
        }
        if (scope == null || scope.isBlank() || "knowledge".equals(scope)) {
            results.addAll(searchKnowledge(q));
        }
        if (scope == null || scope.isBlank() || "user".equals(scope)) {
            results.addAll(searchUsers(q));
        }
        return results.stream().limit(30).toList();
    }

    private List<SearchResult> searchTickets(String q) {
        List<Ticket> candidates = ticketRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(t -> t.getTitle().toLowerCase(Locale.ROOT).contains(q)
                        || (t.getDescription() != null && t.getDescription().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
        List<SearchResult> results = new ArrayList<>();
        for (Ticket ticket : candidates) {
            if (dataScopeService.canAccess(ticket, CurrentUser.get())) {
                String excerpt = ticket.getDescription() == null ? ticket.getTitle() : ticket.getDescription();
                results.add(new SearchResult("ticket", ticket.getTicketNo() + " " + ticket.getTitle(),
                        "/app/tickets/" + ticket.getId(), truncate(excerpt, 80)));
            }
        }
        return results.stream().limit(10).toList();
    }

    private List<SearchResult> searchKnowledge(String q) {
        return faqEntryRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(f -> f.getQuestion().toLowerCase(Locale.ROOT).contains(q)
                        || (f.getAnswer() != null && f.getAnswer().toLowerCase(Locale.ROOT).contains(q)))
                .limit(10)
                .map(f -> new SearchResult("knowledge", f.getQuestion(),
                        "/portal?faq=" + f.getId(), truncate(f.getAnswer(), 80)))
                .toList();
    }

    private List<SearchResult> searchUsers(String q) {
        return userRepository.findByStatusOrderByIdAsc(com.nexflow.user.UserStatus.ACTIVE).stream()
                .filter(u -> u.getDisplayName().toLowerCase(Locale.ROOT).contains(q)
                        || u.getUsername().toLowerCase(Locale.ROOT).contains(q))
                .limit(10)
                .map(u -> new SearchResult("user", u.getDisplayName(),
                        "/app/config?userId=" + u.getId(), u.getUsername() + " · " + safeDept(u)))
                .toList();
    }

    private String safeDept(User user) {
        return user.getDeptId() == null ? "未分配部门" : "部门ID " + user.getDeptId();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
