# 数据字典（核心表）

## operation_log

| 字段 | 含义 |
|---|---|
| operator_id | 实际执行操作的用户 ID |
| operation | 操作编码，如 `FINANCE_APPROVE` |
| business_type / business_id | 关联业务类型和主键 |
| request_id | 请求链路 ID |
| result | `SUCCESS` 或 `FAILURE` |
| error_message | 脱敏后的错误摘要 |
| duration_ms | 请求处理耗时 |
| created_at | 日志创建时间 |

操作日志原则上只新增和查询，不提供修改接口。
