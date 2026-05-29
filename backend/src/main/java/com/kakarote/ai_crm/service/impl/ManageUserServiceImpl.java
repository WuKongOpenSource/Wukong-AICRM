package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.Const;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import com.kakarote.ai_crm.entity.BO.ResetUsernameBO;
import com.kakarote.ai_crm.entity.BO.UserAddBO;
import com.kakarote.ai_crm.entity.BO.UserQueryBO;
import com.kakarote.ai_crm.entity.BO.UserStatusBO;
import com.kakarote.ai_crm.entity.BO.UserUpdateBO;
import com.kakarote.ai_crm.entity.PO.CrmTenant;
import com.kakarote.ai_crm.entity.PO.ManagerDept;
import com.kakarote.ai_crm.entity.PO.ManagerRole;
import com.kakarote.ai_crm.entity.PO.ManagerUser;
import com.kakarote.ai_crm.entity.PO.ManagerUserRole;
import com.kakarote.ai_crm.entity.PO.SystemConfig;
import com.kakarote.ai_crm.entity.VO.LoginTenantOptionVO;
import com.kakarote.ai_crm.entity.VO.ManageUserVO;
import com.kakarote.ai_crm.mapper.ManageUserMapper;
import com.kakarote.ai_crm.mapper.ManagerDeptMapper;
import com.kakarote.ai_crm.mapper.SystemConfigMapper;
import com.kakarote.ai_crm.service.FileStorageService;
import com.kakarote.ai_crm.service.ICrmTenantService;
import com.kakarote.ai_crm.service.IManagerRoleService;
import com.kakarote.ai_crm.service.IManagerUserRoleService;
import com.kakarote.ai_crm.service.ManageUserService;
import com.kakarote.ai_crm.utils.UserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service("manageUserService")
public class ManageUserServiceImpl extends ServiceImpl<ManageUserMapper, ManagerUser> implements ManageUserService {

    private static final String ENTERPRISE_NAME_KEY = "enterprise_name";

    @Autowired
    private IManagerUserRoleService userRoleService;

    @Autowired
    private ManagerDeptMapper deptMapper;

    @Autowired
    private IManagerRoleService roleService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ICrmTenantService tenantService;

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    /**
     * 查询用户按Username。
     */
    @Override
    public ManagerUser queryUserByUsername(String username) {
        List<ManagerUser> users = queryUsersByUsername(username);
        ManagerUser managerUser = CollUtil.isEmpty(users) ? null : users.get(0);
        if (ObjectUtil.isNull(managerUser)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DOES_NOT_EXIST);
        }
        return managerUser;
    }

    /**
     * 查询用户按Username。
     */
    @Override
    public List<ManagerUser> queryUsersByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return Collections.emptyList();
        }
        return baseMapper.queryUsersByUsername(StrUtil.trim(username));
    }

    /**
     * 构建Login租户选项。
     */
    @Override
    public List<LoginTenantOptionVO> buildLoginTenantOptions(Collection<ManagerUser> users) {
        if (CollUtil.isEmpty(users)) {
            return Collections.emptyList();
        }

        Set<Long> tenantIds = users.stream()
                .map(ManagerUser::getTenantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, String> tenantNameMap = tenantService.listByIds(tenantIds)
                .stream()
                .collect(Collectors.toMap(CrmTenant::getTenantId, CrmTenant::getTenantName, (left, right) -> left));
        Map<Long, String> enterpriseNameMap = resolveEnterpriseNameMap(tenantIds, tenantNameMap);

        List<ManagerUser> sortedUsers = users.stream()
                .filter(user -> user.getTenantId() != null)
                .sorted(Comparator
                        .comparing((ManagerUser user) -> StrUtil.nullToDefault(enterpriseNameMap.get(user.getTenantId()), ""))
                        .thenComparing(ManagerUser::getUserId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        Map<Long, ManagerUser> firstUserByTenantId = new LinkedHashMap<>();
        for (ManagerUser user : sortedUsers) {
            firstUserByTenantId.putIfAbsent(user.getTenantId(), user);
        }

        return firstUserByTenantId.values().stream().map(user -> {
            LoginTenantOptionVO option = new LoginTenantOptionVO();
            option.setTenantId(user.getTenantId());
            option.setRealname(StrUtil.blankToDefault(StrUtil.trim(user.getRealname()), user.getUsername()));
            option.setTenantName(StrUtil.blankToDefault(enterpriseNameMap.get(user.getTenantId()), "未命名企业"));
            return option;
        }).collect(Collectors.toList());
    }

    /**
     * 解析公司名称MAP。
     */
    private Map<Long, String> resolveEnterpriseNameMap(Collection<Long> tenantIds, Map<Long, String> tenantNameMap) {
        if (CollUtil.isEmpty(tenantIds)) {
            return Collections.emptyMap();
        }

        Long previousTenantId = TenantContextHolder.getTenantId();
        Map<Long, String> enterpriseNameMap = new HashMap<>();
        try {
            for (Long tenantId : tenantIds) {
                String fallbackName = StrUtil.trim(tenantNameMap.get(tenantId));
                try {
                    TenantContextHolder.setTenantId(tenantId);
                    SystemConfig enterpriseConfig = systemConfigMapper.selectOne(
                            new LambdaQueryWrapper<SystemConfig>()
                                    .eq(SystemConfig::getConfigKey, ENTERPRISE_NAME_KEY)
                                    .last("LIMIT 1"));
                    String enterpriseName = enterpriseConfig != null ? StrUtil.trim(enterpriseConfig.getConfigValue()) : null;
                    enterpriseNameMap.put(tenantId, StrUtil.blankToDefault(enterpriseName, fallbackName));
                } catch (Exception e) {
                    log.warn("加载登录企业名称失败，回退租户名称: tenantId={}, error={}", tenantId, e.getMessage());
                    enterpriseNameMap.put(tenantId, fallbackName);
                }
            }
        } finally {
            if (previousTenantId != null) {
                TenantContextHolder.setTenantId(previousTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }

        return enterpriseNameMap;
    }

    /**
     * 新增用户。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUser(UserAddBO userAddBO) {
        if (StrUtil.isNotEmpty(userAddBO.getUsername())) {
            long duplicateCount = lambdaQuery()
                    .eq(ManagerUser::getUsername, userAddBO.getUsername())
                    .count();
            if (duplicateCount > 0) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "当前企业下用户名已存在");
            }
        }

        if (StrUtil.isNotEmpty(userAddBO.getPassword())) {
            BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
            String password = bCryptPasswordEncoder.encode(userAddBO.getPassword());
            userAddBO.setPassword(password);
        }

        Long parentId = normalizeParentUserId(userAddBO.getParentId());
        validateParentUser(null, parentId);

        ManagerUser manageUser = BeanUtil.copyProperties(userAddBO, ManagerUser.class);
        manageUser.setParentId(parentId);
        manageUser.setStatus(userAddBO.getStatus() != null ? userAddBO.getStatus() : 1);
        manageUser.setCreateTime(new Date());
        save(manageUser);

        if (CollUtil.isNotEmpty(userAddBO.getRoleIds())) {
            Long userId = manageUser.getUserId();
            List<ManagerUserRole> roles = userAddBO.getRoleIds().stream().map(roleId -> {
                ManagerUserRole ur = new ManagerUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                ur.setCreateUserId(UserUtil.getUserId());
                ur.setCreateTime(LocalDateTime.now());
                return ur;
            }).collect(Collectors.toList());
            userRoleService.saveBatch(roles, Const.BATCH_SAVE_SIZE);
        }
    }

    /**
     * 分页查询管理用户列表。
     */
    @Override
    public BasePage<ManageUserVO> queryPageList(UserQueryBO userQueryBO) {
        if (userQueryBO.getDeptId() != null) {
            List<ManagerDept> allDepts = deptMapper.selectList(null);
            Set<Long> deptIds = new LinkedHashSet<>();
            collectChildDeptIds(allDepts, userQueryBO.getDeptId(), deptIds, Const.AUTH_DATA_RECURSION_NUM);
            userQueryBO.setDeptIds(new ArrayList<>(deptIds));
        }
        BasePage<ManageUserVO> page = baseMapper.queryPageList(userQueryBO.parse(), userQueryBO);
        fillRoleInfo(page.getRecords());
        fillImgUrl(page.getRecords());
        fillTenantCreatorFlag(page.getRecords());
        return page;
    }

    /**
     * 处理collectChildDeptIds方法逻辑。
     */
    private void collectChildDeptIds(List<ManagerDept> allDepts, Long parentId, Set<Long> result, int depth) {
        if (parentId == null || depth <= 0 || !result.add(parentId)) {
            return;
        }
        for (ManagerDept dept : allDepts) {
            if (Objects.equals(parentId, dept.getParentId())) {
                collectChildDeptIds(allDepts, dept.getDeptId(), result, depth - 1);
            }
        }
    }

    /**
     * 填充角色信息。
     */
    private void fillRoleInfo(List<ManageUserVO> users) {
        if (CollUtil.isEmpty(users)) {
            return;
        }

        List<Long> userIds = users.stream().map(ManageUserVO::getUserId).collect(Collectors.toList());
        List<ManagerUserRole> userRoles = userRoleService.lambdaQuery()
                .in(ManagerUserRole::getUserId, userIds)
                .list();
        if (CollUtil.isEmpty(userRoles)) {
            users.forEach(u -> {
                u.setRoleIds(Collections.emptyList());
                u.setRoleNames(Collections.emptyList());
            });
            return;
        }

        Set<Long> roleIdSet = userRoles.stream().map(ManagerUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, String> roleNameMap = roleService.lambdaQuery()
                .in(ManagerRole::getRoleId, roleIdSet)
                .list()
                .stream()
                .collect(Collectors.toMap(ManagerRole::getRoleId, ManagerRole::getRoleName, (a, b) -> a));

        Map<Long, List<ManagerUserRole>> userRoleMap = userRoles.stream()
                .collect(Collectors.groupingBy(ManagerUserRole::getUserId));
        for (ManageUserVO user : users) {
            List<ManagerUserRole> urs = userRoleMap.getOrDefault(user.getUserId(), Collections.emptyList());
            user.setRoleIds(urs.stream().map(ManagerUserRole::getRoleId).collect(Collectors.toList()));
            user.setRoleNames(urs.stream()
                    .map(ur -> roleNameMap.getOrDefault(ur.getRoleId(), ""))
                    .filter(StrUtil::isNotEmpty)
                    .collect(Collectors.toList()));
        }
    }

    /**
     * 更新用户。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateBO updateBO) {
        ManagerUser userEntity = baseMapper.getUserId(updateBO.getUserId());
        if (ObjectUtil.isNull(userEntity)) {
            return;
        }
        boolean tenantCreator = isTenantCreator(userEntity, getCurrentTenant());

        if (ObjectUtil.isNotNull(updateBO.getImg())) {
            userEntity.setImg(updateBO.getImg());
        }
        if (ObjectUtil.isNotNull(updateBO.getMobile())) {
            userEntity.setMobile(updateBO.getMobile());
        }
        if (ObjectUtil.isNotNull(updateBO.getRealname())) {
            userEntity.setRealname(updateBO.getRealname());
        }
        if (StrUtil.isNotBlank(updateBO.getPassword())) {
            BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
            String passWord = bCryptPasswordEncoder.encode(updateBO.getPassword());
            userEntity.setPassword(passWord);
        }
        if (ObjectUtil.isNotNull(updateBO.getEmail())) {
            userEntity.setEmail(updateBO.getEmail());
        }
        if (ObjectUtil.isNotNull(updateBO.getDeptId())) {
            userEntity.setDeptId(updateBO.getDeptId());
        }
        if (ObjectUtil.isNotNull(updateBO.getPost())) {
            userEntity.setPost(updateBO.getPost());
        }
        if (ObjectUtil.isNotNull(updateBO.getSex())) {
            userEntity.setSex(updateBO.getSex());
        }
        if (ObjectUtil.isNotNull(updateBO.getStatus()) && !tenantCreator) {
            userEntity.setStatus(updateBO.getStatus());
        }
        if (updateBO.getParentId() != null) {
            Long parentId = normalizeParentUserId(updateBO.getParentId());
            validateParentUser(updateBO.getUserId(), parentId);
            userEntity.setParentId(parentId);
        }
        updateById(userEntity);

        if (updateBO.getRoleIds() != null && !tenantCreator) {
            userRoleService.lambdaUpdate()
                    .eq(ManagerUserRole::getUserId, updateBO.getUserId())
                    .remove();
            if (CollUtil.isNotEmpty(updateBO.getRoleIds())) {
                List<ManagerUserRole> newRoles = updateBO.getRoleIds().stream().map(roleId -> {
                    ManagerUserRole ur = new ManagerUserRole();
                    ur.setUserId(updateBO.getUserId());
                    ur.setRoleId(roleId);
                    ur.setCreateUserId(UserUtil.getUserId());
                    ur.setCreateTime(LocalDateTime.now());
                    return ur;
                }).collect(Collectors.toList());
                userRoleService.saveBatch(newRoles, Const.BATCH_SAVE_SIZE);
            }
        }
    }

    /**
     * 重置用户名。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetUsername(ResetUsernameBO resetUsernameBO) {
        if (resetUsernameBO == null || resetUsernameBO.getUserId() == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DOES_NOT_EXIST);
        }

        ManagerUser userEntity = baseMapper.getUserId(resetUsernameBO.getUserId());
        if (ObjectUtil.isNull(userEntity)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DOES_NOT_EXIST);
        }

        CrmTenant currentTenant = getRequiredCurrentTenant();
        if (!Objects.equals(userEntity.getTenantId(), currentTenant.getTenantId())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DOES_NOT_EXIST);
        }

        String oldUsername = StrUtil.trim(userEntity.getUsername());
        String newUsername = StrUtil.trim(resetUsernameBO.getUsername());
        if (StrUtil.isBlank(newUsername)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请输入新用户名");
        }

        boolean tenantCreator = isTenantCreator(userEntity, currentTenant);
        if (tenantCreator) {
            newUsername = newUsername.toLowerCase();
            if (!Validator.isEmail(newUsername, true)) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "主账号用户名必须是邮箱格式");
            }
            validateCurrentPassword(resetUsernameBO.getCurrentPassword());
            validateTenantContactEmailAvailable(newUsername, currentTenant.getTenantId());
        } else if (StrUtil.equalsIgnoreCase(newUsername, currentTenant.getContactEmail())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "普通员工不能使用企业创建者邮箱作为用户名");
        }

        if (StrUtil.equals(oldUsername, newUsername)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "新用户名不能与当前用户名相同");
        }

        long duplicateCount = lambdaQuery()
                .eq(ManagerUser::getUsername, newUsername)
                .ne(ManagerUser::getUserId, userEntity.getUserId())
                .count();
        if (duplicateCount > 0) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "当前企业下用户名已存在");
        }

        userEntity.setUsername(newUsername);
        if (tenantCreator && (StrUtil.isBlank(userEntity.getEmail()) || StrUtil.equalsIgnoreCase(userEntity.getEmail(), oldUsername))) {
            userEntity.setEmail(newUsername);
        }
        updateById(userEntity);

        if (tenantCreator) {
            currentTenant.setContactEmail(newUsername);
            currentTenant.setUpdateTime(new Date());
            tenantService.updateById(currentTenant);
        }
    }

    /**
     * 设置用户状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setUserStatus(UserStatusBO userStatusBO) {
        List<ManagerUser> managerUserList = lambdaQuery()
                .in(ManagerUser::getUserId, userStatusBO.getIds())
                .list();
        managerUserList.forEach(manageUserEntity -> manageUserEntity.setStatus(userStatusBO.getStatus()));
        updateBatchById(managerUserList);
    }

    /**
     * 查询Login用户。
     */
    @Override
    public ManageUserVO queryLoginUser() {
        ManagerUser userEntity = baseMapper.getUserId(UserUtil.getUserId());
        if (ObjectUtil.isNull(userEntity)) {
            return null;
        }

        ManageUserVO vo = BeanUtil.copyProperties(userEntity, ManageUserVO.class);
        if (ObjectUtil.isNotNull(userEntity.getDeptId())) {
            ManagerDept dept = deptMapper.selectById(userEntity.getDeptId());
            if (dept != null) {
                vo.setDeptName(dept.getDeptName());
            }
        }
        fillRoleInfo(Collections.singletonList(vo));
        fillImgUrl(Collections.singletonList(vo));
        fillTenantCreatorFlag(Collections.singletonList(vo));
        return vo;
    }

    /**
     * 填充IMG地址。
     */
    private void fillImgUrl(List<ManageUserVO> voList) {
        for (ManageUserVO vo : voList) {
            if (StrUtil.isNotBlank(vo.getImg())) {
                try {
                    vo.setImgUrl(fileStorageService.getUrl(vo.getImg()));
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 标记当前租户企业创建者主账号。
     */
    private void fillTenantCreatorFlag(List<ManageUserVO> voList) {
        if (CollUtil.isEmpty(voList)) {
            return;
        }
        String tenantCreatorUsername = getCurrentTenantContactEmail();
        for (ManageUserVO vo : voList) {
            vo.setTenantCreator(StrUtil.isNotBlank(tenantCreatorUsername)
                    && StrUtil.equalsIgnoreCase(tenantCreatorUsername, vo.getUsername()));
        }
    }

    /**
     * 获取当前租户。
     */
    private CrmTenant getCurrentTenant() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return null;
        }
        return tenantService.getById(tenantId);
    }

    /**
     * 获取当前租户，失败时抛出业务异常。
     */
    private CrmTenant getRequiredCurrentTenant() {
        CrmTenant tenant = getCurrentTenant();
        if (tenant == null || tenant.getTenantId() == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "当前企业信息不存在");
        }
        return tenant;
    }

    /**
     * 获取当前租户创建者邮箱。
     */
    private String getCurrentTenantContactEmail() {
        CrmTenant tenant = getCurrentTenant();
        return tenant == null ? null : StrUtil.trim(tenant.getContactEmail());
    }

    /**
     * 判断员工是否当前租户企业创建者。
     */
    private boolean isTenantCreator(ManagerUser user, CrmTenant tenant) {
        return user != null
                && tenant != null
                && StrUtil.isNotBlank(tenant.getContactEmail())
                && StrUtil.equalsIgnoreCase(tenant.getContactEmail(), user.getUsername());
    }

    /**
     * 验证当前登录密码。
     */
    private void validateCurrentPassword(String currentPassword) {
        if (StrUtil.isBlank(currentPassword)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请输入当前登录密码");
        }
        ManagerUser currentUser = baseMapper.getUserId(UserUtil.getUserId());
        if (currentUser == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_USER_DOES_NOT_EXIST);
        }
        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        if (!bCryptPasswordEncoder.matches(currentPassword, currentUser.getPassword())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_OLD_PASSWORD_ERROR);
        }
    }

    /**
     * 验证租户联系人邮箱未被其他租户使用。
     */
    private void validateTenantContactEmailAvailable(String email, Long currentTenantId) {
        long duplicateCount = tenantService.lambdaQuery()
                .ne(CrmTenant::getTenantId, currentTenantId)
                .apply("LOWER(contact_email) = {0}", email)
                .count();
        if (duplicateCount > 0) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_EMAIL_ALREADY_REGISTERED);
        }
    }

    /**
     * 更新密码。
     */
    @Override
    public void updatePassword(String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_PASSWORD_TOO_SHORT);
        }

        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        ManagerUser userEntity = baseMapper.getUserId(UserUtil.getUserId());
        boolean matches = bCryptPasswordEncoder.matches(oldPassword, userEntity.getPassword());
        if (!matches) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_OLD_PASSWORD_ERROR);
        }
        String passWord = bCryptPasswordEncoder.encode(newPassword);
        userEntity.setPassword(passWord);
        updateById(userEntity);
    }

    /**
     * 删除按ID。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Long[] userIds) {
        List<Long> userIdList = Arrays.asList(userIds);
        if (CollUtil.isEmpty(userIdList)) {
            return;
        }

        List<Long> protectedRoleIds = roleService.lambdaQuery()
                .select(ManagerRole::getRoleId)
                .eq(ManagerRole::getRealm, RegistrationServiceImpl.SUPER_ADMIN_REALM)
                .list()
                .stream()
                .map(ManagerRole::getRoleId)
                .filter(Objects::nonNull)
                .toList();
        if (CollUtil.isNotEmpty(protectedRoleIds)) {
            boolean containsProtectedUser = userRoleService.lambdaQuery()
                    .in(ManagerUserRole::getUserId, userIdList)
                    .in(ManagerUserRole::getRoleId, protectedRoleIds)
                    .exists();
            if (containsProtectedUser) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "主账号不允许删除");
            }
        }

        removeByIds(userIdList);
        List<ManagerUserRole> list = userRoleService.lambdaQuery()
                .in(ManagerUserRole::getUserId, userIdList)
                .list();
        if (CollUtil.isNotEmpty(list)) {
            userRoleService.removeByIds(list);
        }
    }

    /**
     * 标准化Parent用户ID。
     */
    private Long normalizeParentUserId(Long parentId) {
        return parentId == null || Objects.equals(parentId, 0L) ? null : parentId;
    }

    /**
     * 校验Parent用户。
     */
    private void validateParentUser(Long userId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(userId, parentId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "直属上级不能选择当前员工");
        }

        Map<Long, Long> parentByUserId = new HashMap<>();
        lambdaQuery()
                .select(ManagerUser::getUserId, ManagerUser::getParentId)
                .list()
                .forEach(user -> {
                    if (user.getUserId() != null) {
                        parentByUserId.put(user.getUserId(), normalizeParentUserId(user.getParentId()));
                    }
                });

        if (!parentByUserId.containsKey(parentId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "直属上级不存在");
        }

        if (userId != null && wouldCreateParentCycle(userId, parentId, parentByUserId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "直属上级不能选择当前员工或其下级员工");
        }
    }

    /**
     * 处理wouldCreateParentCycle方法逻辑。
     */
    private boolean wouldCreateParentCycle(Long userId, Long parentId, Map<Long, Long> parentByUserId) {
        Set<Long> visited = new HashSet<>();
        Long currentParentId = parentId;

        while (currentParentId != null && visited.add(currentParentId)) {
            if (Objects.equals(userId, currentParentId)) {
                return true;
            }
            currentParentId = parentByUserId.get(currentParentId);
        }
        return false;
    }
}
