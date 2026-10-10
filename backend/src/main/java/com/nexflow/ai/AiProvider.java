package com.nexflow.ai;

import java.util.List;

public interface AiProvider {

    ClassifySuggestion classifySuggest(Long ticketId);

    Summary summarize(Long ticketId);

    List<FaqHit> faqRecommend(String query);

    record ClassifySuggestion(String suggestion, double confidence, boolean demo) {
    }

    record Summary(String summary, boolean demo) {
    }

    record FaqHit(Long id, String title) {
    }
}
