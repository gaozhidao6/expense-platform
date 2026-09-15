# API 快速说明

基础地址：`/api`。除登录与健康检查外，接口均需在请求头携带
`Authorization: Bearer <JWT>`。服务使用统一响应结构；业务错误会返回明确的
HTTP 状态和错误信息。

## 认证与基础资料

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` | 用户登录并获取 JWT | 无需登录 |
| GET | `/auth/me` | 获取当前登录用户资料 | 已登录 |
| GET | `/health` | 服务健康检查 | 无需登录 |
| GET | `/departments?page=1&size=10` | 分页查询部门 | ADMIN |
| GET | `/users` | 分页查询用户 | ADMIN |

## 报销单与明细

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/reports` | 创建本人报销草稿及明细 | 已登录 |
| GET | `/reports/page` | 分页查询权限范围内的报销单 | 已登录 |
| GET | `/reports/{id}` | 查询报销单详情及明细 | 已登录且有数据权限 |
| PUT | `/reports/{id}` | 修改本人草稿/已驳回报销单 | 申请人 |
| POST | `/reports/{id}/items` | 新增费用明细 | 申请人 |
| PUT | `/reports/{id}/items/{itemId}` | 修改费用明细 | 申请人 |
| DELETE | `/reports/{id}/items/{itemId}` | 删除费用明细 | 申请人 |
| POST | `/reports/{id}/submit` | 提交审批；必须携带 `Idempotency-Key` | 申请人 |
| POST | `/reports/{id}/withdraw` | 撤回审批中的本人报销单 | 申请人 |
| GET | `/reports/statistics` | 报销统计 | 已登录 |
| GET | `/reports/export` | 导出权限范围内的 CSV | 已登录 |

提交接口的 `Idempotency-Key` 必填；同一用户、同一报销单使用同一键的重复请求只会处理一次。

## 审批、付款与通知

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/approvals/my-pending` | 查询当前用户待办审批任务 | 经理/财务 |
| GET | `/approvals/{reportId}/records` | 查询审批历史 | 申请人、经理、财务、ADMIN |
| POST | `/approvals/{reportId}/manager/approve` | 经理通过 | MANAGER |
| POST | `/approvals/{reportId}/manager/reject` | 经理驳回；请求体 `comment` 必填 | MANAGER |
| POST | `/approvals/{reportId}/finance/approve` | 财务审核通过 | FINANCE |
| POST | `/approvals/{reportId}/finance/reject` | 财务驳回；请求体 `comment` 必填 | FINANCE |
| POST | `/approvals/{reportId}/pay` | 确认付款 | FINANCE |
| GET | `/notifications` | 分页查询我的站内通知 | 已登录 |
| GET | `/notifications/unread-count` | 查询未读数 | 已登录 |
| PUT | `/notifications/{id}/read` | 标记一条通知已读 | 通知接收人 |
| PUT | `/notifications/read-all` | 标记全部通知已读 | 已登录 |

## 附件与发票识别

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/reports/{id}/attachments` | 以 `multipart/form-data` 上传，字段名为 `file` | 申请人；草稿/已驳回 |
| GET | `/reports/{id}/attachments` | 查询附件列表 | 有查看权限的用户 |
| GET | `/reports/{id}/attachments/{attachmentId}/download` | 下载附件 | 有查看权限的用户 |
| DELETE | `/reports/{id}/attachments/{attachmentId}` | 删除附件 | 申请人；草稿/已驳回 |
| POST | `/reports/{id}/attachments/{attachmentId}/recognition` | 触发识别并保存待确认建议 | 申请人；草稿 |
| GET | `/reports/{id}/invoice-suggestions/{suggestionId}` | 查询一条识别建议 | 有查看权限的用户 |
| PUT | `/reports/{id}/invoice-suggestions/{suggestionId}/confirm` | 用用户核对后的字段确认建议并更新明细 | 申请人；草稿 |
| POST | `/reports/{id}/invoice-suggestions/{suggestionId}/reject` | 拒绝识别建议 | 申请人 |

识别结果只作为建议保存，确认前不会写入正式费用明细或发票数据。默认使用本地 Mock 识别器；配置真实 AI 后，接口行为保持不变。

## 审计与追踪

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/operation-logs` | 按操作人、操作类型、结果和时间范围查询审计日志 | ADMIN/AUDITOR |

所有请求都可通过 `X-Request-Id` 关联日志；未提供时服务会自动生成并在响应头返回。审批、付款与撤回失败会额外保留独立事务的 `FAILURE` 审计记录，且不会保存审批意见、密码、令牌或付款敏感信息。
