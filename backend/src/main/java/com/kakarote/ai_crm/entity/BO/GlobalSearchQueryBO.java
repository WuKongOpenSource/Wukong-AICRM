package com.kakarote.ai_crm.entity.BO;

import com.kakarote.ai_crm.common.PageEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "GlobalSearchQueryBO", description = "Global search query")
public class GlobalSearchQueryBO extends PageEntity {

    @Schema(description = "Keyword")
    private String keyword;

    @Schema(description = "Entity type: customer/contact/relation/task/schedule/knowledge")
    private String entityType;

    @Schema(hidden = true)
    private Long tenantId;

    @Schema(hidden = true)
    private Long currentUserId;

    @Schema(hidden = true)
    private Boolean customerEnabled;

    @Schema(hidden = true)
    private Boolean customerAllData;

    @Schema(hidden = true)
    private List<Long> customerUserIds;

    @Schema(hidden = true)
    private Boolean contactEnabled;

    @Schema(hidden = true)
    private Boolean contactAllData;

    @Schema(hidden = true)
    private List<Long> contactUserIds;

    @Schema(hidden = true)
    private Boolean relationEnabled;

    @Schema(hidden = true)
    private Boolean taskEnabled;

    @Schema(hidden = true)
    private Boolean taskAllData;

    @Schema(hidden = true)
    private List<Long> taskUserIds;

    @Schema(hidden = true)
    private Boolean scheduleEnabled;

    @Schema(hidden = true)
    private Boolean scheduleAllData;

    @Schema(hidden = true)
    private List<Long> scheduleUserIds;

    @Schema(hidden = true)
    private Boolean knowledgeEnabled;

    @Schema(hidden = true)
    private Boolean knowledgeAllData;

    @Schema(hidden = true)
    private List<Long> knowledgeUserIds;
}
