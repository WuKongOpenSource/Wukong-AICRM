package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.redis.Redis;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import com.kakarote.ai_crm.entity.BO.RegisterBO;
import com.kakarote.ai_crm.entity.PO.*;
import com.kakarote.ai_crm.mapper.ManageUserMapper;
import com.kakarote.ai_crm.mapper.ManagerDeptMapper;
import com.kakarote.ai_crm.service.*;
import com.kakarote.ai_crm.utils.CloudUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Objects;

@Slf4j
@Service
public class RegistrationServiceImpl implements RegistrationService {

    /** 超级管理员角色标识 */
    public static final String SUPER_ADMIN_REALM = "super_admin";

    private static final String EMAIL_CODE_CACHE_PREFIX = "register:email:code:";
    private static final int EMAIL_CODE_EXPIRE_SECONDS = 600;
    private static final int EMAIL_CODE_COOLDOWN_SECONDS = 60;
    private static final int REGISTER_EMAIL_TYPE = 1;
    private static final int RESET_PASSWORD_EMAIL_TYPE = 2;

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

    @Autowired
    private CloudUtil cloudUtil;

    @Autowired
    private Redis redis;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterBO registerBO) {
        String email = normalizeEmail(registerBO.getEmail());
        String realname = normalizeText(registerBO.getRealname());
        String companyName = StrUtil.trim(registerBO.getCompanyName());
        verifyEmailCode(email, registerBO.getVerificationCode());

        // 2. 检查邮箱唯一性（跨租户，queryUserByUsername 有 @InterceptorIgnore）
        ManagerUser existingUser = manageUserMapper.queryUserByUsername(email);
        if (existingUser != null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_EMAIL_ALREADY_REGISTERED);
        }

        // 3. 创建租户（crm_tenant 在 IGNORE_TENANT_TABLES 中，无需租户上下文）
        CrmTenant tenant = new CrmTenant();
        tenant.setTenantName(companyName);
        tenant.setContactEmail(email);
        tenant.setContactName(StrUtil.isNotBlank(realname) ? realname : email);
        tenant.setStatus(1);
        tenant.setMaxUsers(50);
        tenant.setGiftTokenTotal(ICrmTenantService.DEFAULT_GIFT_TOKEN_TOTAL);
        tenant.setGiftTokenUsed(0L);
        tenant.setCreateTime(new Date());
        tenant.setUpdateTime(new Date());
        tenantService.save(tenant);

        Long newTenantId = tenant.getTenantId();

        // 4. 设置租户上下文，后续 INSERT 自动填充 tenantId
        TenantContextHolder.setTenantId(newTenantId);

        try {
            // 5. 创建默认部门
            ManagerDept dept = new ManagerDept();
            dept.setDeptName(companyName);
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
            user.setUsername(email);
            user.setPassword(encoder.encode(registerBO.getPassword()));
            user.setEmail(email);
            user.setRealname(StrUtil.isNotBlank(realname) ? realname : email);
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

            redis.del(getEmailCodeCacheKey(email));
            log.info("租户注册成功: tenantId={}, email={}, companyName={}", newTenantId, email, companyName);

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

    /**
     * 发送邮件验证码
     *
     * @param email 邮件
     * @param type  类型 1为注册 2为找回密码
     */
    @Override
    public void sendEmail(String email, Integer type) {
        String normalizedEmail = normalizeEmail(email);
        if (!Validator.isEmail(normalizedEmail, true)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "邮箱格式不正确");
        }
        validateSendScene(normalizedEmail, type);

        String cacheKey = getEmailCodeCacheKey(normalizedEmail);
        Long ttl = redis.ttl(cacheKey);
        if (ttl != null && ttl > EMAIL_CODE_EXPIRE_SECONDS - EMAIL_CODE_COOLDOWN_SECONDS) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_BAD_REQUEST, "验证码发送过于频繁，请稍后再试");
        }

        String code = RandomUtil.randomNumbers(6);
        String sceneName = Objects.equals(type, RESET_PASSWORD_EMAIL_TYPE) ? "找回密码" : "注册";
        boolean sent = cloudUtil.sendVerificationCodeEmail(
                normalizedEmail,
                "AI CRM 邮箱验证码",
                code,
                sceneName
        );
        if (!sent) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }

        redis.setex(cacheKey, EMAIL_CODE_EXPIRE_SECONDS, code);
        log.info("发送邮箱验证码成功: email={}, type={}", normalizedEmail, type);
    }

    private void validateSendScene(String email, Integer type) {
        ManagerUser existingUser = manageUserMapper.queryUserByUsername(email);
        if (Objects.equals(type, REGISTER_EMAIL_TYPE)) {
            if (existingUser != null) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_EMAIL_ALREADY_REGISTERED);
            }
            return;
        }
        if (Objects.equals(type, RESET_PASSWORD_EMAIL_TYPE)) {
            if (existingUser == null) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DOES_NOT_EXIST, "该邮箱尚未注册");
            }
            return;
        }
        throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "不支持的验证码类型");
    }

    private void verifyEmailCode(String email, String verificationCode) {
        String cachedCode = redis.get(getEmailCodeCacheKey(email));
        if (StrUtil.isBlank(cachedCode)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_VERIFICATION_CODE_ERROR, "验证码已失效，请重新获取");
        }
        if (!StrUtil.equals(StrUtil.trim(verificationCode), cachedCode)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_VERIFICATION_CODE_ERROR);
        }
    }

    private String getEmailCodeCacheKey(String email) {
        return EMAIL_CODE_CACHE_PREFIX + email;
    }

    private String normalizeEmail(String email) {
        return StrUtil.trim(email).toLowerCase();
    }

    private String normalizeText(String value) {
        String normalized = StrUtil.trim(value);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }
}
