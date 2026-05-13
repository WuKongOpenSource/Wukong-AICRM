package com.kakarote.syncdata.model;

/**
 * ai_crm 选择绑定 wk_crm 公司时展示的轻量选项。
 */
public record OldCompanyOption(
        Long companyId,
        String companyName,
        Long customerCount,
        Long contactCount,
        Long userCount,
        Long followUpCount
) {
}
