package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.PO.WecomCorpConfig;
import com.kakarote.ai_crm.utils.SecretTextCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WecomFinanceArchiveGatewayImpl implements WecomFinanceArchiveGateway {

    private final SecretTextCipher secretTextCipher;
    private final WecomFinanceSdkClient sdkClient;

    @Autowired
    public WecomFinanceArchiveGatewayImpl(SecretTextCipher secretTextCipher,
                                          WecomFinanceSdkClient sdkClient) {
        this.secretTextCipher = secretTextCipher;
        this.sdkClient = sdkClient;
    }

    @Override
    public List<JSONObject> fetchMessages(WecomCorpConfig config, long startSeq, int limit) {
        if (config == null || StrUtil.isBlank(config.getCorpId())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "企业微信会话存档企业未配置");
        }
        String secret = decryptRequired(config.getArchiveSecretEncrypted(), "请先配置企业微信会话存档 Secret");
        String privateKey = decryptRequired(config.getArchivePrivateKeyEncrypted(), "请先配置企业微信会话存档私钥");
        if (StrUtil.isBlank(config.getArchivePublicKeyVersion())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请先配置企业微信会话存档公钥版本");
        }
        return sdkClient.fetchMessages(new WecomFinanceSdkClient.FetchRequest(
                config.getCorpId(),
                secret,
                privateKey,
                StrUtil.trim(config.getArchivePublicKeyVersion()),
                startSeq,
                limit
        ));
    }

    private String decryptRequired(String encrypted, String message) {
        if (StrUtil.isBlank(encrypted)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, message);
        }
        String value = secretTextCipher.decrypt(encrypted);
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, message);
        }
        return value;
    }
}
