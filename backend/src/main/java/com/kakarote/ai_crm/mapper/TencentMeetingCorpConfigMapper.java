package com.kakarote.ai_crm.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kakarote.ai_crm.entity.PO.TencentMeetingCorpConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TencentMeetingCorpConfigMapper extends BaseMapper<TencentMeetingCorpConfig> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM crm_tencent_meeting_corp_config
            WHERE app_id IS NOT NULL
              AND sdk_id IS NOT NULL
            ORDER BY update_time DESC NULLS LAST
            LIMIT 1
            """)
    TencentMeetingCorpConfig selectLatestOAuthConfigIgnoreTenant();

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE crm_tencent_meeting_corp_config
            SET last_sync_time = #{lastSyncTime},
                last_sync_status = #{lastSyncStatus},
                last_sync_error = #{lastSyncError},
                update_time = NOW()
            WHERE id = #{id}
            """)
    int updateSyncStatusIgnoreTenant(TencentMeetingCorpConfig config);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM crm_tencent_meeting_corp_config
            WHERE webhook_token_encrypted IS NOT NULL
            """)
    List<TencentMeetingCorpConfig> selectWebhookConfigsIgnoreTenant();
}
