package com.shixi.expense.attachment.vo;

import org.springframework.core.io.Resource;

/**
 * Service 返回给 Controller 的附件下载信息。
 *
 * <p>该对象不是数据库实体，也不是 JSON 响应；它把文件资源和下载响应头所需的
 * 元数据一起交给 Controller。</p>
 */
public record AttachmentDownload(
        Resource resource,
        String originalFilename,
        String contentType,
        long contentLength) {
}
