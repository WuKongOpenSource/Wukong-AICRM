package com.kakarote.ai_crm.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.PostgreDialect;
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
 * @author hmb
 */
@Configuration
@MapperScan(basePackages = {"com.kakarote.ai_crm.mapper"})
public class MybatisPlusConfig {

    /**
     * 不需要租户隔离的表（全局共享数据）
     */
    private static final Set<String> IGNORE_TENANT_TABLES = Set.of(
            "manager_menu",       // 菜单定义全局共享
            "manager_role_menu",  // 跟随 role 隔离，role 已按租户隔离
            "crm_tenant"          // 租户主表本身不做隔离
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 租户拦截器（必须在分页拦截器之前）
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContextHolder.getTenantId();
                if (tenantId == null) {
                    // 未登录场景（如登录接口）返回默认值，
                    // 具体排除通过 @InterceptorIgnore 注解处理
                    return new LongValue(0);
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TENANT_TABLES.contains(tableName);
            }
        });
        interceptor.addInnerInterceptor(tenantInterceptor);

        // 分页拦截器
        PaginationInnerInterceptor innerInterceptor = new PaginationInnerInterceptor();
        innerInterceptor.setDbType(DbType.POSTGRE_SQL);
        innerInterceptor.setDialect(new PostgreDialect());
        innerInterceptor.setOptimizeJoin(true);
        interceptor.addInnerInterceptor(innerInterceptor);

        return interceptor;
    }
}
