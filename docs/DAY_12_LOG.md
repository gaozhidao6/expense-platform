# Day 12：报销单导出与报表完善

日期：2026-09-04

## 今日目标

在报销单分页和统计功能基础上，增加按权限导出报销单数据，并完善统计报表的使用体验。

## 已建立框架

- `ExpenseReportExportQueryDTO`：接收关键字、状态和时间范围。
- `ExpenseReportService.exportReports()`：导出 Service 入口及中文 TODO。
- `GET /api/reports/export`：导出 Controller 接口框架。
- `ExpenseReportExportRowVO`：CSV 导出行对象框架。
- `ExpenseReportCsvExporter`：CSV 文件生成器框架。

## 计划任务

1. 复用报销单列表的角色数据权限。
2. 复用关键字、状态和时间范围筛选条件。
3. 确定 CSV 或 Excel 导出格式。
4. 设置文件响应头、文件名和 `Content-Type`。
5. 控制导出字段，不导出密码、删除标记等内部字段。
6. 前端增加导出按钮和导出条件确认。
7. 优化统计卡片和金额格式化。

## 当前状态

实现状态：已建立代码框架，导出文件生成逻辑待实现。
测试状态：待执行。
