# SpeedNet Backend

SpeedNet 后端服务，基于 JDK 25、Spring Boot 4 和 Maven 多模块架构。

## 默认模块

- `speednet-dependencies`：统一依赖版本管理
- `speednet-framework`：通用框架与 Spring Boot Starter
- `speednet-module-system`：系统管理能力
- `speednet-module-infra`：基础设施能力
- `speednet-server`：应用启动模块

仓库中还保留了会员、工作流、报表、支付、商城、IoT、IM 和 AI 等可选业务模块。它们默认未在根 `pom.xml` 中启用，可按需取消对应 `<module>` 注释并在 `speednet-server` 中加入依赖。

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

## 工程命名

- Maven groupId：`com.speednet`
- Java 根包名：`com.speednet`
- Spring 应用名：`speednet-server`
- 模块前缀：`speednet-`

## License

本项目保留根目录中的 MIT License。
