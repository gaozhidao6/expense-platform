package com.shixi.expense.department.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.department.service.DepartmentService;
import com.shixi.expense.department.vo.DepartmentVO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * 部门接口控制器。
 *
 * <p>写作思路：Controller 是 HTTP 边界层，只做三件事：接收请求参数、调用
 * Service、组织响应。它不应该直接拼接 SQL，也不应该在这里编写“部门是否可用”
 * 之类的业务规则。</p>
 *
 * <p>部门分页查询的请求链路是：</p>
 * <pre>
 * GET /api/departments?page=1&amp;size=10
 *     -> Controller 接收参数
 *     -> DepartmentService 执行业务流程
 *     -> DepartmentMapper 查询 MySQL
 *     -> Controller 返回分页 VO
 * </pre>
 *
 * <p>分页接口使用 {@code @RequestParam} 接收 page 和 size，参数合法性应由
 * Bean Validation 或 Service 保证。</p>
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    /**
     * Controller 只依赖 Service，不直接依赖 Mapper。
     * 这样 HTTP 层和数据库访问层保持隔离。
     */
    private final DepartmentService departmentService;

    /**
     * Spring 创建 Controller 时自动注入 DepartmentService。
     */
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Page<DepartmentVO> queryPage( @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10")long size) {
        return departmentService.queryPage(page, size);
    }
    
    // 当前仅管理员可访问；如果以后开放给其他角色，需要补充部门数据权限。
}
