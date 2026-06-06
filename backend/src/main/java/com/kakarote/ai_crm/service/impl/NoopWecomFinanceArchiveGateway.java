package com.kakarote.ai_crm.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.kakarote.ai_crm.entity.PO.WecomCorpConfig;

import java.util.List;

public class NoopWecomFinanceArchiveGateway implements WecomFinanceArchiveGateway {

    @Override
    public List<JSONObject> fetchMessages(WecomCorpConfig config, long startSeq, int limit) {
        throw new IllegalStateException("企业微信会话存档 SDK 网关未配置");
    }
}
