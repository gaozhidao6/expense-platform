package com.shixi.expense.attachment.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 附件上传和本地存储配置。
 *
 * <p>把目录、大小和文件类型放在配置文件中，避免在业务代码里写死。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.attachment")
public class AttachmentStorageProperties {

    /** 本地附件根目录；数据库中不保存这个绝对路径。 */
    private String rootDirectory = "./data/attachments";

    /** 存储实现类型：local 或 minio。 */
    private String storageType = "local";

    /** 单个附件允许的最大字节数，默认 10 MB。 */
    private long maxFileSize = 10 * 1024 * 1024L;

    /** 当前阶段允许上传的文件扩展名，统一使用小写且不带点号。 */
    private List<String> allowedExtensions = new ArrayList<>(
            List.of("jpg", "jpeg", "png", "pdf"));
}
