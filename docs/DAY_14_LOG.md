# Day 14：操作日志与审计查询

日期：2026-09-04

## 今日目标

建立操作日志查询模块，记录并查询登录、报销单操作、审批和导出等关键行为。

## 已建立代码框架

- `OperationLog`：对应 `operation_log` 表。
- `OperationLogPageQueryDTO`：分页、操作人、操作类型、业务类型、结果和时间范围参数。
- `OperationLogVO`：日志查询返回对象。
- `OperationLogMapper`：日志数据访问接口。
- `OperationLogService`：分页查询 Service 入口和中文 TODO。
- `OperationLogController`：`GET /api/operation-logs` 接口入口。

## 待实现内容

- 只允许管理员或审计角色查询操作日志。
- 按条件分页查询并按时间倒序排列。
- 在关键业务动作中写入 SUCCESS/FAILURE 日志。
- 记录 requestId、请求路径、耗时和错误信息。
- 前端增加日志查询页面。
