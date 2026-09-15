package com.shixi.expense.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.report.entity.ExpenseItem;
import org.apache.ibatis.annotations.Mapper;

/** 费用明细表的数据访问入口。 */
@Mapper
public interface ExpenseItemMapper extends BaseMapper<ExpenseItem> {

}
