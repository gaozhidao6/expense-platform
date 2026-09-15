package com.shixi.expense.department.vo;

/**
 * 面向部门查询接口的稳定返回结构。
 *
 * <p>不要直接返回 Entity，避免数据库内部字段（例如逻辑删除标记）随接口暴露。</p>
 */
public record DepartmentVO(
        Long id,
        Long parentId,
        String name,
        String code,
        Long managerUserId,
        Integer status,
        Integer sortOrder) {
}
