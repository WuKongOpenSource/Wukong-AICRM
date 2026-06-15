package com.kakarote.ai_crm.entity.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Schema(name = "KnowledgeAiSearchVO", description = "Knowledge AI search result")
public class KnowledgeAiSearchVO {

    @Schema(description = "Original keyword")
    private String keyword;

    @Schema(description = "AI answer in markdown")
    private String answer;

    @Schema(description = "Elapsed milliseconds")
    private Long tookMs;

    @Schema(description = "Overall match percent")
    private Integer matchPercent;

    @Schema(description = "Matched document count")
    private Integer totalHits;

    @Schema(description = "Referenced documents")
    private List<ReferenceItem> references;

    @Data
    @Schema(name = "KnowledgeAiSearchReferenceItem", description = "Referenced knowledge document")
    public static class ReferenceItem {

        private Long knowledgeId;

        private String name;

        private String type;

        private String customerName;

        private String summary;

        private String excerpt;

        private Integer matchPercent;

        private Long fileSize;

        private Date createTime;
    }
}
