package com.shixi.expense.department.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.department.entity.Department;
import com.shixi.expense.department.mapper.DepartmentMapper;
import com.shixi.expense.department.vo.DepartmentVO;

/**
 * 部门业务服务。
 *
 * <p>写作思路：Service 位于 Controller 和 Mapper 之间，负责把一次业务操作
 * 组织起来。Controller 不应该直接调用 Mapper，否则 HTTP 细节和数据库细节会
 * 混在一起，后续增加权限、缓存或事务时会难以维护。</p>
 *
 * <p>部门分页查询的预期流程是：</p>
 * <ol>
 *     <li>接收 Controller 传入的分页条件；</li>
 *     <li>校验或修正页码、每页数量等业务参数；</li>
 *     <li>调用 DepartmentMapper 执行 MyBatis-Plus 分页查询；</li>
 *     <li>将数据库 Entity 转换为安全、稳定的 VO；</li>
 *     <li>把分页结果交回 Controller。</li>
 * </ol>
 *
 * <p>Service 不直接读取 {@code HttpServletRequest}，也不负责决定 HTTP 状态码；
 * 它只关心业务是否正确。</p>
 */
@Service
public class DepartmentService {

    /**
     * Service 依赖 Mapper 访问数据库。
     * 使用 final 表示这个依赖在对象创建时必须完成初始化，之后不再替换。
     */
    private final DepartmentMapper departmentMapper;

    /**
     * Spring 创建 DepartmentService 时，会自动从容器中找到 DepartmentMapper
     * 并传入这里。因为当前类只有一个构造器，所以不需要额外添加 @Autowired。
     */

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }
    // 分页参数的范围限制仍应在 Controller 参数校验或本方法中统一处理。
    public Page<DepartmentVO> queryPage(long page,long size){
        Page<Department> pageRequest = new Page<>(page,size);
        Page<Department> entityPage = departmentMapper.selectPage(pageRequest,null);
        Page<DepartmentVO> result = new Page<>(
                entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(entityPage.getRecords().stream()
                .map(this::toVO)
                .toList());
        return result;
    }

    private DepartmentVO toVO(Department department) {
        return new DepartmentVO(
                department.getId(),
                department.getParentId(),
                department.getName(),
                department.getCode(),
                department.getManagerUserId(),
                department.getStatus(),
                department.getSortOrder());
    }
}
