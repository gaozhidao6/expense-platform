package com.shixi.expense.invoice.service;

import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import com.shixi.expense.common.security.CurrentUserService;
import com.shixi.expense.attachment.entity.Attachment;
import com.shixi.expense.attachment.mapper.AttachmentMapper;
import com.shixi.expense.invoice.dto.ConfirmInvoiceSuggestionRequest;
import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import com.shixi.expense.invoice.entity.Invoice;
import com.shixi.expense.invoice.entity.InvoiceRecognitionSuggestion;
import com.shixi.expense.invoice.enums.InvoiceSuggestionStatus;
import com.shixi.expense.invoice.mapper.InvoiceMapper;
import com.shixi.expense.invoice.mapper.InvoiceRecognitionSuggestionMapper;
import com.shixi.expense.invoice.vo.InvoiceSuggestionVO;
import com.shixi.expense.report.entity.ExpenseItem;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseItemMapper;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import com.shixi.expense.report.service.ReportTotalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发票识别建议业务。
 *
 * <p>AI 结果先作为建议保存；只有用户确认后，才更新费用明细并生成正式发票。</p>
 */
@Service
public class InvoiceSuggestionService {

    private final InvoiceRecognitionSuggestionMapper suggestionMapper;
    private final ExpenseReportMapper reportMapper;
    private final ExpenseItemMapper itemMapper;
    private final AttachmentMapper attachmentMapper;
    private final InvoiceMapper invoiceMapper;
    private final ReportTotalService reportTotalService;
    private final CurrentUserService currentUserService;

    public InvoiceSuggestionService(
            InvoiceRecognitionSuggestionMapper suggestionMapper,
            ExpenseReportMapper reportMapper,
            ExpenseItemMapper itemMapper,
            AttachmentMapper attachmentMapper,
            InvoiceMapper invoiceMapper,
            ReportTotalService reportTotalService,
            CurrentUserService currentUserService) {
        this.suggestionMapper = suggestionMapper;
        this.reportMapper = reportMapper;
        this.itemMapper = itemMapper;
        this.attachmentMapper = attachmentMapper;
        this.invoiceMapper = invoiceMapper;
        this.reportTotalService = reportTotalService;
        this.currentUserService = currentUserService;
    }

    /**
     * 把一次合法的 AI 结果保存为待确认建议。
     *
     * <p>推荐先完成这个方法。每次调用都 insert 新记录，从而保留重复识别历史。</p>
     */
    @Transactional
    public InvoiceSuggestionVO createSuggestion(
            Long reportId,
            Long attachmentId,
            InvoiceRecognitionResult result) {
        // 保存建议的处理步骤。
        // 1. 检查 reportId、attachmentId 和 result 不为空。
        if (reportId == null || attachmentId == null || result == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "报销单 ID、附件 ID 和识别结果不能为空");
        }
        // 2. 检查 reportId 和 attachmentId 必须大于 0。
        if (reportId <= 0 || attachmentId <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "报销单 ID 和附件 ID 必须为正数");
        }
        // 3. 调用 currentUserService.requireCurrentUserId() 获取当前用户 ID。
        Long userId = currentUserService.requireCurrentUserId();
        // 4. 不要增加 createdBy 参数，否则前端可以冒充其他用户。
        // 5. 使用 reportMapper.selectById(reportId) 查询报销单。
        ExpenseReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报销单不存在");
        }
        // 6. 报销单不存在时抛出 NOT_FOUND 业务异常。
        // 7. 判断当前用户是不是这张报销单的申请人。
        if (!Objects.equals(report.getApplicantId(), userId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "只能为自己的报销单保存识别建议");
        }
        // 附件必须存在，并且必须属于路径中的报销单。
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        if (!Objects.equals(attachment.getReportId(), reportId)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "附件不属于当前报销单");
        }
        // 8. 创建 InvoiceRecognitionSuggestion 对象。
        InvoiceRecognitionSuggestion suggestion = new InvoiceRecognitionSuggestion();
        // 9. 填入 reportId、attachmentId 和 result 中的五个识别字段。
        suggestion.setReportId(reportId);
        suggestion.setAttachmentId(attachmentId);
        suggestion.setInvoiceNumber(result.invoiceNumber());
        suggestion.setInvoiceDate(result.invoiceDate());
        suggestion.setAmount(result.amount());
        suggestion.setMerchantName(result.merchantName());
        suggestion.setConfidence(result.confidence());
        // 10. 把当前用户 ID 填入 createdBy。
        suggestion.setCreatedBy(userId);
        // 11. 把 status 设置为 PENDING_CONFIRMATION.name()。
        suggestion.setStatus(
                InvoiceSuggestionStatus.PENDING_CONFIRMATION.name());
        // 12. 此时不要填写 confirmedBy、confirmedAt 或 rejectedAt。
        // 13. 调用 suggestionMapper.insert(suggestion) 保存建议。
        int rows = suggestionMapper.insert(suggestion);
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "保存发票识别建议失败");
        }
        // 14. 检查 insert 返回值是否等于 1，不等于 1 时抛出异常。
        // 15. insert 成功后，MyBatis-Plus 会自动回填 suggestion.id。
        // 16. 最后调用 toVO(suggestion) 并返回结果。
        // 数据库生成的时间字段不会自动回填，所以重新查询后再返回。
        return getPersistedSuggestion(suggestion.getId());
        // 注意：附件归属目前已由附件下载服务校验。
        // 注意：若以后增加其他调用入口，需要把附件归属校验抽成公共方法。
    }

    /** 查询一条建议，必须确保它属于当前用户自己的报销单。 */
    public InvoiceSuggestionVO getSuggestion(Long reportId, Long suggestionId) {
        // 查询建议的处理步骤。
        // 1. 校验 reportId 和 suggestionId 不为空且大于 0。
        if (reportId == null || reportId <= 0 ) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "报销单 ID 必须为正数");
        }
       
        if (suggestionId == null || suggestionId <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "识别建议 ID 必须为正数");
        }       
        // 2. 使用 suggestionMapper.selectById(suggestionId) 查询建议。
        InvoiceRecognitionSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        // 3. 建议不存在时抛出 NOT_FOUND 业务异常。
        if (suggestion == null ) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "发票识别建议不存在");
        }
        // 4. 检查 suggestion.reportId 必须等于路径中的 reportId。
        if (!Objects.equals(suggestion.getReportId(), reportId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "当前报销单中不存在该识别建议");
        }
        // 5. 查询报销单，并获取当前登录用户 ID。
        ExpenseReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报销单不存在");
        }
        Long userId = currentUserService.requireCurrentUserId();
        // 6. 检查当前用户必须是报销单申请人。
        if (!Objects.equals(report.getApplicantId(), userId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "无权查看该发票识别建议");
        }
        // 7. 调用 toVO(suggestion) 返回结果。
        return toVO(suggestion);
        // 注意：不能只根据 suggestionId 查询并直接返回。
        // 注意：否则知道 ID 的用户可能看到别人的发票信息，造成越权访问。
    }

    /**
     * 确认建议，并把用户最终核对后的值写入正式费用明细。
     *
     * <p>建议状态更新和费用明细更新必须处在同一个事务中。</p>
     */
    @Transactional
    public InvoiceSuggestionVO confirmSuggestion(
            Long reportId,
            Long suggestionId,
            ConfirmInvoiceSuggestionRequest request) {
        // 确认建议的处理步骤。
        // 1. 校验 reportId、suggestionId 和 request。
        if (reportId == null || reportId <= 0 ) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "报销单 ID 必须为正数");
        }
       
        if (suggestionId == null || suggestionId <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "识别建议 ID 必须为正数");
        }
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "确认数据不能为空");
        }
        // 2. 校验费用明细 ID，避免把 null 或非法 ID 交给 Mapper。
        if (request.expenseItemId() == null
                || request.expenseItemId() <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "费用明细 ID 必须为正数");
        }
        // 3. 发票号码去除首尾空格后不能为空。
        if (request.invoiceNumber() == null
                || request.invoiceNumber().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "发票号码不能为空");
        }
        // 4. 商户名称去除首尾空格后不能为空。
        if (request.merchantName() == null
                || request.merchantName().isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "商户名称不能为空");
        }
        // 5. 金额不能为空，并且必须严格大于 0。
        if (request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "发票金额必须大于 0");
        }
        // 6. 发票日期不能为空，并且不能晚于今天。
        if (request.invoiceDate() == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "发票日期不能为空");
        }
        if (request.invoiceDate().isAfter(LocalDate.now())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "发票日期不能晚于今天");
        }
        // 7. 获取当前登录用户 ID。
        Long userId = currentUserService.requireCurrentUserId();
        // 4. 查询建议，不存在时抛出 NOT_FOUND 业务异常。
        InvoiceRecognitionSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null ) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "发票识别建议不存在");
        }
        // 5. 检查建议必须属于路径中的 reportId。
        if (!Objects.equals(suggestion.getReportId(), reportId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "当前报销单中不存在该识别建议");
        }
        // 6. 查询报销单，并检查当前用户必须是申请人。
        ExpenseReport report = reportMapper.selectByIdForUpdate(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报销单不存在");
        }
        if (!Objects.equals(report.getApplicantId(), userId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "无权确认该发票识别建议");
        }
        // 7. 只允许 PENDING_CONFIRMATION 状态执行确认。
        if (!Objects.equals(
                suggestion.getStatus(),
                InvoiceSuggestionStatus.PENDING_CONFIRMATION.name())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "只有待确认状态的识别建议可以确认");
        }
        // 8. CONFIRMED 状态不能重复确认。
        // 9. REJECTED 状态不能再次确认。
        // 10. 检查报销单仍为 DRAFT。
        if (!Objects.equals(report.getStatus(), "DRAFT")) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "只有草稿状态的报销单可以写入识别结果");
        }
        // 11. 如果业务允许驳回后编辑，可以额外允许 REJECTED 报销单。

        // 12. 使用 itemMapper.selectById(request.expenseItemId()) 查询费用明细。
        ExpenseItem item =  itemMapper.selectById(request.expenseItemId());
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "费用明细不存在");
        }
        // 13. 检查费用明细的 reportId 必须等于当前 reportId。
        if (!Objects.equals(item.getReportId(), reportId)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "费用明细不属于当前报销单");
        }
        // 14. 使用 request 中的 invoiceDate 更新 ExpenseItem.expenseDate。
        item.setExpenseDate(request.invoiceDate());
        // 15. 使用 request 中的 merchantName 更新 ExpenseItem.merchant。
        item.setMerchant(request.merchantName().trim());
        // 16. 使用 request 中的 amount 更新 ExpenseItem.amount。
        item.setAmount(request.amount());
        // 17. 不要使用旧 AI 结果，因为用户可能已经修改识别值。
        // 18. 把建议中的发票号、日期、商户和金额更新为用户确认值。
        suggestion.setInvoiceNumber(request.invoiceNumber().trim());
        suggestion.setInvoiceDate(request.invoiceDate());
        suggestion.setAmount(request.amount());
        suggestion.setMerchantName(request.merchantName().trim());
        // 19. 把 request.expenseItemId() 填入建议的 expenseItemId。
        suggestion.setExpenseItemId(request.expenseItemId());
        // 20. 把建议状态改为 CONFIRMED.name()。
        suggestion.setStatus(InvoiceSuggestionStatus.CONFIRMED.name());
        // 21. 填写 confirmedBy 和 confirmedAt。
        suggestion.setConfirmedBy(userId);
        suggestion.setConfirmedAt(LocalDateTime.now());
        // 22. 调用 itemMapper.updateById(item) 更新费用明细。
        int itemRows = itemMapper.updateById(item);
        if (itemRows != 1) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "更新费用明细失败");
        }
        // 明细金额改变后，必须同步重算报销单总金额。
        reportTotalService.recalculateAndSave(report);

        // 将用户确认后的内容写入正式 invoice 表。
        Attachment attachment = attachmentMapper.selectById(
                suggestion.getAttachmentId());
        if (attachment == null
                || !Objects.equals(attachment.getReportId(), reportId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "识别建议关联的附件不存在");
        }
        Invoice invoice = new Invoice();
        invoice.setExpenseItemId(item.getId());
        invoice.setInvoiceNumber(request.invoiceNumber().trim());
        invoice.setSellerName(request.merchantName().trim());
        invoice.setIssuedDate(request.invoiceDate());
        invoice.setAmount(request.amount());
        invoice.setFileUrl(attachment.getStoragePath());
        invoice.setFileHash(attachment.getSha256());
        invoice.setRecognitionStatus(
                InvoiceSuggestionStatus.CONFIRMED.name());
        invoice.setConfirmedBy(userId);
        invoice.setConfirmedAt(suggestion.getConfirmedAt());
        if (invoiceMapper.insert(invoice) != 1) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "保存正式发票失败");
        }
        // 23. 调用 suggestionMapper.updateById(suggestion) 更新建议。
        int sugRows = suggestionMapper.updateById(suggestion);
        if (sugRows != 1) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "识别建议已被其他请求处理，请刷新后重试");
        }
        // 24. 任一步更新结果不是 1，都要抛出运行时异常。
        // 25. 运行时异常会让 @Transactional 回滚两次数据库更新。
        // 26. 最后调用 toVO(suggestion) 返回结果。
        return getPersistedSuggestion(suggestion.getId());
        // 易错点：不能在事务外分别更新建议和费用明细。
        // 易错点：否则可能出现“建议已确认，但正式明细没有更新”的矛盾状态。
        
    }

    /** 拒绝建议；拒绝只改变建议状态，不修改正式费用明细。 */
    @Transactional
    public InvoiceSuggestionVO rejectSuggestion(
            Long reportId,
            Long suggestionId) {
        // 拒绝建议的处理步骤。
        // 1. 校验 reportId 和 suggestionId。
        if (reportId == null || reportId <= 0 ) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "报销单 ID 必须为正数");
        }
       
        if (suggestionId == null || suggestionId <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "识别建议 ID 必须为正数");
        }
        // 2. 查询建议并检查它属于当前报销单。
        // 3. 查询报销单并检查当前用户是申请人。
        // 4. 只允许 PENDING_CONFIRMATION 状态执行拒绝。
        Long userId = currentUserService.requireCurrentUserId();
        InvoiceRecognitionSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null ) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "发票识别建议不存在");
        }
        
        if (!Objects.equals(suggestion.getReportId(), reportId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "当前报销单中不存在该识别建议");
        }
        
        ExpenseReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报销单不存在");
        }
        if (!Objects.equals(report.getApplicantId(), userId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "无权拒绝该发票识别建议");
        }
        if (!Objects.equals(
                suggestion.getStatus(),
                InvoiceSuggestionStatus.PENDING_CONFIRMATION.name())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "只有待确认状态的识别建议可以拒绝");
        }
        // 5. 把状态设置为 REJECTED.name()。
        suggestion.setStatus(InvoiceSuggestionStatus.REJECTED.name());
        // 6. 设置 rejectedAt = LocalDateTime.now()。
        suggestion.setRejectedAt(LocalDateTime.now());
        // 7. 调用 suggestionMapper.updateById(suggestion)。
        int rows = suggestionMapper.updateById(suggestion);
        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "识别建议已被其他请求处理，请刷新后重试");
        }
        // 8. 更新结果不是 1 时抛出运行时异常。
        // 9. 最后调用 toVO(suggestion) 返回结果。
        return getPersistedSuggestion(suggestion.getId());
    }

    /** 根据主键重新读取，确保返回数据库生成的时间和最新乐观锁版本。 */
    private InvoiceSuggestionVO getPersistedSuggestion(Long suggestionId) {
        InvoiceRecognitionSuggestion persisted =
                suggestionMapper.selectById(suggestionId);
        if (persisted == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "读取已保存的发票识别建议失败");
        }
        return toVO(persisted);
    }

    /**
     * Entity 转 VO 属于机械映射，已经替你完成。
     *
     * <p>不要直接把 Entity 返回给前端，以免未来给表增加内部字段时意外泄露。</p>
     */
    private InvoiceSuggestionVO toVO(
            InvoiceRecognitionSuggestion suggestion) {
        return new InvoiceSuggestionVO(
                suggestion.getId(),
                suggestion.getReportId(),
                suggestion.getExpenseItemId(),
                suggestion.getAttachmentId(),
                suggestion.getInvoiceNumber(),
                suggestion.getInvoiceDate(),
                suggestion.getAmount(),
                suggestion.getMerchantName(),
                suggestion.getConfidence(),
                suggestion.getStatus(),
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt());
    }
}
