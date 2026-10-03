# 参与开发

先按 README 启动本地环境。后端使用 Java 17 / Maven，前端使用 Node.js 22 / npm；表结构变更增加新的 Flyway 迁移，不修改已经运行过的迁移。

提交前执行：

```powershell
mvn -B -f backend/pom.xml verify
cd frontend
npm ci
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

后端集成测试需要独立的 MySQL 8 开发数据库，会自动迁移并清理本次测试创建的数据。E2E 需要启动后端，会留下明确标记的演示报销数据；不要连接生产数据库。

修复缺陷时添加能复现缺陷的测试。PR 请说明触发条件、改变后的行为和验证结果；涉及数据库、权限或事务时说明相应影响。Issue 请给出复现步骤和脱敏日志。

不要提交 `.env`、JWT、API Key、真实发票、个人信息、构建产物和运行目录。仓库采用 MIT 许可证，提交的代码须有相应使用权。
