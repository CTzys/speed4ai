# SpeedNet 客户前台后端

独立 Spring Boot 服务，直接使用现有 `speed_backend` 的 MySQL 数据库与 `member_user` 客户表，不调用管理后端接口。当前实现邮箱验证码注册、邮箱密码登录、Token 刷新、退出、当前客户资料。

## 环境

- Java 21、Maven 3.6.3+
- MySQL 数据库中已执行现有 `speed_backend` 的客户表迁移，尤其是 `V3__init_member_tables.sql` 与 `V5__member_email_registration.sql`
- `CUSTOM_DB_URL`、`CUSTOM_DB_USER`、`CUSTOM_DB_PASSWORD`：与现有后端相同的数据库
- `CUSTOM_TENANT_ID`：现有后台使用的租户 ID；默认 `1`
- `CUSTOM_MAIL_FROM`、`CUSTOM_SMTP_HOST`、`CUSTOM_SMTP_PORT`、`CUSTOM_SMTP_USER`、`CUSTOM_SMTP_PASSWORD`：生产邮件配置

测试环境运行（默认使用 `test` profile）：

```bash
mvn spring-boot:run
```

`application-test.yaml` 导入 `application-local.yaml` 中的数据库等参数。测试环境发送验证码时不连接 SMTP，而是在接口响应中返回验证码；注册页用弹窗展示并自动填入。当前默认 profile 为 `test`，仅用于测试。正式部署必须设置 `CUSTOM_PROFILE=prod`，并通过环境变量提供数据库密码和 SMTP 配置。

打包并运行可执行 JAR：

```bash
mvn clean package
java -jar target/speed-custom-backend.jar
```

默认监听 `48081`。新项目使用自己的 `custom_flyway_schema_history`，只迁移 `custom_` 表，不改现有后端的 Flyway 历史。

`test` 和 `local` profile 可在 `CUSTOM_DEV_RETURN_CODE=true` 时回显验证码；`prod` profile 即使误设此变量也不会回显。测试环境不要对公网开放注册入口。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/custom-api/auth/email-code` | 发送注册验证码；body `{ "email": "..." }` |
| POST | `/custom-api/auth/register` | 注册并登录；body `{ "email": "...", "code": "...", "password": "..." }` |
| POST | `/custom-api/auth/login` | 登录；body `{ "email": "...", "password": "..." }` |
| POST | `/custom-api/auth/refresh` | 刷新；body `{ "refreshToken": "..." }` |
| POST | `/custom-api/auth/logout` | 退出；Bearer Token |
| GET | `/custom-api/member/me` | 当前客户；Bearer Token |

统一响应为 `{ "code": 0, "data": ..., "msg": "success" }`。客户禁用状态每次受保护请求都会从 `member_user` 读取，因此现有管理后台禁用后，前台请求会被拒绝。

## 尚未实现

套餐、订单、订阅及自助找回密码将在后续阶段开发。当前仪表盘只显示真实的客户基础资料，其余模块明确显示尚未开通。
