package com.shixi.expense.report.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.approval.entity.ApprovalRecord;
import com.shixi.expense.approval.entity.ApprovalTask;
import com.shixi.expense.approval.mapper.ApprovalRecordMapper;
import com.shixi.expense.approval.mapper.ApprovalTaskMapper;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import com.shixi.expense.common.idempotency.IdempotencyRequest;
import com.shixi.expense.common.idempotency.IdempotentOperationExecutor;
import com.shixi.expense.department.entity.Department;
import com.shixi.expense.department.mapper.DepartmentMapper;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.enums.NotificationType;
import com.shixi.expense.notification.service.NotificationService;
import com.shixi.expense.report.dto.ExpenseItemDTO;
import com.shixi.expense.report.dto.ExpenseReportCreateDTO;
import com.shixi.expense.report.dto.ExpenseReportUpdateDTO;
import com.shixi.expense.report.dto.ExpenseReportPageQueryDTO;
import com.shixi.expense.report.dto.ExpenseReportStatisticsQueryDTO;
import com.shixi.expense.report.dto.ExpenseReportExportQueryDTO;
import com.shixi.expense.report.enums.ExpenseReportStatus;
import com.shixi.expense.report.entity.ExpenseItem;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseItemMapper;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import com.shixi.expense.report.vo.ExpenseItemVO;
import com.shixi.expense.report.vo.ExpenseReportExportRowVO;
import com.shixi.expense.report.vo.ExpenseReportVO;
import com.shixi.expense.report.vo.ExpenseReportListVO;
import com.shixi.expense.report.vo.ExpenseReportStatisticsVO;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;
import com.shixi.expense.operation.service.OperationLogWriteService;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * 报销单业务服务。
 *
 * <p>负责报销单、费用明细和基础状态操作的业务编排。</p>
 */
@Service
@Slf4j
public class ExpenseReportService {

    // 状态值集中定义在 ExpenseReportStatus 中；状态变化时必须追加 ApprovalRecord 历史。

    // 报销单、明细、用户和审批记录分别由对应 Mapper 负责数据库访问。
    private final ExpenseReportMapper expenseReportMapper;
    private final ExpenseItemMapper expenseItemMapper;
    private final SysUserMapper sysUserMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final DepartmentMapper departmentMapper;
    private final ExpenseReportCsvExporter csvExporter;
   
    @Autowired(required = false)
    private OperationLogWriteService operationLogWriteService;

    private final NotificationService notificationService;
    private final IdempotentOperationExecutor submissionExecutor;
    public ExpenseReportService(
        ExpenseReportMapper expenseReportMapper,
        ExpenseItemMapper expenseItemMapper,
        SysUserMapper sysUserMapper,
        ApprovalRecordMapper approvalRecordMapper,
        ApprovalTaskMapper approvalTaskMapper,
        DepartmentMapper departmentMapper,
        ExpenseReportCsvExporter csvExporter,
        IdempotentOperationExecutor submissionExecutor,
        NotificationService notificationService) {

    this.expenseReportMapper = expenseReportMapper;
    this.expenseItemMapper = expenseItemMapper;
    this.sysUserMapper = sysUserMapper;
    this.approvalRecordMapper = approvalRecordMapper;
    this.approvalTaskMapper = approvalTaskMapper;
    this.departmentMapper = departmentMapper;
    this.csvExporter = csvExporter;
    this.submissionExecutor = submissionExecutor;
    this.notificationService = notificationService;
}

    // 创建主表、明细并计算总额，必须在同一个事务中完成。
    @Transactional
    public ExpenseReportVO createReport(ExpenseReportCreateDTO request){
        // 从 SecurityContext 获取当前登录用户，不信任请求中的申请人 ID。
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .eq(SysUser::getStatus, 1)
        );

        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        // 生成业务编号，初始化草稿状态和审批轮次，然后保存主表。
        ExpenseReport newReport = new ExpenseReport();
        newReport.setReportNo(generateReportNo());
        newReport.setTitle(request.getTitle());
        newReport.setDescription(request.getDescription());
        newReport.setCurrency(request.getCurrency());
        newReport.setApplicantId(user.getId());
        newReport.setDepartmentId(user.getDepartmentId());
        newReport.setStatus("DRAFT");
        newReport.setApprovalRound(1);
        expenseReportMapper.insert(newReport);
        // 将每条请求明细转换成实体，并绑定刚生成的报销单 ID。
        // 使用 BigDecimal 汇总明细金额，不能信任前端传入的总金额。
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<ExpenseItemDTO> itemDTOList = request.getItems();
        if (itemDTOList != null) {
            for (ExpenseItemDTO itemDTO : itemDTOList) {
                ExpenseItem item = new ExpenseItem();
                item.setReportId(newReport.getId());
                item.setExpenseDate(itemDTO.getExpenseDate());
                item.setExpenseType(itemDTO.getExpenseType());
                item.setMerchant(itemDTO.getMerchant());
                item.setDescription(itemDTO.getDescription());
                item.setAmount(itemDTO.getAmount());
                // DTO 没传税额时，主动设置为 0
                item.setTaxAmount(
                        itemDTO.getTaxAmount() == null
                                ? BigDecimal.ZERO
                                : itemDTO.getTaxAmount()
                );
                // DTO 没传排序值时，主动设置为 0
                item.setSortOrder(
                        itemDTO.getSortOrder() == null
                                ? 0
                                : itemDTO.getSortOrder()
                );
                expenseItemMapper.insert(item);
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        newReport.setTotalAmount(totalAmount);
        expenseReportMapper.updateById(newReport);
        return toVO(newReport);
    }
    /**
     * 查询报销单详情。
     * 申请人可查看本人报销单；管理员可查看全部；经理和财务只能查看分配给自己的审批任务关联报销单。
     */
    public ExpenseReportVO getDetail(Long reportId) {
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1)
        );
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }

        ExpenseReport report = expenseReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报销单不存在");
        }
        if (!canViewDetail(user, report)) {
            throw new AccessDeniedException("无权查看该报销单");
        }

        return toVO(report);
    }

    /** 详情数据权限必须与报销单列表的数据范围保持一致。 */
    private boolean canViewDetail(SysUser user, ExpenseReport report) {
        if (Objects.equals(user.getId(), report.getApplicantId()) || hasRole("ADMIN")) {
            return true;
        }
        if (!hasRole("MANAGER") && !hasRole("FINANCE")) {
            return false;
        }
        Long taskCount = approvalTaskMapper.selectCount(
                new LambdaQueryWrapper<ApprovalTask>()
                        .eq(ApprovalTask::getReportId, report.getId())
                        .eq(ApprovalTask::getApproverId, user.getId()));
        return taskCount != null && taskCount > 0;
    }

    private boolean hasRole(String roleCode) {
        String requiredAuthority = "ROLE_" + roleCode;

        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(authority ->
                        requiredAuthority.equals(authority.getAuthority()));
    }

    /**
     * 获取统计查询使用的数据权限角色。
     * 多角色用户按照 ADMIN、MANAGER、FINANCE、EMPLOYEE 的顺序确定范围。
     */
    private String getCurrentRoleCode() {
        if (hasRole("ADMIN")) {
            return "ADMIN";
        }
        if (hasRole("MANAGER")) {
            return "MANAGER";
        }
        if (hasRole("FINANCE")) {
            return "FINANCE";
        }
        return "EMPLOYEE";
    }
    /**
     * 分页查询当前用户的报销单列表。
     * 列表只返回摘要信息，费用明细通过详情接口单独查询。
     */
    public IPage<ExpenseReportListVO> queryPage(ExpenseReportPageQueryDTO query) {
        String username =SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).eq(SysUser::getStatus, 1));
        // 获取当前登录用户，并确定数据权限范围。
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        Page<ExpenseReport> page = new Page<>(query.getPage(),query.getSize());

        LambdaQueryWrapper<ExpenseReport> wrapper = new LambdaQueryWrapper<ExpenseReport>();
        // 添加关键字、状态等筛选条件。
        if (hasRole("ADMIN")) {
            // 查询全部报销单
        } else if (hasRole("MANAGER")) {
            // 查询经理审批任务相关报销单
            wrapper.inSql(
                    ExpenseReport::getId,
                    "SELECT report_id FROM approval_task " +
                    "WHERE approver_id = " + user.getId()
            );
        } else if (hasRole("FINANCE")) {
            // 查询财务任务相关报销单
             wrapper.inSql(
                    ExpenseReport::getId,
                    "SELECT report_id FROM approval_task " +
                    "WHERE approver_id = " + user.getId()
            );
        } else {
            // 只能查询本人报销单
            wrapper.eq(
                    ExpenseReport::getApplicantId,
                    user.getId()
            );
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(condition -> condition
                    .like(ExpenseReport::getReportNo, keyword)
                    .or()
                    .like(ExpenseReport::getTitle, keyword)
            );
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ExpenseReport::getStatus, query.getStatus().trim());
        }
        // 校验时间范围是否合法
        if (query.getStartTime() != null
                && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {

            throw new IllegalArgumentException(
                    "开始时间不能晚于结束时间"
            );
        }

        // 按报销单创建时间筛选
        if (query.getStartTime() != null) {
            wrapper.ge(
                    ExpenseReport::getCreatedAt,
                    query.getStartTime()
            );
        }

        if (query.getEndTime() != null) {
            wrapper.le(
                    ExpenseReport::getCreatedAt,
                    query.getEndTime()
            );
        }
        wrapper.orderByDesc(ExpenseReport::getCreatedAt);

        return expenseReportMapper.selectPage(page, wrapper).convert(this::toListVO);
    }

    /**
     * 查询报销单统计数据。
     */
    public ExpenseReportStatisticsVO getStatistics(
            ExpenseReportStatisticsQueryDTO query) {
        if (query == null) {
            query = new ExpenseReportStatisticsQueryDTO();
        }        
        // 获取当前登录用户并确定角色数据范围。
        String username =SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).eq(SysUser::getStatus, 1));
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        // 校验 startTime 不晚于 endTime。
        if (query.getStartTime() != null
                && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始时间不能晚于结束时间");
        }
        String roleCode = getCurrentRoleCode();
        // 组装 ExpenseReportStatisticsVO 并返回。
        return expenseReportMapper.selectStatistics(user.getId(), roleCode, query.getStartTime(), query.getEndTime());
    }

    /**
     * 导出报销单数据入口。
     */
    public byte[] exportReports(ExpenseReportExportQueryDTO query) {
        // 查询参数为空时，使用默认对象
        if (query == null) {
            query = new ExpenseReportExportQueryDTO();
        }
        // 校验导出时间范围。
         if (query.getStartTime() != null
                && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "开始时间不能晚于结束时间");
        }
        // 导出查询使用与分页查询一致的角色数据权限规则。
        String username =SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).eq(SysUser::getStatus, 1));
        if (user == null) {
            throw new IllegalStateException(
                    "当前登录用户不存在");
        }
          // 确定角色数据权限
        String roleCode = getCurrentRoleCode();

        // 查询符合条件的报销单
        List<ExpenseReport> reports =
                expenseReportMapper.selectForExport(
                        user.getId(),
                        roleCode,
                        query.getKeyword(),
                        query.getStatus(),
                        query.getStartTime(),
                        query.getEndTime()
                );

        // 转换成 CSV 导出行
        List<ExpenseReportExportRowVO> rows =
                reports.stream()
                        .map(this::toExportRow)
                        .toList();

        // 交给 CSV 生成器
        return csvExporter.export(rows);
    }

    /** 将报销单实体转换为 CSV 导出行对象。 */
    private ExpenseReportExportRowVO toExportRow(ExpenseReport report) {
        ExpenseReportExportRowVO row = new ExpenseReportExportRowVO();
        row.setReportNo(report.getReportNo());
        row.setTitle(report.getTitle());
        row.setApplicantId(report.getApplicantId());
        row.setDepartmentId(report.getDepartmentId());
        row.setTotalAmount(report.getTotalAmount());
        row.setCurrency(report.getCurrency());
        row.setStatus(report.getStatus());
        row.setApprovalRound(report.getApprovalRound());
        row.setCreatedAt(report.getCreatedAt());
        row.setCompletedAt(report.getCompletedAt());
        return row;
    }


    /** 将报销单实体转换成列表摘要 VO。 */
    private ExpenseReportListVO toListVO(ExpenseReport report) {
        ExpenseReportListVO vo = new ExpenseReportListVO();
        vo.setId(report.getId());
        vo.setReportNo(report.getReportNo());
        vo.setApplicantId(report.getApplicantId());
        vo.setDepartmentId(report.getDepartmentId());
        vo.setTitle(report.getTitle());
        vo.setTotalAmount(report.getTotalAmount());
        vo.setCurrency(report.getCurrency());
        vo.setStatus(report.getStatus());
        vo.setApprovalRound(report.getApprovalRound());
        vo.setSubmittedAt(report.getSubmittedAt());
        vo.setCompletedAt(report.getCompletedAt());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());
        return vo;
    }

    /** 将报销单及其有效明细转换成接口返回对象。 */
    private ExpenseReportVO toVO(ExpenseReport report) {
        List<ExpenseItem> savedItems = expenseItemMapper.selectList(
                new LambdaQueryWrapper<ExpenseItem>()
                        .eq(ExpenseItem::getReportId, report.getId())
                        .orderByAsc(ExpenseItem::getSortOrder)
                        .orderByAsc(ExpenseItem::getId)
        );

        List<ExpenseItemVO> itemVOList = savedItems.stream()
                .map(item -> {
                    ExpenseItemVO itemVO = new ExpenseItemVO();
                    itemVO.setId(item.getId());
                    itemVO.setExpenseDate(item.getExpenseDate());
                    itemVO.setExpenseType(item.getExpenseType());
                    itemVO.setMerchant(item.getMerchant());
                    itemVO.setDescription(item.getDescription());
                    itemVO.setAmount(item.getAmount());
                    itemVO.setTaxAmount(item.getTaxAmount());
                    itemVO.setSortOrder(item.getSortOrder());
                    itemVO.setCreatedAt(item.getCreatedAt());
                    itemVO.setUpdatedAt(item.getUpdatedAt());
                    return itemVO;
                })
                .toList();

        ExpenseReportVO reportVO = new ExpenseReportVO();
        reportVO.setId(report.getId());
        reportVO.setReportNo(report.getReportNo());
        reportVO.setApplicantId(report.getApplicantId());
        reportVO.setDepartmentId(report.getDepartmentId());
        reportVO.setTitle(report.getTitle());
        reportVO.setDescription(report.getDescription());
        reportVO.setTotalAmount(report.getTotalAmount());
        reportVO.setCurrency(report.getCurrency());
        reportVO.setStatus(report.getStatus());
        reportVO.setApprovalRound(report.getApprovalRound());
        reportVO.setVersion(report.getVersion());
        reportVO.setSubmittedAt(report.getSubmittedAt());
        reportVO.setCompletedAt(report.getCompletedAt());
        reportVO.setCreatedAt(report.getCreatedAt());
        reportVO.setUpdatedAt(report.getUpdatedAt());
        reportVO.setItems(itemVOList);
        return reportVO;
    }

    /**
     * 生成报销单业务编号。
     *
     * <p>格式为 ER + 年月日时分秒毫秒 + 8 位随机字符，
     * 时间便于识别创建顺序，随机字符用于降低并发创建时编号重复的概率。</p>
     */
    private String generateReportNo() {
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        return "ER" + time + random;
    }

    /** 查询当前用户允许编辑的报销单：仅 DRAFT 或 REJECTED 状态可编辑。 */
    private ExpenseReport getEditableReport(Long reportId) {
        // 查询当前用户
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .eq(SysUser::getStatus, 1)
        );
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        // 查询报销单
        ExpenseReport report = expenseReportMapper.selectById(reportId);
        // 判断报销单存在
        if (report == null) {
            throw new IllegalStateException("报销单不存在");
        }
        // 判断 applicantId 是当前用户
        if (!Objects.equals(user.getId(), report.getApplicantId())) {
            throw new AccessDeniedException("无权操作该报销单");
        }
        // 判断 status 等于 DRAFT
        if (!"DRAFT".equals(report.getStatus())
                && !"REJECTED".equals(report.getStatus())) {
            throw new IllegalStateException("只有草稿状态可以修改明细");
        }
        // 返回通过校验的本人草稿报销单。
            return report;
    }

    /** 查询本人且允许提交的报销单。 */
    private ExpenseReport getOwnedDraft2(Long reportId) {
        // 查询当前用户
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .eq(SysUser::getStatus, 1)
        );
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        // 查询报销单
        ExpenseReport report = expenseReportMapper.selectById(reportId);
        // 判断报销单存在
        if (report == null) {
            throw new IllegalStateException("报销单不存在");
        }
        // 判断 applicantId 是当前用户
        if (!Objects.equals(user.getId(), report.getApplicantId())) {
            throw new AccessDeniedException("无权操作该报销单");
        }
        // 提交只允许从 DRAFT 或 REJECTED 开始。
        if (!"DRAFT".equals(report.getStatus()) && !"REJECTED".equals(report.getStatus())) {
            throw new IllegalStateException("只有草稿状态或者被驳回状态可以修改明细");
        }
            // 返回通过归属和状态校验的报销单。
            return report;
    }
    private ExpenseReport getOwnedDraft3(Long reportId) {
        // 查询当前用户
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .eq(SysUser::getStatus, 1)
        );
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        // 查询报销单
        ExpenseReport report = expenseReportMapper.selectById(reportId);
        // 判断报销单存在
        if (report == null) {
            throw new IllegalStateException("报销单不存在");
        }
        // 判断 applicantId 是当前用户
        if (!Objects.equals(user.getId(), report.getApplicantId())) {
            throw new AccessDeniedException("无权操作该报销单");
        }
        // 撤回只允许当前处于 APPROVING 状态。
        if (!"APPROVING".equals(report.getStatus())) {
            throw new IllegalStateException("只有审批中的报销单可以撤回");
        }
            // 返回通过归属和状态校验的报销单。
            return report;
    }

    /** 查询有效明细并重新计算报销单总金额。 */
    private void recalculateTotal(ExpenseReport report) {
        List<ExpenseItem> items = expenseItemMapper.selectList(
                new LambdaQueryWrapper<ExpenseItem>()
                        .eq(ExpenseItem::getReportId, report.getId())
        );

        BigDecimal totalAmount = items.stream()
                .map(ExpenseItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.setTotalAmount(totalAmount);
        expenseReportMapper.updateById(report);
    }

    /** 修改当前用户自己的草稿报销单基本信息。 */
    @Transactional
    public ExpenseReportVO updateReport(
            Long reportId,
            ExpenseReportUpdateDTO request) {
        // 只允许本人修改 DRAFT 草稿。
        ExpenseReport report = getEditableReport(reportId);

        // 这里只修改标题和说明，金额、明细、申请人和状态由其他业务控制。
        report.setTitle(request.getTitle());
        report.setDescription(request.getDescription());

        int updatedRows = expenseReportMapper.updateById(report);
        if (updatedRows != 1) {
            throw new IllegalStateException("报销单修改失败");
        }

        return toVO(report);
    }

    /**
     * 根据申请人的部门确定经理审批人。
     */
    private Long findManagerId(Long departmentId) {
        if (departmentId == null) {
            throw new IllegalStateException(
                "报销单没有所属部门");
        }
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new IllegalStateException(
                    "所属部门不存在");
        }

        if (department.getStatus() == null
                || department.getStatus() != 1) {
            throw new IllegalStateException(
                    "所属部门已停用");
        }
        //如果部门没有负责人，再查询启用状态的 MANAGER 用户。
        Long managerId = department.getManagerUserId();
        if (managerId == null) {
            throw new IllegalStateException(
                    "该部门未配置部门经理");
        }
        SysUser manager = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                                        .eq(SysUser::getId, managerId).eq(SysUser::getStatus, 1));

        // 找不到经理时抛出异常。
        if (manager == null) {
                throw new IllegalStateException(
                        "部门经理不存在或已停用");
            }
            return managerId;
    }
    @Transactional
    public ExpenseReportVO addItem(Long reportId, ExpenseItemDTO request){
        ExpenseReport report = getEditableReport(reportId);
        ExpenseItem item = new ExpenseItem();
                item.setReportId(report.getId());
                item.setExpenseDate(request.getExpenseDate());
                item.setExpenseType(request.getExpenseType());
                item.setMerchant(request.getMerchant());
                item.setDescription(request.getDescription());
                item.setAmount(request.getAmount());
                // DTO 没传税额时，主动设置为 0
                item.setTaxAmount(
                        request.getTaxAmount() == null
                                ? BigDecimal.ZERO
                                : request.getTaxAmount()
                );
                // DTO 没传排序值时，主动设置为 0
                item.setSortOrder(
                        request.getSortOrder() == null
                                ? 0
                                : request.getSortOrder()
                );
                expenseItemMapper.insert(item);
        recalculateTotal(report);
        return toVO(report);
    }
    
    @Transactional
    public ExpenseReportVO updateItem(Long reportId,Long itemId,ExpenseItemDTO request){
        ExpenseReport report = getEditableReport(reportId);
        ExpenseItem item = expenseItemMapper.selectOne(new LambdaQueryWrapper<ExpenseItem>()
                                                    .eq(ExpenseItem::getId, itemId)
                                                    .eq(ExpenseItem::getReportId, report.getId()));
                if (item == null) {
                    throw new IllegalStateException("费用明细不存在");
                }
                item.setExpenseDate(request.getExpenseDate());
                item.setExpenseType(request.getExpenseType());
                item.setMerchant(request.getMerchant());
                item.setDescription(request.getDescription());
                item.setAmount(request.getAmount());
                // DTO 没传税额时，主动设置为 0
                item.setTaxAmount(
                        request.getTaxAmount() == null
                                ? BigDecimal.ZERO
                                : request.getTaxAmount()
                );
                // DTO 没传排序值时，主动设置为 0
                item.setSortOrder(
                        request.getSortOrder() == null
                                ? 0
                                : request.getSortOrder()
                );
                expenseItemMapper.updateById(item);
        recalculateTotal(report);
        return toVO(report);
    }

    @Transactional
    public ExpenseReportVO deleteItem(Long reportId,Long itemId){
        ExpenseReport report = getEditableReport(reportId);
        ExpenseItem item = expenseItemMapper.selectOne(new LambdaQueryWrapper<ExpenseItem>()
                                                    .eq(ExpenseItem::getId, itemId)
                                                    .eq(ExpenseItem::getReportId, report.getId()));
        if (item == null) {
            throw new IllegalStateException("费用明细不存在");
        }

        expenseItemMapper.deleteById(item.getId());
        recalculateTotal(report);
        return toVO(report);
    }

    /** 提交本人报销单：DRAFT/REJECTED -> APPROVING。 */
    public ExpenseReportVO submit(Long reportId, String idempotencyKey) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).eq(SysUser::getStatus, 1));
        if (user == null) throw new IllegalStateException("当前登录用户不存在");
        IdempotencyRequest request = new IdempotencyRequest(idempotencyKey, user.getId(), "SUBMIT_REPORT", reportId);
        try {
            return submissionExecutor.execute(request, () -> {
                ExpenseReportVO result = submitInternal(reportId);
                recordSubmitSuccess(reportId);
                return result;
            }, ExpenseReportVO.class);
        } catch (RuntimeException exception) {
            recordSubmitFailure(reportId, "报销单提交失败");
            throw exception;
        }
    }

    private ExpenseReportVO submitInternal(Long reportId) {
        // 成功和异常路径分别由外层 submit() 写入审计日志。
        log.info("提交报销单，reportId={}", reportId);
        // 查询本人报销单，并允许 DRAFT 或 REJECTED 两种提交前状态。
        ExpenseReport report = getOwnedDraft2(reportId);

        // 保存操作前状态，审批记录中的 fromStatus 不能写死。
        String fromStatus = report.getStatus();

        // 查询当前报销单的有效明细；@TableLogic 会自动排除 deleted=1 的数据。
        List<ExpenseItem> items = expenseItemMapper.selectList(
                new LambdaQueryWrapper<ExpenseItem>()
                        .eq(ExpenseItem::getReportId, report.getId())
        );

        // 没有任何有效明细时，不允许进入审批流程。
        if (items.isEmpty()) {
            throw new IllegalStateException("至少需要一条费用明细才能提交");
        }

        // 查询当前登录用户，用于记录审批操作人。
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1)
        );
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }
        // 驳回后重新提交属于新一轮审批；首次提交保持第 1 轮。
        Integer currentRound = report.getApprovalRound();
        if (currentRound == null) {
            currentRound = 1;
        }
        if (ExpenseReportStatus.REJECTED.name().equals(fromStatus)) {
            currentRound++;
        }
        report.setApprovalRound(currentRound);

        // 根据报销单所属部门确定经理，不能把 departmentId 直接当作用户 ID。
        Long managerId = findManagerId(report.getDepartmentId());

        // 使用递增后的审批轮次创建本轮经理待审批任务。
        ApprovalTask task = new ApprovalTask();
        task.setReportId(report.getId());
        task.setApprovalRound(report.getApprovalRound());
        task.setNodeCode("MANAGER_APPROVAL");
        task.setNodeName("经理审批");
        task.setSequenceNo(1);
        task.setApproverId(managerId);
        task.setStatus("PENDING");

        int rows = approvalTaskMapper.insert(task);
        if (rows != 1) {
            throw new IllegalStateException("创建经理审批任务失败");
        }

        // 由后端决定状态，客户端不能直接传入 APPROVING 或其他状态。
        report.setStatus(ExpenseReportStatus.APPROVING.name());
        // 主表同步记录当前经理和节点，后续审批会用这两个字段防止越权操作。
        report.setCurrentApproverId(managerId);
        report.setCurrentNodeCode("MANAGER_APPROVAL");
        report.setSubmittedAt(LocalDateTime.now());

        // 状态变更和审批记录必须在同一个事务中完成。
        ApprovalRecord record = new ApprovalRecord();
        record.setReportId(report.getId());
        record.setApprovalRound(report.getApprovalRound());
        record.setAction("SUBMIT");
        record.setFromStatus(fromStatus);
        record.setToStatus(ExpenseReportStatus.APPROVING.name());
        record.setOperatorId(user.getId());
        record.setComment(null);

        // 先更新报销单当前状态，再追加不可变审批历史。
        int updatedRows = expenseReportMapper.updateById(report);
        if (updatedRows != 1) {
            throw new IllegalStateException("报销单提交失败，可能已被其他请求修改");
        }
        approvalRecordMapper.insert(record);
        NotificationCreateCommand command = new NotificationCreateCommand(
                managerId,
                NotificationType.APPROVAL_TODO,
                "新的报销审批任务",
                "您有一张新的报销单需要审批",
                "EXPENSE_REPORT",
                report.getId(),
                "SUBMIT_REPORT:"
                        + report.getId()
                        + ":"
                        + report.getApprovalRound());
        notificationService.create(command);
        // 返回包含最新状态和有效明细的详情。
        return toVO(report);
    }

    private void recordSubmitSuccess(Long reportId) {
        if (operationLogWriteService == null) return;
        try {
            operationLogWriteService.recordSuccess("SUBMIT_REPORT", "EXPENSE_REPORT", reportId);
        } catch (RuntimeException ignored) { }
    }

    private void recordSubmitFailure(Long reportId, String message) {
        if (operationLogWriteService == null) return;
        try {
            operationLogWriteService.recordFailure("SUBMIT_REPORT", "EXPENSE_REPORT", reportId, message);
        } catch (RuntimeException ignored) { }
    }
    /** 撤回本人审批中的报销单：APPROVING -> WITHDRAWN。 */
    @Transactional
    public ExpenseReportVO withdraw(Long reportId) {
        log.info("撤回报销单，reportId={}", reportId);
        try {
        // 查询本人且处于 APPROVING 状态的报销单。
        ExpenseReport report = getOwnedDraft3(reportId);

        // 查询当前登录用户，用于记录审批操作人。
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1)
        );
        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }

        // 保存操作前状态，再将当前状态修改为 WITHDRAWN。
        String fromStatus = report.getStatus();
        report.setStatus(ExpenseReportStatus.WITHDRAWN.name());
        // 撤回后不再存在当前审批人和当前审批节点。
        report.setCurrentApproverId(null);
        report.setCurrentNodeCode(null);
        // 追加撤回历史，审批记录只新增、不修改。
        ApprovalRecord record = new ApprovalRecord();
        record.setReportId(report.getId());
        record.setApprovalRound(report.getApprovalRound());
        record.setAction("WITHDRAW");
        record.setFromStatus(fromStatus);
        record.setToStatus(ExpenseReportStatus.WITHDRAWN.name());
        record.setOperatorId(user.getId());
        record.setComment(null);

        // 主表、待办任务和审批历史处于同一个事务中，任一步失败都会回滚。
        if (expenseReportMapper.updateById(report) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "报销单已被修改，请刷新后重试");
        }
        int cancelledTasks = approvalTaskMapper.cancelPendingTasks(
                report.getId(),
                report.getApprovalRound(),
                "申请人撤回后取消",
                LocalDateTime.now());
        if (cancelledTasks < 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前审批任务不存在或已经处理");
        }
        if (approvalRecordMapper.insert(record) != 1) {
            throw new IllegalStateException("保存撤回记录失败");
        }
        recordWithdrawSuccess(reportId);
        // 返回最新报销单详情。
        return toVO(report);
        } catch (RuntimeException exception) {
            recordWithdrawFailure(reportId, exception);
            throw exception;
        }
    }

    /**
     * 撤回失败审计使用受控摘要；操作人由 OperationLogWriteService 从登录态读取。
     * 审计自身失败不能掩盖撤回失败的原始异常。
     */
    private void recordWithdrawFailure(Long reportId, RuntimeException exception) {
        if (operationLogWriteService == null) return;
        String summary = exception instanceof BusinessException
                ? "业务校验或状态冲突"
                : "撤回报销单失败";
        try {
            operationLogWriteService.recordFailure(
                    "WITHDRAW_REPORT", "EXPENSE_REPORT", reportId, summary);
        } catch (RuntimeException auditException) {
            log.warn("记录撤回失败审计日志失败，reportId={}", reportId, auditException);
        }
    }

    private void recordWithdrawSuccess(Long reportId) {
        if (operationLogWriteService == null) return;
        try {
            operationLogWriteService.recordSuccess(
                    "WITHDRAW_REPORT", "EXPENSE_REPORT", reportId);
        } catch (RuntimeException auditException) {
            throw new IllegalStateException("操作日志写入失败", auditException);
        }
    }

}
