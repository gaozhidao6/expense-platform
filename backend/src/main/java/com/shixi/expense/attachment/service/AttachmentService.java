package com.shixi.expense.attachment.service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.attachment.config.AttachmentStorageProperties;
import com.shixi.expense.attachment.entity.Attachment;
import com.shixi.expense.attachment.mapper.AttachmentMapper;
import com.shixi.expense.attachment.storage.AttachmentStorage;
import com.shixi.expense.attachment.vo.AttachmentDownload;
import com.shixi.expense.attachment.vo.AttachmentVO;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import com.shixi.expense.common.security.CurrentUserService;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseReportMapper;



/**
 * 附件业务服务。
 *
 * <p>负责报销单归属校验、可编辑状态校验、文件存储和元数据持久化。</p>
 */
@Service
public class AttachmentService {

    /**
     * 文件扩展名与浏览器声明的 Content-Type 对应关系。
     *
     * <p>扩展名和 Content-Type 都来自客户端，因此这是基础校验；
     * 后续还可以读取文件特征字节，进一步判断文件的真实类型。</p>
     */
    private static final Map<String, Set<String>> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "pdf", Set.of("application/pdf"));

    /** 负责附件元数据的数据库访问。 */
    private final AttachmentMapper attachmentMapper;

    /** 负责查询报销单，后续用于校验归属人和可编辑状态。 */
    private final ExpenseReportMapper expenseReportMapper;

    /** 负责从可信登录态获取当前用户，不能使用前端传入的用户 ID。 */
    private final CurrentUserService currentUserService;

    /** 负责实际文件保存，让业务层不依赖具体存储方式。 */
    private final AttachmentStorage attachmentStorage;

    private final AttachmentStorageProperties attachmentProperties;

    public AttachmentService(
            AttachmentMapper attachmentMapper,
            ExpenseReportMapper expenseReportMapper,
            CurrentUserService currentUserService,
            AttachmentStorage attachmentStorage,
            AttachmentStorageProperties attachmentProperties) {
        this.attachmentMapper = attachmentMapper;
        this.expenseReportMapper = expenseReportMapper;
        this.currentUserService = currentUserService;
        this.attachmentStorage = attachmentStorage;
        this.attachmentProperties = attachmentProperties;
    }

    public String calculateSha256(MultipartFile file){
        try(InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("读取附件内容失败",e);
        }catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256",exception);
        }
    }
    /**
     * 上传一份报销附件。
     *
     * <p>文件保存和元数据写入共同组成上传流程；数据库写入失败时补偿删除文件。</p>
     *
     * @param reportId 报销单 ID，由 URL 路径取得
     * @param file 前端上传的临时文件
     * @return 保存后的附件展示信息
     */
    @Transactional
    public AttachmentVO upload(Long reportId, MultipartFile file) {
        // 检查报销单 ID 和上传文件是否有效。
        if (reportId == null || reportId <= 0) {
            throw new IllegalArgumentException("报销单 ID 不合法");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        // 当前用户必须从可信登录态取得，不能由前端指定。
        Long userId = currentUserService.requireCurrentUserId();

        // 查询报销单，并校验存在性、归属人和可编辑状态。
        ExpenseReport report = expenseReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,"报销单不存在");
        }
        if (!Objects.equals(report.getApplicantId(), userId)) {
            throw new AccessDeniedException("无权为该报销单上传附件");
        }

        if (!Objects.equals(report.getStatus(), "DRAFT") && !Objects.equals(report.getStatus(), "REJECTED")) {
            throw new BusinessException(ErrorCode.CONFLICT,"只有草稿或已驳回的报销单可以上传附件");
        }

        // 校验文件大小、扩展名和 Content-Type，不能只相信原始文件名。
        if (file.getSize() > attachmentProperties.getMaxFileSize()) {
            throw new IllegalArgumentException("附件大小不能超过 10 MB");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("附件文件名不能为空");
        }

        String extension = StringUtils.getFilenameExtension(originalFileName);
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("附件文件名后缀不能为空");
        }
        extension = extension.toLowerCase(Locale.ROOT);

        if (!attachmentProperties.getAllowedExtensions().contains(extension)) {
            throw new IllegalArgumentException("附件文件名后缀不合法");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("无法识别附件的 Content-Type");
        }

        // Content-Type 不区分大小写，统一转成小写后再比较。
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        Set<String> expectedContentTypes = CONTENT_TYPES_BY_EXTENSION.get(extension);

        // 不仅要求扩展名和 Content-Type 分别合法，还要求两者能够正确对应。
        // 例如 .pdf 必须对应 application/pdf，不能对应 image/png。
        if (expectedContentTypes == null || !expectedContentTypes.contains(normalizedContentType)) {
            throw new IllegalArgumentException(
                    "附件扩展名与 Content-Type 不匹配");
        }

        // 计算文件内容的 SHA-256，并通过 reportId + sha256 查询重复附件。
        String sha256 = calculateSha256(file);
        Attachment existAttachment =   attachmentMapper.selectOne(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getReportId, reportId)
                        .eq(Attachment::getSha256, sha256)
        );
        if (existAttachment != null) {
        throw new BusinessException(ErrorCode.CONFLICT,"该附件已经上传过");
        }

        // 生成安全且不会重复的 objectKey，禁止直接使用原始文件名作为磁盘路径。
        String dateDirectory = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        String objectKey = "reports/"+ reportId+ "/"+ dateDirectory+ "/"
                            +UUID.randomUUID().toString().replace("-","")
                            +"." + extension;
        // 调用存储接口保存文件，并取得用于持久化的相对路径。
        String storagePath;
        try(InputStream inputStream = file.getInputStream()){
            storagePath = attachmentStorage.save(inputStream, objectKey);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "附件保存失败",
                    exception
            );
        }   
        // 组装附件元数据实体。
        Attachment attachment = new Attachment();
        attachment.setReportId(reportId);
        attachment.setOriginalFilename(originalFileName);
        attachment.setStoragePath(storagePath);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(normalizedContentType);
        attachment.setSha256(sha256);
        attachment.setCreatedAt(LocalDateTime.now());

        // 数据库写入失败时删除刚保存的文件，补偿文件系统无法随事务回滚的问题。
        try {
            int rows = attachmentMapper.insert(attachment);

            if (rows != 1) {
                throw new IllegalStateException("附件元数据保存失败");
            }
        } catch (RuntimeException databaseException) {
            try {
                // 数据库保存失败，删除之前已经写入磁盘的文件。
                attachmentStorage.delete(storagePath);
            } catch (IOException | RuntimeException cleanupException) {
                /*
                * 删除失败不能覆盖原始数据库异常。
                * addSuppressed() 会把清理异常附加到原异常中，
                * 以后查看日志时仍然可以看到两个异常。
                */
                databaseException.addSuppressed(cleanupException);
            }

            // 重新抛出数据库异常，让 @Transactional 回滚数据库事务。
            throw databaseException;
        }

        // 将数据库实体转换成前端响应对象，不暴露 storagePath 和 sha256 等内部字段。
        return toVO(attachment);
    }

    /**
     * 查询指定报销单的附件列表。
     *
     * <p>当前先提供接口和核心步骤框架。查询时必须先校验报销单存在且属于当前用户，
     * 不能只根据 reportId 直接返回附件，否则可能泄露其他用户的附件信息。</p>
     */
    public List<AttachmentVO> listByReportId(Long reportId) {
        // 校验 reportId，并从登录态取得当前用户 ID。
        if (reportId == null || reportId <= 0) {
            throw new IllegalArgumentException(
                    "报销单 ID 不合法"
            );
        }
        Long userId = currentUserService.requireCurrentUserId();
        ExpenseReport report =
            expenseReportMapper.selectById(reportId);

        if (report == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "报销单不存在"
            );
        }

        if (!Objects.equals(
                report.getApplicantId(),
                userId)) {
            throw new AccessDeniedException(
                    "无权查看该报销单的附件"
            );
        }
        // 按 reportId 查询附件，并按 createdAt 倒序排列。
        List<Attachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                                                        .eq(Attachment::getReportId, reportId)
                                                        .orderByDesc(Attachment::getCreatedAt));
        // 查询附件不要求报销单处于可编辑状态。
        return attachments.stream().map(this::toVO).toList();
    }

    /**
     * 下载指定报销单中的附件。
     *
     * <p>Controller 只负责 HTTP 响应头；权限、附件归属和文件读取由这里完成。</p>
     */
    public AttachmentDownload download(Long reportId, Long attachmentId) {
        // 报销单 ID 和附件 ID 都必须是有效正数。
        if (reportId == null || reportId <= 0 || attachmentId == null || attachmentId <= 0) {
            throw new IllegalArgumentException("报销单 ID 或附件 ID 不合法");
        }
        // 获取当前用户并查询报销单，校验报销单存在且属于当前用户。
        Long userId = currentUserService.requireCurrentUserId();
        ExpenseReport report = expenseReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,"报销单不存在");
        }
        if (!Objects.equals(userId, report.getApplicantId())) {
            throw new AccessDeniedException("无权下载该报销单的附件");
        }
        // 查询附件，并校验附件确实属于路径中的报销单。
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !Objects.equals(reportId, attachment.getReportId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND,"附件不存在");
        }
        // 从存储层读取文件，并补齐下载响应需要的元数据。
        try{
            Resource resource = attachmentStorage.load(attachment.getStoragePath());
            String contentType = StringUtils.hasText(attachment.getContentType())? attachment.getContentType(): "application/octet-stream";
            return new AttachmentDownload(resource,attachment.getOriginalFilename(),contentType,resource.contentLength());
        }
        catch(IOException exception){
            throw new IllegalStateException("读取附件失败", exception);
        }
    }

    /**
     * 删除指定报销单中的附件。
     *
     * <p>只有报销单申请人可以删除，并且报销单必须处于 DRAFT 或 REJECTED。</p>
     */
    @Transactional
    public void delete(Long reportId, Long attachmentId) {
        // 报销单 ID 和附件 ID 都必须是有效正数。
        if (reportId == null || reportId <= 0 || attachmentId == null || attachmentId <= 0) {
            throw new IllegalArgumentException("报销单 ID 或附件 ID 不合法");
        }
        // 当前用户身份来自登录态；删除前必须校验报销单存在且属于本人。
        Long userId = currentUserService.requireCurrentUserId();
        ExpenseReport report = expenseReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,"报销单不存在");
        }
        if (!Objects.equals(userId, report.getApplicantId())) {
            throw new AccessDeniedException("无权删除该报销单的附件");
        }
        // 已进入审批流程的报销单不能再删除附件。
        if (!Objects.equals("DRAFT", report.getStatus()) && !Objects.equals("REJECTED", report.getStatus()) ) {
                    throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "只有草稿或已驳回的报销单可以删除附件"
            );
        }
        // 同时校验附件 ID 与报销单 ID，避免跨报销单删除附件。
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !Objects.equals(reportId, attachment.getReportId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND,"附件不存在");
        }
        // 先删除数据库元数据，并检查实际影响行数。
        int rows = attachmentMapper.deleteById(attachment);
        if (rows != 1) {
            throw new IllegalStateException(
            "附件元数据删除失败"
                );
        }
        try {
            attachmentStorage.delete(
                    attachment.getStoragePath()
            );
        } catch (IOException exception) {
            /*
            * 抛出运行时异常，让 @Transactional
            * 回滚前面的数据库删除操作。
            */
            throw new IllegalStateException(
                    "附件文件删除失败",
                    exception
            );
        }
        /*
         * 当前简单方案在同一方法内先删除数据库记录，再删除磁盘文件。
         * 磁盘删除失败时抛出运行时异常，使数据库事务回滚；但如果事务提交阶段
         * 极少数情况下失败，文件系统无法自动恢复，生产级方案需要异步补偿任务。
         */
    }

    /**
     * 将附件实体转换成前端响应对象。
     *
     * <p>storagePath 是服务器内部存储信息，sha256 是内部去重信息，
     * 当前都不返回给前端。downloadUrl 只包含受权限保护的业务下载地址。</p>
     */
    private AttachmentVO toVO(Attachment attachment) {
        if (attachment == null) {
            return null;
        }

        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setReportId(attachment.getReportId());
        vo.setOriginalFilename(attachment.getOriginalFilename());
        vo.setFileSize(attachment.getFileSize());
        vo.setContentType(attachment.getContentType());
        vo.setCreatedAt(attachment.getCreatedAt());

        // 返回业务下载地址，不暴露服务器上的真实文件路径。
        vo.setDownloadUrl(
                "/api/reports/"
                        + attachment.getReportId()
                        + "/attachments/"
                        + attachment.getId()
                        + "/download");
        return vo;
    }
}
