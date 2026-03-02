package com.kakarote.ai_crm.config.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 租户上下文持有者
 * 在每个请求中通过 JwtAuthenticationTokenFilter 设置当前租户ID
 * TenantLineInnerInterceptor 通过此类获取当前租户ID，自动追加 SQL 条件
 */
public class TenantContextHolder {

    private static final Logger log = LoggerFactory.getLogger(TenantContextHolder.class);

    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();

    /**
     * 设置当前租户ID
     */
    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
        log.trace("设置租户上下文: tenantId={}", tenantId);
    }

    /**
     * 获取当前租户ID
     */
    public static Long getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * 清除当前租户上下文
     */
    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}
