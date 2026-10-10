package com.nexflow.ai;

import com.nexflow.common.ApiResponse;
import com.nexflow.common.BusinessException;
import com.nexflow.common.CurrentUser;
import com.nexflow.common.ErrorCode;
import com.nexflow.ticket.Ticket;
import com.nexflow.ticket.TicketRepository;
import com.nexflow.user.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiFacade aiFacade;
    private final TicketRepository ticketRepository;
    private final DataScopeService dataScopeService;

    @PostMapping("/classify-suggest/{ticketId}")
    public ApiResponse<AiProvider.ClassifySuggestion> classifySuggest(@PathVariable Long ticketId) {
        checkTicketAccess(ticketId);
        return ApiResponse.ok(aiFacade.classifySuggest(ticketId));
    }

    @PostMapping("/summarize/{ticketId}")
    public ApiResponse<AiProvider.Summary> summarize(@PathVariable Long ticketId) {
        checkTicketAccess(ticketId);
        return ApiResponse.ok(aiFacade.summarize(ticketId));
    }

    @GetMapping("/faq-recommend")
    public ApiResponse<List<AiProvider.FaqHit>> faqRecommend(@RequestParam(required = false) String q) {
        return ApiResponse.ok(aiFacade.faqRecommend(q));
    }

    private void checkTicketAccess(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工单不存在"));
        if (!dataScopeService.canAccess(ticket, CurrentUser.get())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少权限: ticket:view（无权查看该工单）");
        }
    }
}
