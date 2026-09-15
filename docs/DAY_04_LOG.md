# Day 04：登录与 JWT 认证

日期：2026-08-27

## 今日完成

- 登录请求 DTO 与响应 VO
- BCrypt `PasswordEncoder` Bean
- JWT 生成、解析、签名和过期校验
- 用户角色联表查询
- JWT 认证过滤器
- Spring Security 过滤器链配置
- 明确 401 和 403 响应
- 登录 Service 与 Controller

## 自动化测试

- `AuthServiceTests`：登录成功、用户不存在、密码错误、用户禁用
- `JwtTokenProviderTests`：Token 生成解析、篡改拒绝、过期拒绝
- `AuthenticationFlowTests`：公开接口、登录参数校验、401、403、合法 Token、无效 Token

执行 `mvn test` 结果：

```text
Tests run: 17
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## 真实环境验收（2026-08-28）

- [x] MySQL 3306 端口可连接
- [x] Flyway V2 创建本地 `admin` 和 `employee` 测试用户
- [x] `admin / Admin123!` 登录成功并返回 JWT
- [x] JWT 携带访问 `/api/users` 返回 `200`
- [x] 无 Token 访问 `/api/users` 返回 `401`
- [x] 登录响应包含用户名、角色和过期时间
- [x] 登录响应不包含密码哈希

数据库角色确认：

```text
admin    -> ADMIN
employee -> EMPLOYEE
```

## Day 04 结论

登录、JWT 认证、角色加载、401/403 自动化测试和真实登录冒烟测试均已完成。

## 明日计划

- 完成 RBAC 角色权限配置
- 验证员工和管理员的 200/403 边界
- 前端实现登录、Token 保存和 Axios 请求拦截
