package com.kakarote.ai_crm.entity.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "KnowledgePreviewTokenVO", description = "Knowledge media preview token")
public class KnowledgePreviewTokenVO {

    @Schema(description = "Preview URL")
    private String url;

    @Schema(description = "Expiration time")
    private String expiresAt;
}
