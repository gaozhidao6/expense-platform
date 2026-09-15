package com.shixi.expense.support;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 无需登录的健康检查接口，用于本地启动验证和部署探针。 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /** 返回服务状态和当前时间，不执行数据库等重依赖检查。 */
    @GetMapping
    Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "expense-platform",
                "time", OffsetDateTime.now().toString());
    }
}
