package com.kakarote.syncdata.config;

import com.kakarote.syncdata.SyncProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * 创建老 WK CRM MySQL 数据源。
     */
    @Bean
    public DataSource oldCrmDataSource(SyncProperties properties) {
        return createDataSource("old-crm", properties.getOldCrm());
    }

    /**
     * 创建目标 AI CRM PostgreSQL 数据源。
     */
    @Bean
    public DataSource targetDataSource(SyncProperties properties) {
        return createDataSource("ai-crm-target", properties.getTarget());
    }

    /**
     * 创建访问老 WK CRM 的 JdbcTemplate。
     */
    @Bean
    public JdbcTemplate oldCrmJdbcTemplate(@Qualifier("oldCrmDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(500);
        return jdbcTemplate;
    }

    /**
     * 创建访问目标 AI CRM 的 JdbcTemplate。
     */
    @Bean
    public JdbcTemplate targetJdbcTemplate(@Qualifier("targetDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 根据配置创建 Hikari 数据源实例。
     */
    private HikariDataSource createDataSource(String poolName, SyncProperties.DataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(tuneJdbcUrl(properties.getJdbcUrl()));
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    /**
     * 为 PostgreSQL 目标库补充批量写入和 TCP 保活参数，降低远程同步时的网络往返和断连概率。
     */
    private String tuneJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:")) {
            return jdbcUrl;
        }
        String tuned = jdbcUrl;
        tuned = appendParameterIfMissing(tuned, "reWriteBatchedInserts", "true");
        tuned = appendParameterIfMissing(tuned, "tcpKeepAlive", "true");
        return tuned;
    }

    private String appendParameterIfMissing(String jdbcUrl, String name, String value) {
        if (jdbcUrl.matches(".*[?&]" + java.util.regex.Pattern.quote(name) + "=.*")) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + name + "=" + value;
    }
}
