package com.kakarote.ai_crm.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.entity.PO.WecomCorpConfig;
import com.kakarote.ai_crm.utils.SecretTextCipher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WecomFinanceArchiveGatewayImplTest {

    @Test
    void fetchMessagesShouldRejectIncompleteArchiveConfigBeforeCallingSdk() {
        WecomFinanceSdkClient sdkClient = mock(WecomFinanceSdkClient.class);
        WecomFinanceArchiveGatewayImpl gateway = new WecomFinanceArchiveGatewayImpl(cipher(), sdkClient);
        WecomCorpConfig config = config();
        config.setArchiveSecretEncrypted(null);
        config.setArchivePrivateKeyEncrypted(cipher().encrypt("private-key"));
        config.setArchivePublicKeyVersion("1");

        assertThatThrownBy(() -> gateway.fetchMessages(config, 0L, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先配置企业微信会话存档 Secret");
        verifyNoInteractions(sdkClient);
    }

    @Test
    void fetchMessagesShouldDecryptConfigAndDelegateToSdkClient() {
        SecretTextCipher cipher = cipher();
        WecomFinanceSdkClient sdkClient = mock(WecomFinanceSdkClient.class);
        WecomFinanceArchiveGatewayImpl gateway = new WecomFinanceArchiveGatewayImpl(cipher, sdkClient);
        WecomCorpConfig config = config();
        config.setArchiveSecretEncrypted(cipher.encrypt("archive-secret"));
        config.setArchivePrivateKeyEncrypted(cipher.encrypt("private-key"));
        config.setArchivePublicKeyVersion("1");
        JSONObject message = new JSONObject().fluentPut("seq", 8L).fluentPut("msgid", "msg_8");
        when(sdkClient.fetchMessages(any(WecomFinanceSdkClient.FetchRequest.class))).thenReturn(List.of(message));

        List<JSONObject> messages = gateway.fetchMessages(config, 7L, 2);

        assertThat(messages).containsExactly(message);
        verify(sdkClient).fetchMessages(org.mockito.Mockito.argThat(request ->
                "corp_1".equals(request.corpId())
                        && "archive-secret".equals(request.secret())
                        && "private-key".equals(request.privateKey())
                        && "1".equals(request.publicKeyVersion())
                        && request.startSeq() == 7L
                        && request.limit() == 2
        ));
    }

    private static WecomCorpConfig config() {
        WecomCorpConfig config = new WecomCorpConfig();
        config.setCorpId("corp_1");
        return config;
    }

    private static SecretTextCipher cipher() {
        return new SecretTextCipher("0123456789abcdef");
    }
}
