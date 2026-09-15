package com.shixi.expense.attachment.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 附件上传请求 DTO。
 *
 * <p>报销单 ID 由 URL 路径提供，接收人和归属关系由后端登录态及业务数据判断。</p>
 */
@Data
public class AttachmentUploadDTO {

    /** 待上传的文件。 */
    @NotNull(message = "请选择要上传的附件")
    private MultipartFile file;
}
