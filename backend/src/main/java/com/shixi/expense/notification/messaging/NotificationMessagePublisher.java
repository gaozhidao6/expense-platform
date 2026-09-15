package com.shixi.expense.notification.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shixi.expense.notification.config.NotificationRabbitProperties;
import com.shixi.expense.notification.dto.NotificationCreateCommand;

/** RabbitMQ 通知消息生产者。 */
@Component
public class NotificationMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final NotificationRabbitProperties properties;

    public NotificationMessagePublisher(
            RabbitTemplate rabbitTemplate,
            NotificationRabbitProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /** 发布消息：有事务时等事务成功提交，没有事务时直接发送。 */
    public void publish(NotificationCreateCommand command) {
        // 有业务事务时，消息必须等事务提交成功后才能发送。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 注册事务同步回调。
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            // 数据库提交成功后发送消息。
                            send(command);
                        }
                    });
        } else {
            // 没有事务时可以立即发送。
            send(command);
        }
        // 原因：必须避免数据库已经回滚，但消费者却收到消息并生成通知。
    }

    /** 真正执行 RabbitMQ 发送操作。 */
    private void send(NotificationCreateCommand command) {
        // 按交换机和 routing key 投递通知命令，转换器负责序列化为 JSON。
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey(),
                command);
    }
}
