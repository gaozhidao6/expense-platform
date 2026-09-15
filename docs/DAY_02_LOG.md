# Day 02：实体、Mapper 与分页基础

日期：2026-08-19

## 今日目标

- [x] 理解 Entity、Mapper、Service、Controller 的分层职责
- [x] 根据数据库表创建基础 Entity
- [x] 创建 MyBatis-Plus Mapper
- [x] 配置 MySQL 分页插件
- [x] 完成后端编译验证
- [ ] 完成部门分页查询接口
- [ ] 完成用户分页查询和关键字搜索接口

## 今日完成内容

### 1. 创建 Entity

已创建：

- `department/entity/Department.java`
- `user/entity/SysUser.java`
- `role/entity/SysRole.java`

使用的主要注解：

| 注解 | 作用 |
|---|---|
| `@TableName` | 指定 Entity 对应的数据库表 |
| `@TableId(type = IdType.AUTO)` | 配置自增主键 |
| `@TableLogic` | 配置逻辑删除字段 |
| `@Data` | 为简单 Entity 生成常用方法 |
| `@Getter`、`@Setter` | 为用户 Entity 提供访问方法，避免自动生成敏感字段的 `toString()` |

### 2. 创建 Mapper

已创建：

- `department/mapper/DepartmentMapper.java`
- `user/mapper/SysUserMapper.java`
- `role/mapper/SysRoleMapper.java`

三个 Mapper 都继承了：

```java
BaseMapper<Entity>
```

因此可以直接使用 MyBatis-Plus 提供的基础 CRUD 方法，例如 `selectById`、`selectList`、`insert` 和 `updateById`。

### 3. 配置分页插件

已创建：

```text
config/MybatisPlusConfig.java
```

配置了：

```java
PaginationInnerInterceptor(DbType.MYSQL)
```

后续可以使用 MyBatis-Plus 的 `Page` 对象执行分页查询。

## 编译验证

执行命令：

```cmd
cd /d D:\shixi\expense-platform\backend
mvn test -DskipTests
```

结果：

```text
BUILD SUCCESS
编译 11 个源文件
```

## 今日理解

### Entity

Entity 表示数据库表在 Java 中的对象模型，负责字段映射，不负责处理业务流程。

### Mapper

Mapper 负责访问数据库。继承 `BaseMapper` 后，可以复用 MyBatis-Plus 的基础数据库操作。

### Service

Service 负责业务规则，例如权限、状态转换、金额计算和事务控制。今天尚未创建 Service。

### Controller

Controller 负责接收 HTTP 请求和返回响应。今天尚未创建 Controller。

## 今日问题与解决方式

| 问题 | 解决方式 |
|---|---|
| Entity 是否必须使用 `@Data` | 不必须；简单 Entity 可用 `@Data`，包含密码等敏感字段时使用 `@Getter`、`@Setter` 更谨慎 |
| 空包目录如何保留 | 使用 `.gitkeep` 占位，创建 Java 文件后可删除 |

## 今日验收

- [x] `Department` 能映射 `department` 表
- [x] `SysUser` 能映射 `sys_user` 表
- [x] `SysRole` 能映射 `sys_role` 表
- [x] 三个 Mapper 已创建并标注 `@Mapper`
- [x] MySQL 分页插件已配置
- [x] Maven 编译成功
- [ ] Swagger 中能查询部门分页数据
- [ ] Swagger 中能查询用户分页数据

## 今日复盘

### 今天学会的 3 个概念

1. MyBatis-Plus Entity 注解负责 Java 字段与数据库表字段的映射。
2. `BaseMapper` 提供通用 CRUD，可以减少基础 SQL 和 XML 配置。
3. 分页插件负责把 `Page` 查询转换为数据库对应的分页 SQL。

### 仍然需要理解的概念

- `Page` 和 `IPage` 的使用方式
- Service 如何组织分页查询和业务规则
- DTO 与 VO 为什么不能直接使用 Entity
- MyBatis-Plus 逻辑删除实际生成的 SQL

## 明日计划

- 创建 Department Service 和 Controller
- 实现 `GET /api/departments?page=1&size=10`
- 使用 Swagger 验证分页结果
- 再实现用户分页和关键字搜索
