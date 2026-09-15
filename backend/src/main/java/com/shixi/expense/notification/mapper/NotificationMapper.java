package com.shixi.expense.notification.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.notification.entity.Notification;

/** 站内通知的数据访问接口，基础单表操作由 MyBatis-Plus 提供。 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
