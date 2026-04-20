package com.kakarote.ai_crm.service;

import com.kakarote.ai_crm.common.auth.DataPermissionContext;

public interface DataPermissionService {

    DataPermissionContext createContext(String module);

    DataPermissionContext createContextByPermission(String permission);

    boolean hasUserDataAccess(String module, Long targetUserId);

    boolean hasUserDataAccessByPermission(String permission, Long targetUserId);

    void assertUserDataAccess(String module, Long targetUserId);

    void assertUserDataAccessByPermission(String permission, Long targetUserId);
}
