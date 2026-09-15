package com.shixi.expense.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.approval.entity.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/** 审批记录数据访问入口。当前只使用新增和查询，禁止修改历史记录。 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {

    // 当前审批时间线使用 BaseMapper 的条件查询完成。
}
