package com.shixi.expense.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.department.entity.Department;
import org.apache.ibatis.annotations.Mapper;

/** 部门数据访问接口，基础单表操作由 MyBatis-Plus 提供。 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
