package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import com.kakarote.ai_crm.entity.BO.RegisterBO;
import com.kakarote.ai_crm.entity.PO.*;
import com.kakarote.ai_crm.mapper.ManageUserMapper;
import com.kakarote.ai_crm.mapper.ManagerDeptMapper;
import com.kakarote.ai_crm.service.*;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

@Slf4j
@Service
public class RegistrationServiceImpl implements RegistrationService {

    /** 固定验证码（临时方案） */
    private static final String FIXED_VERIFICATION_CODE = "888888";

    /** 超级管理员角色标识 */
    public static final String SUPER_ADMIN_REALM = "super_admin";

    @Autowired
    private ManageUserMapper manageUserMapper;

    @Autowired
    private ICrmTenantService tenantService;

    @Autowired
    private ManagerDeptMapper deptMapper;

    @Autowired
    private IManagerRoleService roleService;

    @Autowired
    private ManageUserService manageUserService;

    @Autowired
    private IManagerUserRoleService userRoleService;

    @Lazy
    @Autowired
    private WeKnoraClient weKnoraClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterBO registerBO) {
        // 1. 验证验证码
        if (!FIXED_VERIFICATION_CODE.equals(registerBO.getVerificationCode())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_VERIFICATION_CODE_ERROR);
        }

        // 2. 检查邮箱唯一性（跨租户，queryUserByUsername 有 @InterceptorIgnore）
        ManagerUser existingUser = manageUserMapper.queryUserByUsername(registerBO.getEmail());
        if (existingUser != null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_EMAIL_ALREADY_REGISTERED);
        }

        // 3. 创建租户（crm_tenant 在 IGNORE_TENANT_TABLES 中，无需租户上下文）
        CrmTenant tenant = new CrmTenant();
        tenant.setTenantName(registerBO.getCompanyName());
        tenant.setContactEmail(registerBO.getEmail());
        tenant.setContactName(StrUtil.isNotBlank(registerBO.getRealname()) ? registerBO.getRealname() : registerBO.getEmail());
        tenant.setStatus(1);
        tenant.setMaxUsers(50);
        tenant.setCreateTime(new Date());
        tenant.setUpdateTime(new Date());
        tenantService.save(tenant);

        Long newTenantId = tenant.getTenantId();

        // 4. 设置租户上下文，后续 INSERT 自动填充 tenantId
        TenantContextHolder.setTenantId(newTenantId);

        try {
            // 5. 创建默认部门
            ManagerDept dept = new ManagerDept();
            dept.setDeptName(registerBO.getCompanyName());
            dept.setParentId(0L);
            dept.setSortOrder(1);
            dept.setCreateTime(new Date());
            deptMapper.insert(dept);

            // 6. 创建超级管理员角色（realm=super_admin，拥有全部权限，无需分配菜单）
            ManagerRole adminRole = ManagerRole.builder()
                    .roleName("超级管理员")
                    .realm(SUPER_ADMIN_REALM)
                    .description("系统超级管理员（注册时自动创建）")
                    .dataType(5) // 全部数据权限
                    .createTime(LocalDateTime.now())
                    .build();
            roleService.save(adminRole);

            // 7. 创建管理员用户（email 作为 username）
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            ManagerUser user = new ManagerUser();
            user.setUsername(registerBO.getEmail());
            user.setPassword(encoder.encode(registerBO.getPassword()));
            user.setEmail(registerBO.getEmail());
            user.setRealname(StrUtil.isNotBlank(registerBO.getRealname()) ? registerBO.getRealname() : registerBO.getEmail());
            user.setDeptId(dept.getDeptId());
            user.setStatus(1);
            user.setCreateTime(new Date());
            manageUserService.save(user);

            // 8. 关联用户与角色
            ManagerUserRole userRole = new ManagerUserRole();
            userRole.setUserId(user.getUserId());
            userRole.setRoleId(adminRole.getRoleId());
            userRole.setCreateTime(LocalDateTime.now());
            userRoleService.save(userRole);

            log.info("租户注册成功: tenantId={}, email={}, companyName={}", newTenantId, registerBO.getEmail(), registerBO.getCompanyName());

            // 9. 初始化 WeKnora 租户（注册 + 创建模型 + 创建知识库）
            if (weKnoraClient.isEnabled()) {
                try {
                    weKnoraClient.getOrCreateTenantContext(newTenantId);
                    log.info("WeKnora 租户初始化成功: tenantId={}", newTenantId);
                } catch (Exception e) {
                    log.warn("WeKnora 租户初始化失败，将在首次使用时重试: tenantId={}, error={}", newTenantId, e.getMessage());
                }
            }
        } finally {
            TenantContextHolder.clear();
        }
    }
}
