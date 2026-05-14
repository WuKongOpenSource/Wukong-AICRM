package com.kakarote.ai_crm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.PostgreDialect;
import com.kakarote.ai_crm.config.auth.GlobalDataPermissionHandler;
import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * mybatis-plus配置
 * 这里把租户隔离、数据权限和分页统一接到 MyBatis 拦截链上，顺序错误会直接影响 SQL 改写结果。
 * @author hmb
 */
@Configuration
@MapperScan(basePackages = {"com.kakarote.ai_crm.mapper"})
public class MybatisPlusConfig {

    private static final Set<String> IGNORE_TENANT_TABLES = Set.of(
            "manager_menu",            // 菜单定义全局共享
            "manager_role_menu",       // 跟随 role 隔离，role 已按租户隔离
            "crm_tenant",              // 租户主表本身不做隔离
            "crm_custom_field_pool",   // 字段池全局共享，跨租户复用物理列
            "crm_ai_model_pricing"     // 模型积分倍率为平台全局配置，仅数据库维护
    );

    /**
     * 处理mybatisPlusInterceptor方法逻辑。
     */
    /**
     * 不需要租户隔离的表（全局共享数据）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(GlobalDataPermissionHandler dataPermissionHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 先拼 tenant_id，再叠加数据权限和分页；如果把分页提前，count/sql 重写可能拿不到完整的隔离条件。
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            /**
             * 获取租户ID。
             */
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContextHolder.getTenantId();
                if (tenantId == null) {
                    // 未登录场景（如登录接口）返回默认值，
                    // 具体排除通过 @InterceptorIgnore 注解处理；默认值本身不代表可访问任何真实租户数据。
                    return new LongValue(0);
                }
                return new LongValue(tenantId);
            }

            /**
             * 获取租户ID列。
             */
            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            /**
             * 处理ignoreTable方法逻辑。
             */
            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TENANT_TABLES.contains(tableName);
            }
        });
        interceptor.addInnerInterceptor(tenantInterceptor);
        // 数据权限依赖前面已经注入好的租户条件，只对业务模块追加 owner/部门维度的过滤 SQL。
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataPermissionHandler));

        // 分页拦截器
        PaginationInnerInterceptor innerInterceptor = new PaginationInnerInterceptor();
        innerInterceptor.setDbType(DbType.POSTGRE_SQL);
        innerInterceptor.setDialect(new PostgreDialect());
        innerInterceptor.setOptimizeJoin(true);
        interceptor.addInnerInterceptor(innerInterceptor);

        return interceptor;
    }
}
