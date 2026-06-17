package com.kakarote.ai_crm.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.kakarote.ai_crm.utils.UserUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
        this.strictInsertFill(metaObject, "updateTime", Date.class, new Date());

        Long userId = UserUtil.getUserIdOrNull();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createUserId", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
    }
}
