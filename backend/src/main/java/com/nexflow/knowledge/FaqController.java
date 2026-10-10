package com.nexflow.knowledge;

import com.nexflow.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class FaqController {

    private final FaqEntryRepository faqEntryRepository;

    @GetMapping("/faq")
    public ApiResponse<List<FaqEntry>> faq() {
        return ApiResponse.ok(faqEntryRepository.findAllByOrderBySortOrderAsc());
    }
}
