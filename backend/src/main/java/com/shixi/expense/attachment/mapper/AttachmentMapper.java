package com.shixi.expense.attachment.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.attachment.entity.Attachment;

/**
 * 附件元数据数据访问接口。
 *
 * <p>基础单表操作由 MyBatis-Plus 提供，复杂查询后续再按需要补充。</p>
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
