# 验证记录

## 2026-10-06：费用限额与超限审批

- Windows / JDK 17 / MySQL 8：`mvn -B verify`，145 项测试，0 failure / 0 error，成功打包。
- 新增 9 项真实 MySQL + HTTP 回归：超限原因、等额边界、规则快照、停用规则、晚期失败回滚、管理员权限、非法类型与长度、提交时重查最新规则、CNY 限制。Redis 故障通过注入模拟。
- AI 确认与提交共用报销单行锁；对应原有 `InvoiceSuggestionServiceTests` 7 项在恢复严格 Mock 检查后单独通过。
- `npm test`、`npm run build` 通过；`npm run test:e2e` 使用本机 Chrome，3 项浏览器测试全部通过。
- 新浏览器流程覆盖管理员改限额、员工填写超限原因、后端拒绝越权、审批人查看提交限额快照及驳回后展示新规则。截图见 [超限审批演示](EXPENSE_LIMITS.md)。
- 浏览器测试用报销单标题 `超限流程演示-时间戳`，本轮演示结束后驳回；交通规则在测试结束时恢复。原有完整报销演示仍会保留一条完成单。
- 本轮不涉及预算额度、日累计限额、拆单合并或真实银行转账。在线 CI 状态以对应提交的 GitHub Actions 结果为准。

## 2026-10-03 至 2026-10-04：基础与可靠性验证

本地验证环境：Windows、JDK 17、MySQL 8、Node.js 22；测试于 2026-10-03 至 2026-10-04 执行。

| 检查 | 结果与范围 |
| --- | --- |
| Maven tests | 136 项，0 failure / 0 error；真实 MySQL 集成测试 |
| 后端打包 | Spring Boot 可执行 JAR 成功生成并实际启动 |
| 前端 npm test | 源码静态检查通过；不当作浏览器测试 |
| 前端 npm run build | TypeScript 检查与 Vite 生产构建通过；仍有 bundle 体积提示 |
| Playwright | 2 项通过：创建、附件、Mock 建议确认、提交、经理审批、财务审核、付款、审计；员工前端路由与后端 API 越权被拒绝 |
| 截图 / 视频 | 由通过的真实浏览器测试录制；无真实发票或个人资料 |
| Windows 启动脚本 | 实际完成打包、安装依赖、启动健康检查与前端代理登录；Ctrl+C 后两个进程停止 |
| Docker Compose / CI | 首轮 GitHub Actions 全部通过：verify 与 containers 两个任务成功，实际构建 / 启动容器并验证 Nginx 代理登录 |

故障回归采用真实 MySQL 的提交、回滚和并发竞争，Redis 离线、对象存储删除失败和 RabbitMQ 发送失败通过故障注入模拟。没有把这些测试当作真实 Redis / RabbitMQ / MinIO 服务的网络、重启或吞吐量测试。

测试创建的数据使用独立前缀并在结束后清理。浏览器流程保留标题为“差旅报销演示-时间戳”的报销单，便于复查；因此应仅在开发库运行。

本机 Chromium 下载超时后使用已安装 Chrome 运行 Playwright，视频编码使用 Playwright 自带 FFmpeg。CI 使用 Playwright 安装的 Chromium；可从仓库 Actions 页面查看在线结果。

在线证据：[2026-10-04 首轮 CI](https://github.com/gaozhidao6/expense-platform/actions/runs/37135557208)，对应代码提交 `90d6c0a`。后续记录验证结果的提交仅调整文档。
