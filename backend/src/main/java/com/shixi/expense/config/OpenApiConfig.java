package com.shixi.expense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 文档配置，为 Swagger UI 提供接口标题和版本信息。 */
@Configuration
public class OpenApiConfig {

    /** 创建项目的 OpenAPI 元数据。 */
    @Bean
    OpenAPI expensePlatformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Expense Platform API")
                .version("v1"));
    }
}
