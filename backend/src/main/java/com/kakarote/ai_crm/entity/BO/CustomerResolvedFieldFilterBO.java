package com.kakarote.ai_crm.entity.BO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端解析后的客户字段筛选条件，仅使用白名单列名。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CustomerResolvedFieldFilterBO", description = "服务端解析后的客户字段筛选条件")
public class CustomerResolvedFieldFilterBO {

    @Schema(description = "安全列名")
    private String columnName;

    @Schema(description = "操作符: isEmpty/isNotEmpty")
    private String operator;

    @Schema(description = "空值模式: nullOnly/blank/jsonArray")
    private String emptyMode;
}
