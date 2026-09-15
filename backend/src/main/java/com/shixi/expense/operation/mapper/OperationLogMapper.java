package com.shixi.expense.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.operation.entity.OperationLog;

import org.apache.ibatis.annotations.Mapper;

/** 操作日志数据访问接口。日志原则上只允许新增和查询。 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
