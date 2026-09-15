# Day 19：附件查询收尾、下载接口与联调

日期：2026-09-14

## 明日目标

在 Day 18 上传链路的基础上，先完成代码收尾和后端真实联调，再实现安全的附件下载接口。仍然使用本机目录和 MySQL，不接入 MinIO、Docker 或 AI 识别。

## 明日执行顺序

### 1. 整理附件列表代码

- 删除已经完成的 TODO 注释并整理缩进。
- 将“查询报销单并校验归属”的重复代码提取成私有方法。
- 上传操作继续要求 `DRAFT` 或 `REJECTED`；列表和下载只要求用户有查看权限。

建议提取：

```java
private ExpenseReport requireOwnedReport(
        Long reportId,
        Long userId)
```

### 2. 增加文件读取能力

在 `AttachmentStorage` 中增加读取接口，例如：

```java
Resource load(String objectKey) throws IOException;
```

`LocalFileAttachmentStorage.load()` 需要：

1. 校验 `objectKey`。
2. 解析并规范化路径。
3. 阻止路径穿越。
4. 判断文件存在且为普通文件。
5. 返回可供 Spring 下载的 `Resource`。

### 3. 实现附件下载业务

计划接口：

```text
GET /api/reports/{reportId}/attachments/{attachmentId}/download
```

Service 必须校验：

- 报销单存在并且当前用户有权查看。
- 附件记录存在。
- 附件的 `reportId` 与路径中的 `reportId` 一致。
- 磁盘文件存在。

Controller 返回 `ResponseEntity<Resource>`，正确设置 Content-Type、Content-Length 和下载文件名。下载响应不能暴露服务器绝对路径。

### 4. 执行后端真实联调

使用正确的 Swagger 地址：

```text
http://localhost:8080/swagger-ui/index.html
```

验证：

1. 员工向自己的草稿报销单上传合法附件。
2. 磁盘和数据库同时产生记录。
3. 附件列表按时间倒序返回。
4. 下载内容与上传内容一致。
5. 重复上传返回 409。
6. 非法类型、超大文件、越权访问和非法状态被拒绝。

### 5. 补充测试

- 为 `AttachmentService` 增加单元测试：归属校验、状态校验、重复附件和元数据保存失败补偿。
- 为下载存储逻辑增加：文件存在、文件不存在、路径穿越测试。
- 更新旧报销测试中的 `submit(reportId, idempotencyKey)` 调用和构造器依赖，使完整 `mvn test` 恢复通过。

## 明日暂时不做

- 不接入 MinIO。
- 不做 AI 发票识别。
- 不允许已进入审批流程的报销单删除附件。
- 后端下载闭环完成前，不急于实现前端组件。

## 明日验收标准

- 上传、列表和下载接口形成后端闭环。
- 用户不能查询或下载其他申请人的附件。
- 下载文件内容与上传文件一致。
- 数据库中只保存相对路径，不暴露服务器目录。
- 附件相关单元测试通过。
- 完整 Maven 测试的旧接口签名问题得到处理，或在日志中明确记录剩余阻塞。

