package com.shixi.expense.department.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 部门实体，保存组织层级、负责人和启用状态。 */
@Data
@TableName("department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级部门 ID；根部门可以为空。 */
    private Long parentId;
    private String name;
    /** 部门业务编码，用于规则匹配和演示数据定位。 */
    private String code;
    /** 部门经理用户 ID，审批路由会使用该字段。 */
    private Long managerUserId;
    /** 1 表示启用，0 表示停用。 */
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，查询时由 MyBatis-Plus 自动过滤。 */
    @TableLogic
    private Integer deleted;
}
