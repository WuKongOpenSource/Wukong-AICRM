package com.kakarote.ai_crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kakarote.ai_crm.entity.PO.CrmTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CrmTenantMapper extends BaseMapper<CrmTenant> {

    @Update("""
            UPDATE crm_tenant
            SET gift_credit_used = LEAST(COALESCE(gift_credit_total, 0), COALESCE(gift_credit_used, 0) + #{consumeCredits}),
                update_time = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId}
              AND COALESCE(gift_credit_total, 0) > COALESCE(gift_credit_used, 0)
            """)
    int consumeGiftCredits(@Param("tenantId") Long tenantId, @Param("consumeCredits") Long consumeCredits);

    /**
     * 查询按ID并锁定更新。
     */
    @Select("""
            SELECT *
            FROM crm_tenant
            WHERE tenant_id = #{tenantId}
            FOR UPDATE
            """)
    CrmTenant selectByIdForUpdate(@Param("tenantId") Long tenantId);

    /**
     * 新增PurchasedCredits。
     */
    @Update("""
            UPDATE crm_tenant
            SET purchased_credit_total = COALESCE(purchased_credit_total, 0) + #{creditAmount},
                update_time = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId}
            """)
    int addPurchasedCredits(@Param("tenantId") Long tenantId, @Param("creditAmount") Long creditAmount);
}
