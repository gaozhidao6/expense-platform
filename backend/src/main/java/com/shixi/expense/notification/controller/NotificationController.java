package com.shixi.expense.notification.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shixi.expense.notification.dto.NotificationPageQueryDTO;
import com.shixi.expense.notification.service.NotificationService;
import com.shixi.expense.notification.vo.NotificationVO;

import jakarta.validation.Valid;

/** 当前登录用户的站内通知接口。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 分页查询当前登录用户自己的通知。 */
    @GetMapping
    public IPage<NotificationVO> queryMyNotifications(
            @Valid NotificationPageQueryDTO query) {
        return notificationService.queryMyNotifications(query);
    }

    /** 查询当前登录用户的未读通知数量。 */
    @GetMapping("/unread-count")
    public Map<String, Long> countMyUnread() {
        return Map.of("count", notificationService.countMyUnread());
    }

    /** 将当前登录用户的一条通知标记为已读。 */
    @PutMapping("/{notificationId}/read")
    public void markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
    }

    /** 将当前登录用户的全部未读通知标记为已读。 */
    @PutMapping("/read-all")
    public void markAllAsRead() {
        notificationService.markAllAsRead();
    }
}
