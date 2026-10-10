package com.nexflow.portal;

import lombok.Data;

import java.util.List;

@Data
public class PortalHomeDto {

    private String welcome;
    private List<TemplateDto> templates;
    private List<FaqDto> faqs;
    private MyStats stats;
    private boolean demo = true;

    @Data
    public static class TemplateDto {
        private Long typeId;
        private String name;
        private String icon;
        private String desc;
    }

    @Data
    public static class FaqDto {
        private Long id;
        private String question;
        private String answer;
    }

    @Data
    public static class MyStats {
        private long myPending;
        private long myOpen;
    }
}
