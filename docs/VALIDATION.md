# 验证记录

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
| Docker Compose / CI | 本机没有 Docker；由 GitHub Actions 的独立任务构建并启动容器、验证 Nginx 代理登录 |

故障回归采用真实 MySQL 的提交、回滚和并发竞争，Redis 离线、对象存储删除失败和 RabbitMQ 发送失败通过故障注入模拟。没有把这些测试当作真实 Redis / RabbitMQ / MinIO 服务的网络、重启或吞吐量测试。

测试创建的数据使用独立前缀并在结束后清理。浏览器流程保留标题为“差旅报销演示-时间戳”的报销单，便于复查；因此应仅在开发库运行。

本机 Chromium 下载超时后使用已安装 Chrome 运行 Playwright，视频编码使用 Playwright 自带 FFmpeg。CI 使用 Playwright 安装的 Chromium；可从仓库 Actions 页面查看在线结果。
