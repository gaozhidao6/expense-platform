package com.shixi.expense.attachment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MinIO 连接配置。 */
@ConfigurationProperties(prefix = "app.attachment.minio")
public record MinioStorageProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket) {
}
