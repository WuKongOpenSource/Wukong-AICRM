package com.kakarote.ai_crm.entity.BO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 客户导出参数
 */
@Data
@Schema(name = "CustomerExportBO", description = "客户导出参数")
public class CustomerExportBO {

    @Schema(description = "指定导出的客户ID列表（为空则按条件导出）")
    private List<Long> customerIds;

    @Schema(description = "搜索关键字")
    private String keyword;

    @Schema(description = "阶段筛选")
    private String stage;

    @Schema(description = "客户等级筛选")
    private String level;
}
