# Day 05：RBAC 权限与前端登录

日期：2026-08-28

## 今日目标

- 理解认证与授权的区别
- 基于角色保护后端接口
- 让前端完成登录、保存 Token 和自动携带 Token
- 验证员工、经理、财务、管理员的访问边界

## 今日任务

### 1. 完善用户角色加载

登录成功时从 `sys_user_role` 和 `sys_role` 查询角色编码，并写入 JWT：

```text
EMPLOYEE / MANAGER / FINANCE / ADMIN
```

角色必须以后端数据库中的关联关系为准，不能信任前端提交的角色。

### 2. 配置方法级权限

启用 `@PreAuthorize`，并在接口上声明权限：

```java
@PreAuthorize("hasRole('ADMIN')")
```

Spring Security 中的角色通常使用 `ROLE_` 前缀：

```text
JWT 角色：ADMIN
GrantedAuthority：ROLE_ADMIN
```

### 3. 设计接口权限矩阵

| 角色 | 用户/部门管理 | 查看自己的报销 | 审批报销 | 财务付款 |
|---|---:|---:|---:|---:|
| EMPLOYEE | 否 | 是 | 否 | 否 |
| MANAGER | 否 | 是 | 是 | 否 |
| FINANCE | 否 | 是 | 否 | 是 |
| ADMIN | 是 | 是 | 按配置 | 按配置 |

### 4. 前端登录流程

- 创建登录页面和表单校验
- 调用 `POST /api/auth/login`
- 保存 Token 和用户信息
- Axios 请求拦截器自动添加 `Authorization: Bearer <token>`
- 响应遇到 `401` 时清除登录状态并跳转登录页
- 遇到 `403` 时提示无权限

Token 不应写入日志，也不应直接显示在页面上。

### 5. 后端验收

准备至少两个测试用户：

```text
employee / EMPLOYEE
admin    / ADMIN
```

验证：

```text
无 Token 访问受保护接口       -> 401
EMPLOYEE 访问 ADMIN 接口      -> 403
ADMIN 访问 ADMIN 接口         -> 200
错误或过期 Token              -> 401
```

## 推荐实现顺序

1. 给测试用户生成 BCrypt 密码哈希
2. 插入测试用户和用户角色关联数据
3. 在需要保护的 Controller 方法增加 `@PreAuthorize`
4. 使用 MockMvc 验证各角色权限
5. 创建前端登录页面
6. 添加 Axios 请求和响应拦截器
7. 手工验证登录、刷新页面、退出登录

## 今日复盘问题

- 为什么角色必须由后端决定，不能信任前端传来的角色？
- `hasRole('ADMIN')` 和 `hasAuthority('ROLE_ADMIN')` 有什么区别？
- 为什么未登录是 401，角色不足是 403？
- Token 放在浏览器哪里有什么安全取舍？
- 页面刷新后如何恢复登录状态？

## 今日验收标准

- [ ] 不同角色访问接口得到正确的 200/403
- [ ] 前端登录成功后能访问用户接口
- [ ] Axios 自动携带 Bearer Token
- [ ] Token 失效后前端回到登录页
- [ ] 前端不展示或记录密码和 Token
