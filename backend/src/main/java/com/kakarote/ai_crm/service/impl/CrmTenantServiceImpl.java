package com.kakarote.ai_crm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kakarote.ai_crm.entity.PO.CrmTenant;
import com.kakarote.ai_crm.mapper.CrmTenantMapper;
import com.kakarote.ai_crm.service.ICrmTenantService;
import org.springframework.stereotype.Service;

@Service
public class CrmTenantServiceImpl extends ServiceImpl<CrmTenantMapper, CrmTenant> implements ICrmTenantService {
}
