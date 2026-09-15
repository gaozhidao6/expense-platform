# Day 10：报销单分页查询

日期：2026-09-03

## 今日目标

为报销单列表增加分页、关键字和状态筛选，返回轻量列表摘要，避免列表接口一次加载费用明细。

## 已完成内容

- 新增 `ExpenseReportPageQueryDTO`：接收页码、每页数量、关键字和状态。
- 新增 `ExpenseReportListVO`：返回报销单列表摘要。
- 在 `ExpenseReportService` 新增 `queryPage()`：使用 MyBatis-Plus `Page` 和 `IPage` 查询。
- 关键字匹配 `reportNo` 和 `title`。
- 状态参数作为可选筛选条件。
- 当前阶段按当前登录用户的 `applicantId` 限制数据范围。
- 新增接口：`GET /api/reports/page`。
- 列表接口不返回费用明细，详情仍使用 `GET /api/reports/{id}`。

## 接口示例

```text
GET /api/reports/page?page=1&size=10
GET /api/reports/page?page=1&size=10&keyword=差旅
GET /api/reports/page?page=1&size=10&status=COMPLETED
```

## 分层职责

```text
Controller：绑定查询参数并调用 Service
DTO：校验分页和筛选参数
Service：构造数据权限和查询条件
Mapper：执行 MyBatis-Plus 分页查询
VO：返回列表摘要，不暴露实体对象
```

## 验收状态

- [x] DTO 和 VO 已创建
- [x] 分页查询 Service 已实现
- [x] 关键字筛选已实现
- [x] 状态筛选已实现
- [x] 当前用户数据权限已实现
- [x] Controller 接口已补充
- [x] 后端编译验证通过

## 后续任务

- 增加前端报销单列表和筛选页面。
- 根据角色扩展经理、财务和管理员的数据范围。
- 增加时间范围筛选和统计汇总。

## 前端完成情况

- 报销单列表页已接入时间范围筛选。
- 已增加统计卡片：总数、审批中、财务审核、待付款、已完成、总金额。
- 列表查询和统计查询会同步使用开始时间、结束时间。
- 前端页面地址：`/reports`。
- 已执行 `npm run build`，构建通过。
