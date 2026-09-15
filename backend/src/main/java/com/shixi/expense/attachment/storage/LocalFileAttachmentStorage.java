package com.shixi.expense.attachment.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shixi.expense.attachment.config.AttachmentStorageProperties;

/**
 * 使用本机目录保存、读取和删除附件的存储实现。
 *
 * <p>当前阶段不依赖 Docker 和 MinIO。以后切换对象存储时，只需增加新的
 * AttachmentStorage 实现，AttachmentService 的业务流程不需要整体重写。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "app.attachment",
        name = "storage-type",
        havingValue = "local",
        matchIfMissing = true)
public class LocalFileAttachmentStorage implements AttachmentStorage {

    /** 规范化后的附件根目录，用来约束所有文件只能保存在该目录以内。 */
    private final Path rootDirectory;

    public LocalFileAttachmentStorage(
            AttachmentStorageProperties properties) {
        this.rootDirectory = Path.of(properties.getRootDirectory())
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 保存文件的核心框架。
     *
     * <p>保存前先规范化目标路径，再检查目标路径仍位于 rootDirectory 内，
     * 防止 objectKey 中的 ../ 把文件写到项目目录之外。</p>
     */
    @Override
    public String save(InputStream inputStream, String objectKey)
            throws IOException {
        // 输入流和存储 key 都必须有效。
        if (inputStream == null) {
            throw new IllegalArgumentException(
                    "附件输入流不能为空"
            );
        }

        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "附件存储 key 不能为空"
            );
        }

        // 将相对 key 拼接到附件根目录，并消除路径中的 . 和 ..。
        Path path = rootDirectory.resolve(objectKey).normalize();

        // 最终路径必须仍位于附件根目录内，防止路径穿越。
        if (!path.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("附件存储路径不合法");
        }

        // 创建目标文件的父目录。
        Path parentDirectory = path.getParent();
        if (parentDirectory == null) {
            throw new IllegalArgumentException("附件目标路径缺少父目录");
        }

        Files.createDirectories(parentDirectory);

        // 默认不覆盖已有文件；目标文件重名时直接抛出异常。
        Files.copy(inputStream, path);

        // 数据库只保存相对 key，不暴露服务器绝对路径。
        return objectKey;
    }

    /**
     * 从本地目录读取附件。
     *
     * <p>读取前执行与保存相同的路径安全检查，避免访问附件目录之外的文件。</p>
     */
    @Override
    public Resource load(String objectKey) throws IOException {
        // 存储 key 必须有效。
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "附件存储 key 不能为空"
            );
        }
        // 计算规范化后的目标路径。
        Path path = rootDirectory.resolve(objectKey).normalize();
        // 最终路径必须位于附件根目录内，防止路径穿越。
        if (!path.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("附件存储路径不合法");
        }
        // 目标必须存在并且是普通文件，目录不能作为附件下载。
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("附件文件不存在");
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("附件路径不是普通文件");
        }
        // 将本地路径转换为 Spring Resource，并确认资源可以读取。
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalArgumentException(
                    "附件文件不存在或无法读取"
            );
        }
        return resource;
    }

    /** 删除文件的核心框架，主要用于数据库写入失败后的补偿清理。 */
    @Override
    public void delete(String objectKey) throws IOException {
        // 删除文件时使用与保存相同的参数和路径安全检查。
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "附件存储 key 不能为空"
            );
        }
        Path path = rootDirectory.resolve(objectKey).normalize();
        if (!path.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("附件存储路径不合法");
        }

        Files.deleteIfExists(path);
    }
}
