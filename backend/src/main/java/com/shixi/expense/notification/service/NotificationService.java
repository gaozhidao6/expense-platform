package com.shixi.expense.notification.service;

import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.common.security.CurrentUserService;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.dto.NotificationPageQueryDTO;
import com.shixi.expense.notification.entity.Notification;
import com.shixi.expense.notification.enums.NotificationType;
import com.shixi.expense.notification.mapper.NotificationMapper;
import com.shixi.expense.notification.config.NotificationRabbitProperties;
import com.shixi.expense.notification.messaging.NotificationMessagePublisher;
import com.shixi.expense.notification.vo.NotificationVO;

/** 站内通知业务服务。 */
@Service
@EnableConfigurationProperties(NotificationRabbitProperties.class)
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final CurrentUserService currentUserService;
    private final NotificationMessagePublisher messagePublisher;
    private final NotificationRabbitProperties rabbitProperties;

    public NotificationService(
            NotificationMapper notificationMapper,
            CurrentUserService currentUserService,
            NotificationMessagePublisher messagePublisher,
            NotificationRabbitProperties rabbitProperties) {
        this.notificationMapper = notificationMapper;
        this.currentUserService = currentUserService;
        this.messagePublisher = messagePublisher;
        this.rabbitProperties = rabbitProperties;
    }

    private void validateCreateCommand(NotificationCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("通知创建参数不能为空");
        }

        if (command.recipientId() == null || command.recipientId() <= 0) {
            throw new IllegalArgumentException("通知接收人不合法");
        }

        if (command.type() == null) {
            throw new IllegalArgumentException("通知类型不合法");
        }

        if (command.title() == null
                || command.title().trim().isEmpty()
                || command.title().length() > 150) {
            throw new IllegalArgumentException("通知标题不合法");
        }

        if (command.content() == null
                || command.content().trim().isEmpty()
                || command.content().length() > 1000) {
            throw new IllegalArgumentException("通知内容不合法");
        }

        if (command.businessType() == null
                || command.businessType().trim().isEmpty()
                || command.businessType().length() > 50) {
            throw new IllegalArgumentException("业务类型不合法");
        }

        if (command.businessId() == null || command.businessId() <= 0) {
            throw new IllegalArgumentException("业务 ID 不合法");
        }

        if (command.messageKey() == null
                || command.messageKey().trim().isEmpty()
                || command.messageKey().length() > 100) {
            throw new IllegalArgumentException("消息幂等键不合法");
        }
    }
    /** 创建通知：根据开关选择异步发送或同步落库。 */
    public void create(NotificationCreateCommand command) {
        validateCreateCommand(command);
        if (rabbitProperties.enabled()) {
            // 异步模式下将通知交给生产者。
            messagePublisher.publish(command);
            // 立即返回，避免异步发送后又在当前线程重复保存。
            return;
        }
        // RabbitMQ 未开启时同步保存，保证本地环境仍可使用通知功能。
        persist(command);
    }

    /** 消费者专用入口：只校验和落库，不能再次发布消息。 */
    public void createFromMessage(NotificationCreateCommand command) {
        // 队列消息属于外部输入，落库前仍需完整校验。
        validateCreateCommand(command);
        // 消费者直接落库，不能再次进入消息发布入口。
        persist(command);
    }

    /** 将经过校验的通知写入数据库。 */
    private void persist(NotificationCreateCommand command) {
        Notification notification = new Notification();
        notification.setRecipientId(command.recipientId());
        notification.setType(command.type().name());
        notification.setTitle(command.title());
        notification.setContent(command.content());
        notification.setBusinessType(command.businessType().trim());
        notification.setBusinessId(command.businessId());
        notification.setMessageKey(command.messageKey().trim());
        notification.setIsRead(0);

        try {
            int rows = notificationMapper.insert(notification);

            if (rows != 1) {
                throw new IllegalStateException("创建通知失败");
            }
        } catch (DuplicateKeyException exception) {
            // Only the same recipient/message pair is a successfully delivered duplicate.
            Long existing = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getRecipientId, command.recipientId())
                    .eq(Notification::getMessageKey, command.messageKey().trim()));
            if (existing == null || existing == 0) throw exception;
        }
    }

    /** 分页查询当前登录用户自己的通知。 */
    public IPage<NotificationVO> queryMyNotifications(NotificationPageQueryDTO query) {
        Long currentUserId = currentUserService.requireCurrentUserId();
        Page<Notification> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Notification> wrapper =
                new LambdaQueryWrapper<>();

        // 最基本的数据权限：当前用户只能查询发给自己的通知。
        wrapper.eq(Notification::getRecipientId, currentUserId);
        if (StringUtils.hasText(query.getType())) {
            wrapper.eq(
                    Notification::getType,
                    NotificationType.from(query.getType()).name());
        }
        if (query.getIsRead() != null) {
            wrapper.eq(
                    Notification::getIsRead,
                    query.getIsRead());
        }
        wrapper.orderByDesc(Notification::getCreatedAt);

        return notificationMapper.selectPage(page, wrapper).convert(this::toVO);
    }

    /**
     * 将数据库通知实体转换成前端响应对象。
     *
     * <p>不直接返回 Entity，可以避免以后新增内部字段时被接口意外暴露。</p>
     */
    private NotificationVO toVO(Notification notification) {
        if (notification == null) {
            return null;
        }

        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setBusinessType(notification.getBusinessType());
        vo.setBusinessId(notification.getBusinessId());
        vo.setIsRead(notification.getIsRead());
        vo.setReadAt(notification.getReadAt());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }

    /** 查询当前登录用户的未读通知数量。 */
    public long countMyUnread() {
        Long userId = currentUserService.requireCurrentUserId();
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                                            .eq(Notification::getRecipientId, userId)
                                            .eq(Notification::getIsRead, 0));
    }

    /** 将一条属于当前登录用户的通知标记为已读。 */
    public void markAsRead(Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            throw new IllegalArgumentException("通知 ID 不合法");
        }

        Long userId = currentUserService.requireCurrentUserId();
        Notification notification = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                                                .eq(Notification::getId, notificationId)
                                                .eq(Notification::getRecipientId, userId));

        if (notification == null) {
            throw new IllegalArgumentException(
                    "通知不存在或无权操作");
        }
        if (Integer.valueOf(1).equals(notification.getIsRead())) {
            return;
        }

        notification.setIsRead(1);
        notification.setReadAt(LocalDateTime.now());
        int rows = notificationMapper.updateById(notification);
        if (rows != 1) {
            throw new IllegalStateException("通知标记已读失败");
        }
    }

    /**
     * 将当前登录用户的全部未读通知标记为已读。
     *
     * <p>更新条件必须包含当前用户 ID，避免修改其他用户的通知；
     * 只处理未读数据，避免覆盖已有的首次阅读时间。</p>
     */
    public void markAllAsRead() {
        Long userId = currentUserService.requireCurrentUserId();

        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper
                .eq(Notification::getRecipientId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadAt, LocalDateTime.now());

        notificationMapper.update(wrapper);
    }
}
