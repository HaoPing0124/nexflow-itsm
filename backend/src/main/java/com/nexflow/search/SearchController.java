package com.nexflow.search;

import com.nexflow.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ApiResponse<List<SearchResult>> search(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String scope) {
        return ApiResponse.ok(searchService.search(q, scope));
    }
}
