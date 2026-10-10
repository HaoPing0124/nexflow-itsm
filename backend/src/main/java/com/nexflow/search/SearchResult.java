package com.nexflow.search;

import lombok.Data;

@Data
public class SearchResult {

    private String type;
    private String title;
    private String link;
    private String excerpt;

    public SearchResult(String type, String title, String link, String excerpt) {
        this.type = type;
        this.title = title;
        this.link = link;
        this.excerpt = excerpt;
    }
}
