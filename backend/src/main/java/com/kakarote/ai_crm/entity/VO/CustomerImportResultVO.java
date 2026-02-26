package com.kakarote.ai_crm.entity.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 客户导入确认结果
 */
@Data
@Schema(name = "CustomerImportResultVO", description = "客户导入确认结果")
public class CustomerImportResultVO {

    @Schema(description = "新增数量")
    private int imported;

    @Schema(description = "更新数量")
    private int updated;

    @Schema(description = "跳过数量")
    private int skipped;

    @Schema(description = "失败错误信息")
    private List<String> errors;
}
