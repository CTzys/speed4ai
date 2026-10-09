# SpeedNet 客户前台后端

独立 Spring Boot 服务，直接使用现有 `speed_backend` 的 MySQL 数据库与 `member_user` 客户表，不调用管理后端接口。实现邮箱验证码注册、邮箱密码登录、Token 刷新、退出、当前客户资料；套餐与工单通过固定代理接口接入主后台，并复用会员会话。

## 环境

- Java 25、Maven 3.6.3+
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
| GET | `/custom-api/subscription/info` | 当前客户订阅摘要；Bearer Token |

统一响应为 `{ "code": 0, "data": ..., "msg": "success" }`。客户禁用状态每次受保护请求都会从 `member_user` 读取，因此现有管理后台禁用后，前台请求会被拒绝。

## 尚未实现

自助找回密码尚未实现。套餐、订单、订阅和工单已经接入主后台；部署时设置 `CUSTOM_BACKEND_URL` 并保持两端数据库与租户配置一致。工单代理为 `/custom-api/tickets/*`，支持受保护截图上传、读取与暂存删除；迁移和权限见 [客户工单说明](../speed_backend/docs/support-tickets.md)。

## 客户端订阅信息

`GET /custom-api/subscription/info`，请求头 `Authorization: Bearer <accessToken>`。
不接受客户端传入用户或租户 ID，按登录会话查询当前客户的订阅，响应禁止缓存。
需要同时部署客户后端和主后台（内部接口 `/app-api/subscription-products/info`）。

```json
{
  "code": 0,
  "data": {
    "expiryTime": 1794196800000,
    "planName": "月度套餐",
    "totalBytes": 107374182400,
    "remainingBytes": 85899345920,
    "usedBytes": 21474836480,
    "extraBytes": 0,
    "nodeLimit": 10,
    "nextResetTime": 1794196800000,
    "status": 1,
    "syncStatus": 2,
    "unlimited": false,
    "usagePercent": 20.0,
    "remainingDays": 30,
    "pendingOrderCount": 0
  },
  "msg": ""
}
```

返回订阅到期时间、套餐名称、套餐基础流量额度、剩余流量，以及客户首页概览字段。
流量单位为字节；剩余流量包含额外流量包，遵循订阅的仅下载或上传加下载计费方式，最低为 0。
不限流量订阅的 `totalBytes` 和 `remainingBytes` 均为 `null`。
没有当前订阅时 `data` 为 `null`；已到期订阅仍返回到期时间和账面流量，客户端应根据到期时间判断有效性。
`expiryTime` 为 Unix 毫秒时间戳（沿用主后台序列化配置）；流量为已采集的统计值。

| 新增字段 | 含义 |
| --- | --- |
| `usedBytes` | 本周期已用流量（按订阅计费方式统计，字节） |
| `extraBytes` | 剩余补充流量（字节）；到期后为 0 |
| `nodeLimit` | 节点上限 |
| `nextResetTime` | 下次流量重置时间，毫秒时间戳；无重置计划为 null |
| `status` | 0 待生效、1 生效中、2 已暂停、3 流量耗尽、4 已到期、5 已结束 |
| `syncStatus` | 0 待配置、1 配置中、2 已核对、3 配置失败 |
| `unlimited` | 是否不限流量 |
| `usagePercent` | 本周期使用百分比（0–100）；不限流量为 null |
| `remainingDays` | 距到期剩余天数，向上取整，已到期为 0 |
| `pendingOrderCount` | 当前用户待支付、权益处理中、权益待处理订单总数 |

剩余流量已包含补充流量，客户端不要再次加上 `extraBytes`。使用百分比按当前总额度（含补充流量）计算。
用户昵称、邮箱继续从 `/custom-api/member/me` 获取；复制订阅链接继续调用 `/custom-api/packages/link`。

## 节点信息与 Clash Verge 订阅

以下接口均使用 `Authorization: Bearer <accessToken>`，只查询当前登录客户的订阅：

| 方法 | 路径 | 返回 |
| --- | --- | --- |
| GET | `/custom-api/subscription/nodes` | JSON 节点列表，每项含 `id`、`name`、`protocol`、`server`、`port` |
| GET | `/custom-api/subscription/clash` | 原始 Clash/Mihomo YAML 配置，可保存为 `subscription.yaml` 后本地导入 |
| GET | `/custom-api/subscription/clash-link` | JSON `{ "code": 0, "data": { "path": "/app-api/subscription/feed/<tenant>/<subscription-token>?format=clash" }, "msg": "" }` |

客户端获取 `clash-link` 返回的 `data.path` 后，拼接对外提供主后台 `/app-api/` 的 HTTPS 域名，在 Clash Verge 的订阅界面导入这个完整 URL，即可自动更新。
订阅 URL 使用独立的订阅密钥，不需要登录访问令牌；每次下载仍校验账号启用状态、订阅有效性和节点配置状态。不要用短期 `accessToken` 拼接 URL。
客户域名若只代理 `/custom-api/`，需要另行配置 `/app-api/subscription/feed/` 的代理，或使用已暴露该路径的主后台域名。

下载配置接口返回 `application/yaml`，不是 JSON 包装；同时提供 `subscription-userinfo`（流量及到期信息）、`profile-update-interval: 1`（小时）和禁止缓存响应头。
不支持未开通、暂停、到期、已结束、流量耗尽或未同步成功的订阅。仅返回未释放且同步成功的节点。
节点列表不返回连接凭据；YAML 和订阅链接包含连接授权信息，应作为凭据保存。

当前转换覆盖已有 VMess、VLESS、Trojan（必须 TLS）节点，以及 TCP、WebSocket、gRPC 传输；Reality 和其他协议沿用现有入站限制，尚不支持。
配置含 `proxies`、手动选择代理组 `SpeedNet` 和基本规则：IPv4 回环及私有网段直连，其余流量走所选节点。
生成的是可导入的订阅配置文件，Clash Verge 的 JavaScript 扩展脚本不属于该接口。
规则现在由主后台数据库提供：用户专用规则优先，否则使用租户通用规则；未配置时采用基本规则。配置入口为「订阅管理 → Clash 规则」，保存后下次拉取自动生效。
旧订阅 URL 不带 `format` 参数时仍返回原来的 Base64 格式，保持已有客户端兼容性。
主后台与客户后端需要同时部署新版本。
