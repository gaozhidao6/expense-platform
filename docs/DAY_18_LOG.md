# Day 18：附件上传基础模块与本地文件存储

日期：2026-09-13

## 今日目标

在不使用 Docker 和 MinIO 的前提下，完成报销附件上传的后端基础链路：接收文件、校验权限与类型、计算文件摘要、保存到本机目录，并将附件元数据写入 MySQL。

## 今日完成内容

### 1. 附件模块骨架

在 `com.shixi.expense.attachment` 下建立了以下分层：

- `Attachment`：附件元数据实体。
- `AttachmentUploadDTO`：接收 `multipart/form-data` 中的上传文件。
- `AttachmentVO`：返回给前端的安全附件信息。
- `AttachmentMapper`：使用 MyBatis-Plus 访问附件表。
- `AttachmentService`：编排权限、校验、存储和元数据持久化。
- `AttachmentStorage`：抽象文件存储能力。
- `LocalFileAttachmentStorage`：当前使用本机目录的存储实现。
- `AttachmentController`：提供上传和列表查询接口。
- `AttachmentStorageProperties`：集中管理存储目录、大小和扩展名配置。

### 2. 附件元数据表

新增 Flyway 迁移 `V4__create_attachment_table.sql`，创建 `attachment` 表，保存：

- 报销单 ID。
- 原始文件名。
- 相对存储路径。
- 文件大小。
- Content-Type。
- SHA-256。
- 创建时间。

表中使用 `(report_id, sha256)` 唯一索引，防止同一报销单重复保存内容相同的附件；`report_id` 通过外键关联 `expense_report`。

### 3. 上传业务链路

完成 `POST /api/reports/{reportId}/attachments`：

1. 校验报销单 ID 和上传文件。
2. 从 `CurrentUserService` 获取当前登录用户 ID。
3. 校验报销单存在、属于当前用户，并且处于 `DRAFT` 或 `REJECTED`。
4. 限制文件大小为 10 MB。
5. 只允许 JPG、JPEG、PNG 和 PDF，并检查扩展名与 Content-Type 的对应关系。
6. 使用流式读取计算 SHA-256。
7. 按 `reportId + sha256` 提前检查重复附件。
8. 使用日期、报销单 ID 和 UUID 生成安全的 `objectKey`。
9. 保存文件后写入附件元数据。
10. 数据库写入失败时补偿删除已经保存的文件。
11. 将 Entity 转成 VO，不暴露服务器绝对路径和 SHA-256。

### 4. 本地文件存储

`LocalFileAttachmentStorage` 已实现：

- 输入流和 `objectKey` 非空校验。
- `resolve()` 和 `normalize()` 生成规范路径。
- 使用 `startsWith(rootDirectory)` 阻止路径穿越。
- 自动创建文件父目录。
- 使用 `Files.copy()` 写入文件，默认不覆盖同名文件。
- 使用 `Files.deleteIfExists()` 删除文件。
- 数据库只保存相对 `objectKey`，不保存服务器绝对路径。

默认存储目录为：

```text
backend/data/attachments
```

### 5. 附件列表查询

新增 `GET /api/reports/{reportId}/attachments`：

- 校验报销单 ID。
- 从登录态取得当前用户。
- 校验报销单存在且属于当前用户。
- 按创建时间倒序查询附件。
- 转换为 `AttachmentVO` 返回。

列表查询不限制报销单状态，因为已提交或已完成的报销单仍然需要查看历史附件。

### 6. 联调测试数据

本机 MySQL 的 `DEMO-DRAFT-001` 报销单中加入了两条元数据测试记录：

- `[联调测试]差旅发票.pdf`
- `[联调测试]打车票据.png`

这些记录只用于附件列表测试，没有对应的真实磁盘文件，因此不能用于下载测试。清理 SQL：

```sql
DELETE FROM attachment
WHERE storage_path LIKE 'TEST-ATTACHMENT/%';
```

## 今天理解的代码原理

### `MultipartFile` 的职责

`MultipartFile` 是 Spring 对上传文件的临时表示，提供文件名、大小、Content-Type 和输入流。它不代表文件已经永久保存，后端仍需主动写入本地目录或对象存储。

### 为什么同时校验扩展名和 Content-Type

扩展名和 Content-Type 可以互相检查，避免 `.pdf` 文件却声明为 `image/png`。但二者都来自客户端，仍然可能被伪造；当前属于基础校验，后续可增加文件特征字节识别。

### SHA-256 的用途

SHA-256 是文件内容摘要。相同内容即使改名，摘要仍然相同。本项目通过 `reportId + sha256` 判断同一报销单是否重复上传，并由数据库唯一索引处理并发情况下的最终去重。

### 为什么不能直接使用原始文件名

原始文件名可能重复、包含特殊字符或恶意路径。实际存储名称由后端使用 UUID 生成；原始文件名只用于页面展示。

### 为什么文件保存需要补偿

`@Transactional` 只能回滚数据库，不能撤销磁盘写入。如果文件保存成功而数据库插入失败，需要主动删除文件，同时不能让清理异常覆盖原始数据库异常。

## 今日验证结果

- 后端主代码 Maven 编译通过。
- `LocalFileAttachmentStorageTests` 共 4 项测试全部通过：保存文件、阻止路径穿越、拒绝覆盖和删除文件。
- 本机 MySQL 已存在 `attachment` 表，并确认插入 2 条列表测试数据。
- 附件模块已完成代码实现，但今天未执行带登录态的人工上传联调。
- 完整 `mvn test` 被旧报销测试阻断：测试仍使用旧版 `submit(reportId)` 和旧版 `ExpenseReportService` 构造器，与本次附件代码无关。

## 今日遗留

- 清理附件列表方法中已经完成但仍保留的 TODO 注释，并整理缩进。
- 人工验证上传接口、真实磁盘文件和数据库记录是否一致。
- 尚未实现附件下载和业务删除接口。
- `AttachmentVO.downloadUrl` 当前为 `null`。
- 前端尚未接入上传和附件列表组件。

## 今日总结

今天完成了附件上传的后端基础闭环，理解了 multipart 请求、文件流、大小和类型校验、SHA-256 去重、安全对象 key、路径穿越防护，以及数据库事务与文件系统之间需要补偿处理的原因。
