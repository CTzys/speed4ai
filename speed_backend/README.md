# SpeedNet Backend

SpeedNet 后端服务，基于 JDK 25、Spring Boot 4 和 Maven 多模块架构。

## 默认模块

- `speednet-dependencies`：统一依赖版本管理
- `speednet-framework`：通用框架与 Spring Boot Starter
- `speednet-module-system`：系统管理能力
- `speednet-module-infra`：基础设施能力
- `speednet-server`：应用启动模块

会员、Xray、订阅、工单、支付及商城模块已默认启用。商城包括商品、营销、交易和统计四个模块。工作流、报表、IoT、IM 和 AI 等可选模块仍默认关闭，可按需在根 `pom.xml` 和 `speednet-server/pom.xml` 中启用。

## 技术栈

- Java 25
- Spring Boot 4.1
- MyBatis Plus
- MySQL / PostgreSQL / Oracle / SQL Server 等数据库脚本
- Redis / Redisson
- Spring Security

## 启动

1. 安装 JDK 25 和 Maven。
2. 修改 `speednet-server/src/main/resources/application-local.yaml` 中的数据库、Redis 等连接信息。数据库账号具备建库权限时，MySQL 数据库会自动创建。
3. 执行：

   ```shell
   mvn clean package -DskipTests
   java -jar speednet-server/target/speednet-server.jar
   ```

主启动类为 `com.speednet.server.SpeednetServerApplication`。

本地默认数据库连接为 `127.0.0.1:3306/speednet-vue-pro`，使用专用账号 `speednet`。Redis 默认连接 `127.0.0.1:6379` 的 DB 0，无密码。

## 数据库版本迁移

项目使用 Flyway 管理数据库版本。首次连接空数据库时，会自动执行 `speednet-server/src/main/resources/db/migration/V1__init_speednet_schema.sql`，创建表结构和初始数据。

后续数据库变更请添加新的增量脚本，不要修改已经在环境中执行过的迁移文件，例如：

```text
V2__add_stock_table.sql
V3__add_order_index.sql
```

已有非空数据库首次接入时会自动建立 `flyway_schema_history` 并标记为 V1，不会重新执行初始化脚本。

原 V8～V16 已按版本顺序合并为 `V8__xray_and_subscription_management.sql`，适用于尚未执行原 V8～V16 的数据库。已执行其中任一旧迁移的环境应继续使用原迁移文件，避免 Flyway 校验失败或重复执行 SQL。

## 工程命名

- Maven groupId：`com.speednet`
- Java 根包名：`com.speednet`
- Spring 应用名：`speednet-server`
- 模块前缀：`speednet-`

## Xray 入站管理

在“Xray 管理 → 服务器管理”配置目标服务器的面板协议、端口、基础路径及 API Token，然后进入“入站管理”或点击服务器行的“入站”。基础路径填写面板 Web Base Path（例如 `/secret/`），不要包含 `/panel/api`。Token 由支持 Bearer 认证的 3x-ui 面板生成；不支持此认证的旧版本需要先升级。

安装时读取实际面板 TCP 端口，并为已启用的 UFW/firewalld 添加放行规则；firewalld 同时写入运行时和永久规则。未知防火墙或放行失败会在安装日志中提示，不清空规则、不启用或关闭防火墙；云安全组仍需自行配置。

安装成功后会自动读取并保存面板的 HTTP/HTTPS、端口和基础路径；读取失败仅记录警告，不影响安装成功状态，也不会清空原有配置。未配置 API Token 时会通过支持命名 Token 的官方 CLI 生成 SpeedNet 专用 Token，并加密保存；已有 Token 会保留。旧面板不支持该命令时记录警告，需手动填写 Token。如果面板经由反向代理访问，请手动填写代理对外的连接信息。

该功能由后端调用 3x-ui 的 `/panel/api/inbounds/list`、`get/:id`、`add`、`update/:id`、`del/:id` HTTP 接口，入站配置保存于远端面板。编辑使用远端最新配置合并可编辑字段，保留流量计数、tag 及其他面板字段。API Token 不返回浏览器，HTTPS 使用正常证书校验；请求失败后请刷新列表确认远端状态，再决定是否重试。

表单支持备注、启用、协议、监听地址、端口、流量额度、到期时间，以及协议相关的 `settings`、`streamSettings`、`sniffing` JSON 对象。协议和客户端配置必须匹配；修改协议时需相应调整 JSON 配置。删除会同时删除面板中的关联客户端。

启动后 Flyway 自动执行 `V8__xray_and_subscription_management.sql` 添加菜单与权限。普通角色需要分配服务器查询权限 `xray:server:query`，以及 `xray:inbound:query/create/update/delete` 中所需的权限。

## License

本项目保留根目录中的 MIT License。

安装时会从官方安装输出捕获面板登录用户名和密码，按现有密钥配置加密保存，安装日志对账号密码脱敏。具有服务器编辑权限的管理员可在“服务器管理 → 编辑 → 查看账号密码”查看；普通查询及列表接口不返回凭据。面板凭据字段已包含在 `V8__xray_and_subscription_management.sql` 合并迁移中。

已安装但未记录凭据的服务器，可点击“从服务器读取并填入”，尝试读取新版官方安装器的 `/etc/x-ui/install-result.env`（root 所有且权限为 600）。该文件记录的是安装时的账号，面板修改密码后可能失效；旧版本无此文件时，通过服务器 `x-ui` 菜单重置登录信息，再在编辑窗口手动记录。手动记录只保存凭据，不修改远端账号。

## 用户订阅管理

独立的“订阅管理 → 订阅列表”支持后台创建、节点分配、客户端认证生成、订阅链接、流量采集、延长/重置/结束与授权撤销。启动新版后端自动执行数据库迁移，重新登录获取菜单。订阅扫描已接入“基础设施 → 定时任务”，默认每分钟执行。使用步骤、支持范围及联调说明见 [订阅管理文档](docs/subscription-management.md)。

## 商城管理

商城及支付模块参与默认构建，后台套餐商品和订单统一在“订阅管理”，会员统计在“会员中心”，保留“支付管理”。启动时 V9 合并迁移创建缺失的 63 张业务表，保留已有表及数据，不导入演示商品、订单或支付渠道。迁移为默认租户（1）初始化保守的交易配置；其他租户在“商城 → 交易设置”保存配置后使用。

首次使用请配置商品分类、品牌、属性、运费模板，再创建商品。订单、售后、优惠券、活动、分销及统计接口随服务一起加载。普通角色需分配相应商城菜单权限。

真实支付需在“支付管理 → 应用信息”创建应用标识 `mall`，配置其支付/退款业务回调及自己的微信或支付宝渠道。钱包充值使用 `wallet` 应用。将 `speednet.pay` 的渠道回调地址替换为可公网访问的本服务地址；现有配置中的示例域名不能用于实际收款。快递查询、地图、短信和小程序相关能力需配置自己的服务账号。

商城及支付定时任务沿用“基础设施 → 定时任务”管理，根据业务开启支付同步/通知、订单过期关闭/自动收货、优惠券过期及统计任务。初始化任务默认暂停，接入支付并确认配置后再开启。

## 订阅商品与客户前台

「订阅管理 → 套餐商品 / 套餐订单」配置订阅专用售价、流量和节点。客户前台可新购、续订、升级折抵、补充流量和查询订单，始终只持有一份当前订阅。启用配置、数据库迁移、支付应用及升级计价规则见 [商品系统说明](docs/product-system-design.md)。需要同时部署主后台、客户后台及两套前台。

未提交的原 V9～V19 已合并为 `V9__subscription_commerce_and_menu_cleanup.sql`。已执行原 V9～V19 的数据库使用 `consolidate-v9` 备份和同步历史，不能重新执行合并 SQL；部分执行的环境须先使用原脚本补齐至 V19。

## 客户工单

新增“工单管理”后台及客户中心“工单系统”，支持提交与回复、截图、领取转交、内部备注、订单/订阅关联、未读及操作记录。部署时运行 Flyway V10 并给客服分配工单权限；接入与验证见 [客户工单说明](docs/support-tickets.md)。

Clash 订阅支持数据库通用规则与客户专用规则，配置与部署见 [Clash 路由规则](docs/clash-rules.md)。

客户工单与 Clash 规则新增迁移已合并为 V10；已执行原 V10/V11 的环境使用 `consolidate-v10` 备份并同步历史，详见 [Flyway 维护](script/flyway/README.md)。
