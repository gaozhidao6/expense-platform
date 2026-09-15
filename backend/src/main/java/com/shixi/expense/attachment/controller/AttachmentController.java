package com.shixi.expense.attachment.controller;

import java.util.List;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shixi.expense.attachment.dto.AttachmentUploadDTO;
import com.shixi.expense.attachment.service.AttachmentService;
import com.shixi.expense.attachment.vo.AttachmentDownload;
import com.shixi.expense.attachment.vo.AttachmentVO;

import jakarta.validation.Valid;

/**
 * 报销附件接口。
 *
 * <p>Controller 只负责接收 multipart/form-data 参数并调用 Service，
 * 不在这里编写权限校验、文件保存或数据库操作。</p>
 */
@RestController
@RequestMapping("/api/reports/{reportId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * 上传单个附件。
     *
     * <p>表单字段名必须是 file，请求类型必须是 multipart/form-data。</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentVO upload(
            @PathVariable Long reportId,
            @Valid @ModelAttribute AttachmentUploadDTO request) {
        return attachmentService.upload(reportId, request.getFile());
    }

    /**
     * 查询一张报销单的附件列表。
     *
     * <p>Controller 只转交报销单 ID，数据权限和数据库查询由 Service 负责。</p>
     */
    @GetMapping
    public List<AttachmentVO> list(
            @PathVariable Long reportId) {
        return attachmentService.listByReportId(reportId);
    }

    /** 下载附件，文件名使用 UTF-8 编码以支持中文。 */
    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long reportId,
            @PathVariable Long attachmentId) {
        AttachmentDownload download =
                attachmentService.download(reportId, attachmentId);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .body(download.resource());
    }

    /** 删除草稿或已驳回报销单中的附件。 */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long reportId,
            @PathVariable Long attachmentId) {
        attachmentService.delete(reportId, attachmentId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
