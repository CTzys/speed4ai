# Flyway 维护

在 `speed_backend` 目录执行：

```bash
bash script/flyway/flyway.sh check
bash script/flyway/flyway.sh info
bash script/flyway/flyway.sh repair
bash script/flyway/flyway.sh validate
bash script/flyway/flyway.sh consolidate-v8
```

脚本从源码的 `application.yaml` 和活动 profile 对应的 `application-<profile>.yaml` 读取连接配置，默认使用项目配置的 `local`。第二个参数可指定 profile，例如 `bash script/flyway/flyway.sh repair dev`。支持逗号分隔的多个 profile，后者覆盖前者，以及环境变量覆盖和 Spring `${...}` 占位符。优先使用 `spring.flyway.url/user/password`，否则使用动态数据源的 primary（默认 master）连接。

使用已打包的 `speednet-server/target/speednet-server.jar` 中的依赖，无需安装 Flyway CLI 或启动后端。可通过 `SPEEDNET_SERVER_JAR` 指定其他已打包的后端 JAR，通过 `JAVA_HOME` 指定 JDK。`check` 只检查配置，不连接数据库；`repair` 修改历史并立即运行 `validate`，不会执行迁移 SQL。

原 V8～V16 合并场景：仅适用于原 V8～V16 全部成功执行的数据库。先停止后端，备份 `flyway_schema_history`，删除版本 9～16 的历史记录（保留版本 8），再运行 `repair`。不要删除 V8 或业务表。若尚未删除旧记录，`repair` 会将缺失迁移标记为 deleted，不能实现只保留 V1～V8 的目标。

也可用 `consolidate-v8` 一次完成：先核对 V1～V16 全部成功，再创建带时间戳的历史备份表，在事务中备份并删除 V9～V16，最后 repair 和 validate。该操作不会修改业务表，且拒绝重复合并或处理部分执行的历史。完成后必须 clean 打包后再启动后端，避免旧 JAR/target/classes 中残留原迁移脚本。

此工具加载项目现有普通 YAML 配置，不加载 Spring Config Import、远程配置中心或按 `spring.config.activate.on-profile` 条件筛选文档。
