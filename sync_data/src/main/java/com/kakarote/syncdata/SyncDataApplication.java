package com.kakarote.syncdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SyncProperties.class)
public class SyncDataApplication {

    /**
     * 应用启动入口。
     * 启动时只提供 HTTP API，不在进程启动阶段自动执行全量同步。
     */
    public static void main(String[] args) {
        SpringApplication.run(SyncDataApplication.class, args);
    }
}
