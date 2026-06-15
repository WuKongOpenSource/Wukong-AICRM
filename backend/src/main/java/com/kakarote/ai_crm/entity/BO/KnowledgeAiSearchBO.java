package com.kakarote.ai_crm.entity.BO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "KnowledgeAiSearchBO", description = "Knowledge AI search query")
public class KnowledgeAiSearchBO {

    @Schema(description = "Search keyword or question")
    private String keyword;

    @Schema(description = "Knowledge type filter")
    private String type;

    @Schema(description = "Maximum number of referenced documents")
    private Integer limit;
}
