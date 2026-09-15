# Day 20：RabbitMQ、MinIO 与发票识别后端收尾

日期：2026-09-14

状态：今日任务已完成

## 今日目标

完成 RabbitMQ 异步通知，补充可切换的 MinIO 附件存储，实现不依赖真实外部服务的发票识别后端骨架，并用自动化测试锁定核心行为。

## 今日完成内容

### 1. RabbitMQ 通知链路

- 配置持久化 DirectExchange、Queue、Binding 和 JSON 消息转换器。
- 生产者在无事务时立即发送，在业务事务提交成功后再发送事务内消息。
- 消费者通过专用入口校验并保存通知，避免消费后再次发布形成循环。
- RabbitMQ 关闭时保留同步写库路径。
- 使用 `recipientId + messageKey` 数据库唯一约束保证重复消费幂等。

### 2. MinIO 附件存储

- 增加 MinIO Java SDK、连接配置和 `MinioClient` Bean。
- 使用 `storage-type` 在本地文件和 MinIO 存储之间切换，默认仍为 `local`。
- 完成 `MinioAttachmentStorage.save()`、`load()` 和 `delete()`。
- 上传、读取和删除异常统一转换为 `IOException`。
- 读取操作使用 try-with-resources 关闭对象输入流。
- 启动 MinIO 模式时检查 bucket，不存在则自动创建。
- `AttachmentService` 和 Controller 不依赖具体存储产品，无需整体改写。

### 3. AI 发票识别后端

- 定义 `InvoiceRecognitionClient` 识别接口。
- 定义包含发票号码、日期、金额、商户和置信度的结构化结果。
- 增加不访问外部网络的 `MockInvoiceRecognitionClient`。
- 增加识别 Service 和 REST Controller。
- 复用附件下载服务完成用户权限和附件归属校验。
- 使用 try-with-resources 打开并关闭附件输入流。
- 校验识别结果的必填字段、日期、正数金额和 0～1 置信度。
- 识别结果只作为建议返回，不直接修改报销单和明细。

### 4. 注释整理

- 删除 RabbitMQ、MinIO、发票识别中已经完成的 TODO。
- 将附件 Service 和本地存储中“骨架”“后续实现”等过期说明改为当前行为。
- 保留仍然真实未完成的 TODO，包括审批失败审计、撤回审计和 Department VO 转换。

## 自动化测试改进

### RabbitMQ

- 消费者消息转交。
- 无事务立即发送、事务提交后发送、事务回滚不发送。
- 开关开启时异步发布，关闭时同步落库。
- 重复通知按幂等成功处理。

### MinIO

- 上传、读取和删除参数校验。
- 上传参数中的 bucket、objectKey、对象大小和分片大小。
- 下载内容及 Resource 长度。
- 上传、读取和删除异常转换。
- 空 bucket 配置拒绝。
- bucket 不存在时创建，存在时不重复创建。

### 发票识别

- Mock 客户端结构化返回和参数校验。
- Controller 正确转交识别请求。
- Service 的 ID 校验、正常识别和 IOException 转换。
- null 结果、空发票号、空商户、未来日期、非正金额和非法置信度拒绝。
- 置信度边界值 0 和 1 可以通过。

## 今日验证结果

执行：

```text
mvn clean test
```

结果：

- 测试总数：84。
- 失败：0。
- 错误：0。
- 跳过：0。
- Maven 退出码：0。

测试日志中的 Tomcat Native 版本提示不影响测试结果。

## 今日未做但不阻塞完成

- 按用户选择，不安装 Docker，也不连接真实 RabbitMQ 或 MinIO。
- 发票识别当前使用 Mock 客户端，不调用真实 AI。
- Publisher Confirm、死信队列和 Outbox 属于后续可靠性增强，不属于今日基础链路范围。
- 识别建议的数据库持久化和人工确认流程留到下一阶段。

## 下一阶段建议

实现“识别建议持久化与人工确认”：先保存 AI 建议状态，再由用户确认或修改，确认后才更新正式报销明细。

## 今日总结

今日完成 RabbitMQ 通知、MinIO 存储和发票识别后端的代码收尾及无外部服务测试。三条增强链路均保持接口解耦，在默认本地配置下可以稳定编译和运行，今日任务完成。
