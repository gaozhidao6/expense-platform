# Day 6：报销单与费用明细

日期：2026-08-29

## 今日目标

完成报销单草稿和费用明细的基础链路，理解一对多关系、事务边界、逻辑删除和金额精度。今天只处理“创建/编辑草稿和维护明细”，提交与审批放到后续任务。

## 今日要回答的原理问题

1. 为什么一张报销单可以对应多条费用明细？
2. 为什么总金额必须由后端使用 `BigDecimal` 重新计算，不能直接相信前端传来的总额？
3. 为什么保存报销单和明细需要事务？
4. 逻辑删除和物理删除有什么区别？
5. 为什么只能编辑自己的草稿？

## 实现任务

### 1. 设计数据模型

报销单是主表，费用明细是从表：

```text
expense_report (1)  ------  (N) expense_item
```

报销单至少需要保存：申请人、标题、状态、总金额、版本号、创建时间和更新时间。

费用明细至少需要保存：报销单 ID、费用类型、发生日期、金额、说明以及逻辑删除标记。

### 2. 创建后端分层代码

按现有项目结构创建 `report` 模块：

```text
report/
├── controller
├── service
├── mapper
├── entity
├── dto
└── vo
```

建议准备以下对象：

- `ExpenseReport`、`ExpenseItem`：数据库实体
- `ExpenseReportCreateDTO`、`ExpenseItemDTO`：创建和编辑请求
- `ExpenseReportVO`、`ExpenseItemVO`：接口响应对象
- `ExpenseReportMapper`、`ExpenseItemMapper`：数据库访问

### 3. 实现草稿接口

建议接口：

```text
POST   /api/reports                 创建草稿
GET    /api/reports/{id}            查询草稿详情
PUT    /api/reports/{id}            修改草稿及明细
POST   /api/reports/{id}/items      新增明细
PUT    /api/reports/{id}/items/{itemId} 修改明细
DELETE /api/reports/{id}/items/{itemId} 删除明细
```

### 4. 后端计算总额

总金额必须只统计当前有效明细：

```java
BigDecimal totalAmount = items.stream()
        .filter(item -> !item.isDeleted())
        .map(ExpenseItem::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
```

金额字段使用 `DECIMAL`，Java 使用 `BigDecimal`，不要使用 `double` 或 `float`。

### 5. 添加数据权限校验

编辑、删除明细前必须同时校验：

```text
当前用户 ID == 报销单申请人 ID
报销单状态 == DRAFT
```

任何一个条件不满足，都拒绝操作并返回明确业务错误。

### 6. 使用事务保证一致性

创建或整体修改报销单时，报销单和明细应在同一个事务中保存：

```java
@Transactional
public Long createDraft(ExpenseReportCreateDTO request) {
    // 保存报销单、保存明细、重新计算总额
}
```

如果明细保存失败，报销单主记录也应回滚，不能留下半成品数据。

## 今日验收标准

- [x] 可以创建一张只有草稿状态的报销单。
- [x] 可以新增、修改和逻辑删除费用明细。
- [x] 创建及维护明细时，总金额始终等于有效明细金额之和。
- [x] 金额使用 `BigDecimal`，数据库使用 `DECIMAL`。
- [x] 非本人不能查看报销单详情。
- [x] 非本人不能编辑或删除报销单明细。
- [x] 非草稿状态不能修改明细。
- [x] 任一保存步骤失败时，主表和从表整体回滚。
- [x] 已补充创建、金额计算、越权操作、非法状态和事务回滚测试。

## 今日建议执行顺序

1. 阅读 `V1__init_schema.sql`，确认现有报销相关表是否已经存在。
2. 若表不存在，新增 Flyway migration；不要直接手工改数据库结构。
3. 创建 Entity、Mapper、DTO 和 VO。
4. 编写 Service，先完成创建草稿和明细维护。
5. 添加 Controller 和参数校验。
6. 用 MockMvc 或 Swagger 验证成功、空明细、越权和非法状态场景。
7. 运行 `mvn test`，把实际结果补到本日志末尾。

## 常见风险

- 直接接收前端 `totalAmount`，导致金额被篡改。
- 用 `double` 计算金额产生精度误差。
- 删除明细时物理删除，导致审计和历史数据丢失。
- 只校验报销单存在，不校验申请人，造成越权。
- 在 Controller 中拆分多次保存，导致事务边界不完整。

## 今日结果记录

实现状态：Day 6 调整后的任务已完成。已实现草稿创建、本人详情查询、明细新增/修改/逻辑删除、金额重算、数据权限和事务回滚。修改报销单标题与说明的 `PUT /api/reports/{id}` 按计划顺延到明天。

测试结果：`Tests run: 37, Failures: 0, Errors: 0, BUILD SUCCESS`。

真实接口验收：

```text
员工登录并创建两条明细       -> 200
创建状态                    -> DRAFT
300.00 + 50.00             -> totalAmount 350.00
本人查询详情                -> 200
标题为空                    -> 400
其他用户查询员工报销单       -> 403
无 Token 查询               -> 401
第二条明细数据库插入失败     -> 主表和第一条明细整体回滚
原总额 350，新增明细 100     -> 450
将新增明细修改为 200         -> 550
删除该明细                  -> 350
数据库检查删除后的明细        -> deleted = 1
其他用户删除员工的明细        -> 403
非 DRAFT 状态维护明细        -> 拒绝，且 Mapper 不执行写操作
```

遇到的问题：

- Spring Boot 的 `/error` 二次分派被安全配置拦截，导致原始 400/403 被覆盖为 401；将 `/error` 加入安全白名单后恢复正确状态码。
- `ExpenseReport.version` 已添加 `@Version`，但最初未注册 `OptimisticLockerInnerInterceptor`，导致读取已有报销单后更新总额时报缺少原版本参数；补充拦截器后真实 CRUD 验收通过。

明日衔接：先完成修改草稿标题与说明的 `PUT /api/reports/{id}`，再进入提交、撤回、驳回后重提等报销单状态转换。
