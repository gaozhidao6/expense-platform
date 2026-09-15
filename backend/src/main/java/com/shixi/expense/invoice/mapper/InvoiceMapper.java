package com.shixi.expense.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.invoice.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;

/** 正式发票数据访问接口。 */
@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {
}
