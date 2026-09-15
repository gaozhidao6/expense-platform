# Day 5 项目面试问答记录

日期：2026-08-28

## 1. 请介绍项目架构和登录链路

**回答要点：** 前端提交登录信息，Controller 接收 DTO，Service 处理业务并调用 Mapper，最后返回 VO 和 JWT。

**补充：** DTO 是 Controller 通过 `@RequestBody` 绑定请求 JSON 得到的，不是前端直接传入 DTO。JWT 由 `JwtTokenProvider` 生成，VO 只负责组织响应数据。

## 2. 为什么使用 DTO 和 VO，而不是直接使用 Entity

**回答要点：** DTO/VO 可以限制请求和返回的数据，避免暴露密码哈希等敏感字段。

**补充：** BCrypt 哈希带随机盐，同一密码多次编码通常不同，不能用 `encode()` 后直接比较，应使用：

```java
passwordEncoder.matches(rawPassword, passwordHash);
```

## 3. 如何实现用户分页和关键字查询

**回答要点：** 使用 MyBatis-Plus 的 `Page` 分页。

**补充：** `Page` 负责页码、每页数量、总数和记录；`LambdaQueryWrapper` 负责条件和排序，不负责分页。关键字可以对用户名、姓名、工号做 OR 查询；角色查询才需要联表。

## 4. BCrypt 和 JWT 有什么区别

**标准答案：** BCrypt 是不可逆的密码哈希，用于登录时验证密码；JWT 是登录成功后的身份凭证，用于携带用户身份、角色和过期时间。BCrypt 不能替代 JWT，JWT 也不能替代密码校验。

## 5. JwtAuthenticationFilter 的执行流程

**标准答案：** 从 `HttpServletRequest` 的 `Authorization` 请求头读取 Token，判断是否以 `Bearer ` 开头，提取并验证 JWT，解析用户和角色，创建 `Authentication`，放入 `SecurityContextHolder`，然后继续请求链。Token 无效时返回 401；没有 Token 访问公开接口时应放行。

## 6. 401 和 403 的区别

**标准答案：** 401 表示没有有效身份，例如未登录、Token 缺失、过期或无效；403 表示身份有效但权限不足。Spring Security 中通常由 `AuthenticationEntryPoint` 处理 401，由 `AccessDeniedHandler` 处理 403。

## 7. 为什么需要方法级权限

**回答要点：** 可以限制每个角色的权限范围。`@PreAuthorize("hasRole('ADMIN')")` 用于限制只有 ADMIN 角色的用户执行方法。

**补充：** `hasRole("ADMIN")` 默认匹配 `ROLE_ADMIN`，因此创建权限对象时通常写：

```java
new SimpleGrantedAuthority("ROLE_" + roleCode);
```

## 8. 角色如何从数据库进入 Spring Security

**标准答案：** 根据用户 ID 查询 `sys_user_role` 和 `sys_role`，得到角色编码如 `ADMIN`；转换成 `ROLE_ADMIN`，封装为 `GrantedAuthority`；再放入 `Authentication`，最终保存到 `SecurityContext`，供 `@PreAuthorize` 判断。

## 9. 为什么分页接口返回 VO，关键字查询如何避免异常

**回答要点：** VO 用于限定返回字段，避免暴露 `passwordHash`、`deleted` 等内部字段；关键字查询前先判断是否为空，再 `trim()`。

```java
if (StringUtils.hasText(query.getKeyword())) {
    String keyword = query.getKeyword().trim();
}
```

**补充：** MyBatis-Plus 参数绑定会使用预编译参数，避免手动拼接 SQL，降低 SQL 注入风险。

## 10. 项目做了哪些测试，单元测试和 MockMvc 有什么区别

**标准答案：** 测试登录成功和失败、JWT 解析、分页关键字查询、角色权限、参数校验等。

- Service 单元测试：重点验证业务逻辑，通常 Mock Mapper、PasswordEncoder 和 JWT 工具类。
- Controller/MockMvc 测试：重点验证 HTTP 路径、请求参数、状态码、JSON 响应和权限拦截。

当前测试结果：`Tests run: 17, Failures: 0, Errors: 0, BUILD SUCCESS`。

## 复习重点

1. DTO 是请求边界对象，VO 是响应边界对象，Entity 不应直接暴露。
2. `passwordEncoder.matches()` 才是 BCrypt 正确校验方式。
3. `Page` 负责分页，`LambdaQueryWrapper` 负责查询条件。
4. JWT Filter 验证成功后必须写入 `SecurityContext`。
5. 401 是身份问题，403 是权限问题。
6. `hasRole("ADMIN")` 对应 `ROLE_ADMIN`。
