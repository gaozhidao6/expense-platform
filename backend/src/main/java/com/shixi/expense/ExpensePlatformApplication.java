package com.shixi.expense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 智能报销平台后端启动入口。 */
@SpringBootApplication
public class ExpensePlatformApplication {

    /** 启动 Spring Boot 容器并扫描当前包下的业务组件。 */
    public static void main(String[] args) {
        SpringApplication.run(ExpensePlatformApplication.class, args);
    }
}
