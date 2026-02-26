package com.kakarote.ai_crm.entity.VO;

import com.kakarote.ai_crm.entity.BO.CustomerImportBO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 客户导入预览结果
 */
@Data
@Schema(name = "CustomerImportPreviewVO", description = "客户导入预览结果")
public class CustomerImportPreviewVO {

    @Schema(description = "总行数")
    private int totalRows;

    @Schema(description = "有效行数")
    private int validRows;

    @Schema(description = "重复行数")
    private int duplicateRows;

    @Schema(description = "错误行数")
    private int errorRows;

    @Schema(description = "所有行数据")
    private List<CustomerImportBO> rows;

    @Schema(description = "全局错误信息")
    private List<String> errors;
}
