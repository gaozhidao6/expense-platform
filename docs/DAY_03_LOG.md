# Day 03：DTO、VO 与用户分页查询

日期：2026-08-20

## 今日目标

- 理解 Entity、DTO、VO 的职责边界
- 创建用户分页查询 DTO 和用户 VO
- 实现用户分页查询与关键字搜索
- 使用 Bean Validation 校验分页参数
- 确保敏感字段不会出现在接口响应中

## 完成内容

- 创建 `UserPageQueryDTO`：支持 `page`、`size`、`keyword`
- 校验 `page >= 1`、`1 <= size <= 100`
- 创建 `UserVO`，不包含 `passwordHash` 和 `deleted`
- `SysUserController` 提供 `GET /api/users`
- `SysUserService` 使用 `Page` 和 `LambdaQueryWrapper` 查询
- 关键字匹配 `username`、`realName`、`employeeNo`
- 未传关键字时查询全部未删除用户
- 查询结果按 `createdAt` 倒序返回
- 使用 `entityPage.convert(this::toVO)` 完成 Entity 到 VO 的转换

## 请求示例

```http
GET /api/users?page=1&size=10&keyword=test
```

## 接口验证

| 请求 | 结果 |
|---|---|
| `GET /api/users?page=1&size=10` | `200 OK` |
| `GET /api/users?page=1&size=10&keyword=test` | `200 OK` |
| `GET /api/users?page=0&size=101` | `400 Bad Request` |
| 响应包含 `passwordHash` | 不包含 |

当前数据库没有用户数据，成功请求返回空 `records` 列表属于正常结果。

## 验收结论

- [x] DTO 创建与参数校验
- [x] VO 创建与敏感字段隔离
- [x] 用户分页查询接口
- [x] 用户名、姓名、工号关键字查询
- [x] 无关键字查询
- [x] Entity 到 VO 转换
- [x] 真实 HTTP 请求验证
- [x] `mvn test` 编译测试通过
- [ ] MockMvc 集成测试待后续补充

## 明日计划

- 实现登录接口和 BCrypt 密码校验
- 基于 JWT 接入 Spring Security 认证
- 验证未登录 401、无权限 403 和合法 Token 访问流程
