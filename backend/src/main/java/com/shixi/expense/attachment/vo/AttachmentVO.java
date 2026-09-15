package com.shixi.expense.attachment.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 返回给前端的附件信息。
 *
 * <p>只暴露业务需要的元数据，不直接返回附件实体或服务器绝对路径。</p>
 */
@Data
public class AttachmentVO {

    /** 附件 ID。 */
    private Long id;

    /** 所属报销单 ID。 */
    private Long reportId;

    /** 原始文件名。 */
    private String originalFilename;

    /** 文件大小，单位为字节。 */
    private Long fileSize;

    /** 文件的 MIME 类型。 */
    private String contentType;

    /** 前端访问或下载附件时使用的地址，后续再确定生成方式。 */
    private String downloadUrl;

    /** 上传时间。 */
    private LocalDateTime createdAt;
}
