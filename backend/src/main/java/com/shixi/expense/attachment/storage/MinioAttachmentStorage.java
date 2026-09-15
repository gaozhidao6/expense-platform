package com.shixi.expense.attachment.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.shixi.expense.attachment.config.MinioStorageProperties;

/** 使用 MinIO 实现附件上传、读取和删除。 */
@Component
@ConditionalOnProperty(
        prefix = "app.attachment",
        name = "storage-type",
        havingValue = "minio")
public class MinioAttachmentStorage implements AttachmentStorage {

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioAttachmentStorage(
            MinioClient minioClient,
            MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public String save(InputStream inputStream, String objectKey) throws IOException {
        // 上传前校验输入流、对象 key 和 bucket 配置。
        if (inputStream == null
                || objectKey == null
                || objectKey.isBlank()
                || properties.bucket() == null
                || properties.bucket().isBlank()) {
            throw new IllegalArgumentException("MinIO 上传参数不能为空");
        }
        // 文件大小未知时使用分片上传，分片大小为 10 MB。
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .stream(inputStream, -1L, 10L * 1024 * 1024)
                .build();
        // SDK 异常统一转换为存储接口声明的 IOException。
        try {
            minioClient.putObject(args);
        } catch (Exception exception) {
            throw new IOException("向 MinIO 上传附件失败", exception);
        }
        // 数据库只保存对象 key，不暴露存储服务内部地址。
        return objectKey;
    }

    @Override
    public Resource load(String objectKey) throws IOException {
        // 读取前校验对象 key 和 bucket 配置。
        if (objectKey == null
                || objectKey.isBlank()
                || properties.bucket() == null
                || properties.bucket().isBlank()) {
            throw new IllegalArgumentException("MinIO 读取参数不能为空");
        }
        // 使用 bucket 和对象 key 定位附件。
        GetObjectArgs args = GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build();
        // 读取结束后自动关闭 MinIO 返回的输入流。
        try (InputStream inputStream = minioClient.getObject(args)) {
            // 当前附件上限为 10 MB，可安全读入内存并包装为 Resource。
            return new ByteArrayResource(inputStream.readAllBytes());
        } catch (Exception exception) {
            throw new IOException("从 MinIO 读取附件失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        // 删除前校验对象 key 和 bucket 配置。
        if (objectKey == null
                || objectKey.isBlank()
                || properties.bucket() == null
                || properties.bucket().isBlank()) {
            throw new IllegalArgumentException("MinIO 删除参数不能为空");
        }
        // 使用 bucket 和对象 key 定位待删除附件。
        RemoveObjectArgs args = RemoveObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build();

        // SDK 异常统一转换为 IOException。
        try {
            minioClient.removeObject(args);
        } catch (Exception exception) {
            throw new IOException("从 MinIO 删除附件失败", exception);
        }
    }
}
