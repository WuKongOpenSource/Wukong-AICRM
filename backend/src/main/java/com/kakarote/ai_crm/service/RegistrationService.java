package com.kakarote.ai_crm.service;

import com.kakarote.ai_crm.entity.BO.RegisterBO;

public interface RegistrationService {

    /**
     * 注册新租户及超级管理员用户
     * @param registerBO 注册信息
     */
    void register(RegisterBO registerBO);
}
