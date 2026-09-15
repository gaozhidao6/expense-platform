package com.shixi.expense.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.invoice.entity.InvoiceRecognitionSuggestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发票识别建议数据访问接口。
 *
 * <p>常用的 insert、selectById 和 updateById 已由 MyBatis-Plus 提供。</p>
 */
@Mapper
public interface InvoiceRecognitionSuggestionMapper
        extends BaseMapper<InvoiceRecognitionSuggestion> {
}
