package com.kakarote.ai_crm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kakarote.ai_crm.entity.PO.CrmTenant;
import com.kakarote.ai_crm.mapper.CrmTenantMapper;
import com.kakarote.ai_crm.service.ICrmTenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmTenantServiceImpl extends ServiceImpl<CrmTenantMapper, CrmTenant> implements ICrmTenantService {

    @Override
    public long getGiftTokenTotal(Long tenantId) {
        CrmTenant tenant = tenantId != null ? getById(tenantId) : null;
        if (tenant == null) {
            return 0L;
        }
        Long total = tenant.getGiftTokenTotal();
        return total != null && total > 0 ? total : DEFAULT_GIFT_TOKEN_TOTAL;
    }

    @Override
    public long getGiftTokenUsed(Long tenantId) {
        CrmTenant tenant = tenantId != null ? getById(tenantId) : null;
        if (tenant == null || tenant.getGiftTokenUsed() == null) {
            return 0L;
        }
        return Math.max(tenant.getGiftTokenUsed(), 0L);
    }

    @Override
    public long getGiftTokenRemaining(Long tenantId) {
        long total = getGiftTokenTotal(tenantId);
        long used = getGiftTokenUsed(tenantId);
        return Math.max(total - used, 0L);
    }

    @Override
    public boolean hasAvailableGiftTokens(Long tenantId) {
        return getGiftTokenRemaining(tenantId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeGiftTokens(Long tenantId, long consumeTokens) {
        if (tenantId == null || consumeTokens <= 0) {
            return;
        }
        baseMapper.consumeGiftTokens(tenantId, consumeTokens);
    }
}
