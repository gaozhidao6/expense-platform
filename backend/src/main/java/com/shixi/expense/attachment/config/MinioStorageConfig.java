package com.shixi.expense.attachment.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 只有选择 minio 存储时才创建 MinIO 客户端。 */
@Configuration
@EnableConfigurationProperties(MinioStorageProperties.class)
@ConditionalOnProperty(
        prefix = "app.attachment",
        name = "storage-type",
        havingValue = "minio")
public class MinioStorageConfig {

    @Bean
    public MinioClient minioClient(MinioStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /** 应用启动时确保附件 bucket 已存在。 */
    @Bean
    public ApplicationRunner minioBucketInitializer(
            MinioClient minioClient,
            MinioStorageProperties properties) {
        return arguments -> {
            String bucket = properties.bucket();
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalStateException("MinIO bucket 不能为空");
            }

            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build());

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build());
            }
        };
    }
}
