package com.nexflow.ai;

import com.nexflow.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiFacade {

    private final AppProperties props;
    private final NoOpAiProvider noOpAiProvider;
    private final RuleAiProvider ruleAiProvider;

    private AiProvider provider() {
        return props.getAi().isEnabled() ? ruleAiProvider : noOpAiProvider;
    }

    public AiProvider.ClassifySuggestion classifySuggest(Long ticketId) {
        return provider().classifySuggest(ticketId);
    }

    public AiProvider.Summary summarize(Long ticketId) {
        return provider().summarize(ticketId);
    }

    public List<AiProvider.FaqHit> faqRecommend(String query) {
        return provider().faqRecommend(query);
    }
}
