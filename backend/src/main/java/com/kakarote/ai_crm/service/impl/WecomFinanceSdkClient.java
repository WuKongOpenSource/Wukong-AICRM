package com.kakarote.ai_crm.service.impl;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

public interface WecomFinanceSdkClient {

    List<JSONObject> fetchMessages(FetchRequest request);

    record FetchRequest(String corpId,
                        String secret,
                        String privateKey,
                        String publicKeyVersion,
                        long startSeq,
                        int limit) {
    }
}
