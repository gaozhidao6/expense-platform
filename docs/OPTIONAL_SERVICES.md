# 可选服务

本机环境默认同步通知、本地附件、Mock 识别；只需 MySQL 即可演示主流程。

## RabbitMQ

Compose 的 `extras` profile 可提供独立的开发 broker。启用应用时，在本机设置或在 Compose 的 backend.environment 增加：

```yaml
NOTIFICATION_RABBITMQ_ENABLED: 'true'
RABBITMQ_HOST: rabbitmq
RABBITMQ_USERNAME: expense_mq
RABBITMQ_PASSWORD: expense_mq_dev
BACKGROUND_JOBS_ENABLED: 'true'
```

本机运行的 host 改成 localhost。密码须与 RabbitMQ 容器一致；示例只供本地开发。消费失败进入 `expense.notification.queue.dead`。将已有旧队列改为新增死信参数时，RabbitMQ 会拒绝不一致声明；先备份并处理旧队列消息，再由维护者迁移队列，或使用独立的新队列 / exchange 配置，不能直接删除含未处理消息的队列。

## MinIO

启动 extras 后，本机 console 为 http://localhost:9001。backend.environment 示例：

```yaml
ATTACHMENT_STORAGE_TYPE: minio
MINIO_ENDPOINT: http://minio:9000
MINIO_ACCESS_KEY: expense_minio
MINIO_SECRET_KEY: expense_minio_dev
MINIO_BUCKET: expense-attachments
```

本机运行将 endpoint 改为 http://localhost:9000。启用前确认服务已就绪。切换存储类型不会迁移历史附件，应使用独立演示库或先迁移历史对象。

## AI 识别

设置 INVOICE_RECOGNITION_PROVIDER=ai，并配置 INVOICE_AI_BASE_URL、INVOICE_AI_API_KEY、INVOICE_AI_MODEL；当前适配器面向兼容的聊天 / 视觉响应格式，需验证供应商接口契约。Mock 不是 OCR，也不代表真实 AI 准确率。密钥通过环境变量提供，不写入仓库。真实服务涉及票据出境与费用时，由部署者自行选择适用环境。
