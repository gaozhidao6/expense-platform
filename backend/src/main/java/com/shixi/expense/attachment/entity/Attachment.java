package com.shixi.expense.attachment.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 报销附件实体，对应附件元数据表。
 *
 * <p>当前只定义数据库字段映射，不实现文件保存、删除和下载逻辑。</p>
 */
@Data
@TableName("attachment")
public class Attachment {

    /** 附件元数据主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属报销单 ID。 */
    private Long reportId;

    /** 用户上传时使用的原始文件名。 */
    private String originalFilename;

    /** 文件在本地存储中的相对路径或对象 key。 */
    private String storagePath;

    /** 文件大小，单位为字节。 */
    private Long fileSize;

    /** 文件的 MIME 类型。 */
    private String contentType;

    /** 文件内容的 SHA-256，用于后续去重。 */
    private String sha256;

    /** 元数据创建时间。 */
    private LocalDateTime createdAt;
}
