package com.kakarote.ai_crm.ai.context;

import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * AI 上下文持有者
 * 用于在 AI 工具调用时传递当前用户信息
 * 由于 Spring AI 的工具调用在不同线程执行，无法通过 SecurityContext 获取用户信息
 * 因此使用这个类来存储和传递用户ID
 *
 * 线程安全说明：
 * - SESSION_USER_MAP 使用 ConcurrentHashMap，是线程安全的
 * - 以 sessionId 为 key 存储 userId，不同用户的请求使用不同的 sessionId，不会混淆
 * - 使用标准 ThreadLocal 配合 Micrometer context-propagation
 *   通过 ContextPropagationConfig 注册 ThreadLocalAccessor，Reactor 会自动传播上下文
 */
public class AiContextHolder {

    private static final Logger log = LoggerFactory.getLogger(AiContextHolder.class);

    /**
     * 存储会话ID与用户ID的映射（主要的线程安全存储）
     * Key: sessionId
     * Value: userId
     * 这是多用户并发时最可靠的方式
     */
    private static final Map<Long, Long> SESSION_USER_MAP = new ConcurrentHashMap<>();

    /**
     * 存储会话ID与租户ID的映射
     * Key: sessionId
     * Value: tenantId
     */
    private static final Map<Long, Long> SESSION_TENANT_MAP = new ConcurrentHashMap<>();

    private static final Map<Long, Long> SESSION_CUSTOMER_MAP = new ConcurrentHashMap<>();

    private static final Map<Long, Long> SESSION_EMPLOYEE_MAP = new ConcurrentHashMap<>();

    private static final Map<Long, Long> SESSION_RELATION_MAP = new ConcurrentHashMap<>();

    /**
     * 当前正在处理的会话ID
     * 通过 Micrometer context-propagation 实现跨线程传递
     */
    private static final ThreadLocal<Long> CURRENT_SESSION_ID = new ThreadLocal<>();

    /**
     * 当前正在处理的用户ID（备用）
     * 通过 Micrometer context-propagation 实现跨线程传递
     */
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private static final ThreadLocal<Long> CURRENT_CUSTOMER_ID = new ThreadLocal<>();

    private static final ThreadLocal<Long> CURRENT_EMPLOYEE_ID = new ThreadLocal<>();

    private static final ThreadLocal<Long> CURRENT_RELATION_ID = new ThreadLocal<>();

    /**
     * 设置会话上下文
     * 在开始 AI 对话时调用
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     */
    public static void setContext(Long sessionId, Long userId) {
        setContext(sessionId, userId, null);
    }

    /**
     * 设置会话上下文（含租户ID）
     * 在开始 AI 对话时调用
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param tenantId  租户ID
     */
    public static void setContext(Long sessionId, Long userId, Long tenantId) {
        setContext(sessionId, userId, tenantId, null);
    }

    public static void setContext(Long sessionId, Long userId, Long tenantId, Long customerId) {
        setContext(sessionId, userId, tenantId, customerId, null);
    }

    public static void setContext(Long sessionId, Long userId, Long tenantId, Long customerId, Long employeeId) {
        setContext(sessionId, userId, tenantId, customerId, employeeId, null);
    }

    public static void setContext(Long sessionId, Long userId, Long tenantId,
                                  Long customerId, Long employeeId, Long relationId) {
        if (sessionId != null && userId != null) {
            // 存储到 Map 中（线程安全，全局可访问）
            SESSION_USER_MAP.put(sessionId, userId);
            // 同时设置 ThreadLocal（用于跨线程传递，通过 context-propagation）
            CURRENT_SESSION_ID.set(sessionId);
            CURRENT_USER_ID.set(userId);
            // 存储租户ID
            if (tenantId != null) {
                SESSION_TENANT_MAP.put(sessionId, tenantId);
                TenantContextHolder.setTenantId(tenantId);
            }
            if (customerId != null) {
                SESSION_CUSTOMER_MAP.put(sessionId, customerId);
                CURRENT_CUSTOMER_ID.set(customerId);
            } else {
                SESSION_CUSTOMER_MAP.remove(sessionId);
                CURRENT_CUSTOMER_ID.remove();
            }
            if (employeeId != null) {
                SESSION_EMPLOYEE_MAP.put(sessionId, employeeId);
                CURRENT_EMPLOYEE_ID.set(employeeId);
            } else {
                SESSION_EMPLOYEE_MAP.remove(sessionId);
                CURRENT_EMPLOYEE_ID.remove();
            }
            if (relationId != null) {
                SESSION_RELATION_MAP.put(sessionId, relationId);
                CURRENT_RELATION_ID.set(relationId);
            } else {
                SESSION_RELATION_MAP.remove(sessionId);
                CURRENT_RELATION_ID.remove();
            }
            log.debug("设置 AI 上下文: sessionId={}, userId={}, tenantId={}, customerId={}, employeeId={}, relationId={}, 线程={}",
                sessionId, userId, tenantId, customerId, employeeId, relationId, Thread.currentThread().getName());
        }
    }

    /**
     * 绑定当前线程的用户与租户上下文，不关联会话ID。
     * 适用于线程池异步任务等需要显式传递上下文的场景。
     */
    public static void bindThreadContext(Long userId, Long tenantId) {
        CURRENT_SESSION_ID.remove();
        CURRENT_CUSTOMER_ID.remove();
        CURRENT_EMPLOYEE_ID.remove();
        CURRENT_RELATION_ID.remove();
        if (userId == null) {
            CURRENT_USER_ID.remove();
        } else {
            CURRENT_USER_ID.set(userId);
        }

        if (tenantId == null) {
            TenantContextHolder.clear();
        } else {
            TenantContextHolder.setTenantId(tenantId);
        }

        log.debug("绑定线程 AI 上下文: userId={}, tenantId={}, 线程={}",
                userId, tenantId, Thread.currentThread().getName());
    }

    /**
     * 直接设置 sessionId（供 context-propagation 使用）
     * 优化：如果当前线程已有相同的 sessionId，跳过设置避免重复操作
     */
    public static void setSessionId(Long sessionId) {
        if (sessionId == null) {
            return;
        }

        // 优化：如果已经是相同的 sessionId，跳过设置
        Long currentSessionId = CURRENT_SESSION_ID.get();
        if (sessionId.equals(currentSessionId)) {
            return;  // 已经设置过，无需重复设置
        }

        CURRENT_SESSION_ID.set(sessionId);
        // 从 Map 中恢复 userId
        Long userId = SESSION_USER_MAP.get(sessionId);
        if (userId != null) {
            CURRENT_USER_ID.set(userId);
        }
        // 从 Map 中恢复 tenantId
        Long tenantId = SESSION_TENANT_MAP.get(sessionId);
        if (tenantId != null) {
            TenantContextHolder.setTenantId(tenantId);
        }
        Long customerId = SESSION_CUSTOMER_MAP.get(sessionId);
        if (customerId != null) {
            CURRENT_CUSTOMER_ID.set(customerId);
        } else {
            CURRENT_CUSTOMER_ID.remove();
        }
        Long employeeId = SESSION_EMPLOYEE_MAP.get(sessionId);
        if (employeeId != null) {
            CURRENT_EMPLOYEE_ID.set(employeeId);
        } else {
            CURRENT_EMPLOYEE_ID.remove();
        }
        Long relationId = SESSION_RELATION_MAP.get(sessionId);
        if (relationId != null) {
            CURRENT_RELATION_ID.set(relationId);
        } else {
            CURRENT_RELATION_ID.remove();
        }
        log.trace("恢复 AI 上下文: sessionId={}, userId={}, tenantId={}, customerId={}, employeeId={}, relationId={}",
            sessionId, userId, tenantId, customerId, employeeId, relationId);
    }

    /**
     * 获取当前用户ID
     * 优先从 SESSION_USER_MAP 获取（更可靠），如果获取不到则尝试从 ThreadLocal 获取
     *
     * @return 用户ID，如果不存在返回 null
     */
    public static Long getCurrentUserId() {
        // 优先尝试通过 sessionId 从 Map 获取（最可靠）
        Long sessionId = CURRENT_SESSION_ID.get();

        if (sessionId != null) {
            Long userId = SESSION_USER_MAP.get(sessionId);
            if (userId != null) {
                log.trace("获取用户ID: sessionId={}, userId={}", sessionId, userId);
                return userId;
            }
        }

        // 备用：直接从 ThreadLocal 获取
        return CURRENT_USER_ID.get();
    }

    /**
     * 获取当前会话ID
     */
    public static Long getCurrentSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    public static Long getCurrentCustomerId() {
        Long sessionId = CURRENT_SESSION_ID.get();
        if (sessionId != null) {
            Long customerId = SESSION_CUSTOMER_MAP.get(sessionId);
            if (customerId != null) {
                return customerId;
            }
        }
        return CURRENT_CUSTOMER_ID.get();
    }

    public static Long getCurrentEmployeeId() {
        Long sessionId = CURRENT_SESSION_ID.get();
        if (sessionId != null) {
            Long employeeId = SESSION_EMPLOYEE_MAP.get(sessionId);
            if (employeeId != null) {
                return employeeId;
            }
        }
        return CURRENT_EMPLOYEE_ID.get();
    }

    public static Long getCurrentRelationId() {
        Long sessionId = CURRENT_SESSION_ID.get();
        if (sessionId != null) {
            Long relationId = SESSION_RELATION_MAP.get(sessionId);
            if (relationId != null) {
                return relationId;
            }
        }
        return CURRENT_RELATION_ID.get();
    }

    /**
     * 获取会话对应的用户ID（从全局 Map 中）
     *
     * @param sessionId 会话ID
     * @return 用户ID，如果不存在返回 null
     */
    public static Long getSessionUser(Long sessionId) {
        return sessionId != null ? SESSION_USER_MAP.get(sessionId) : null;
    }

    /**
     * 清除当前线程的上下文
     * 在对话结束时调用
     */
    public static void clear() {
        Long sessionId = CURRENT_SESSION_ID.get();
        log.trace("清除 AI 上下文: sessionId={}", sessionId);
        // 注意：不要从 SESSION_USER_MAP 中删除，因为其他线程可能还需要
        // SESSION_USER_MAP 的清理应该通过 clearSession 方法显式调用
        CURRENT_SESSION_ID.remove();
        CURRENT_USER_ID.remove();
        CURRENT_CUSTOMER_ID.remove();
        CURRENT_EMPLOYEE_ID.remove();
        CURRENT_RELATION_ID.remove();
    }

    /**
     * 清理当前线程绑定的用户与租户上下文。
     */
    public static void clearThreadContext() {
        log.trace("清理线程 AI 上下文: thread={}", Thread.currentThread().getName());
        CURRENT_SESSION_ID.remove();
        CURRENT_USER_ID.remove();
        CURRENT_CUSTOMER_ID.remove();
        CURRENT_EMPLOYEE_ID.remove();
        CURRENT_RELATION_ID.remove();
        TenantContextHolder.clear();
    }

    /**
     * 清除指定会话的上下文
     * 在会话结束或删除时调用
     *
     * @param sessionId 会话ID
     */
    public static void clearSession(Long sessionId) {
        if (sessionId != null) {
            SESSION_USER_MAP.remove(sessionId);
            SESSION_TENANT_MAP.remove(sessionId);
            SESSION_CUSTOMER_MAP.remove(sessionId);
            SESSION_EMPLOYEE_MAP.remove(sessionId);
            SESSION_RELATION_MAP.remove(sessionId);
            log.trace("移除会话: sessionId={}", sessionId);
        }
    }

    /**
     * 获取当前租户ID（从 SESSION_TENANT_MAP 中通过 sessionId 获取）
     */
    public static Long getCurrentTenantId() {
        Long sessionId = CURRENT_SESSION_ID.get();
        if (sessionId != null) {
            Long tenantId = SESSION_TENANT_MAP.get(sessionId);
            if (tenantId != null) {
                return tenantId;
            }
        }
        return TenantContextHolder.getTenantId();
    }

    /**
     * 获取当前活跃会话数（用于调试）
     */
    public static int getActiveSessionCount() {
        return SESSION_USER_MAP.size();
    }
}
