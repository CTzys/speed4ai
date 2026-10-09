import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.flywaydb.core.Flyway;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/** Standalone maintenance: loads source configuration without starting the application. */
public class FlywayMaintenance {
    public static void main(String[] args) throws Exception {
        String action = args[1];
        if (!List.of("check", "info", "repair", "validate", "consolidate-v8", "consolidate-v9", "consolidate-v10", "migrate").contains(action)) {
            throw new IllegalArgumentException("用法：bash script/flyway/flyway.sh [check|info|repair|validate|migrate|consolidate-v8|consolidate-v9|consolidate-v10] [profile]");
        }
        Path resources = Path.of(args[0], "speednet-server", "src", "main", "resources");
        StandardEnvironment env = new StandardEnvironment();
        load(env, resources.resolve("application.yaml"));
        String profiles = args[2].isBlank()
                ? env.getProperty("spring.profiles.active", "local") : args[2];
        for (String profile : profiles.split(",")) {
            profile = profile.trim();
            if (!profile.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("无效 profile");
            }
            load(env, resources.resolve("application-" + profile + ".yaml"));
        }
        String primary = env.getProperty("spring.datasource.dynamic.primary", "master");
        String prefix = "spring.datasource.dynamic.datasource." + primary + ".";
        String url = env.getProperty("spring.flyway.url");
        boolean dedicated = url != null;
        if (!dedicated) url = env.getProperty(prefix + "url", env.getProperty("spring.datasource.url"));
        String user = env.getProperty(dedicated ? "spring.flyway.user" : prefix + "username",
                env.getProperty("spring.datasource.username"));
        String password = env.getProperty(dedicated ? "spring.flyway.password" : prefix + "password",
                env.getProperty("spring.datasource.password", ""));
        if (url == null || user == null || url.contains("${") || password.contains("${")) {
            throw new IllegalStateException("数据库配置缺失或包含未解析的占位符");
        }
        String locations = env.getProperty("spring.flyway.locations", "classpath:db/migration");
        String[] resolved = locations.split(",");
        for (int i = 0; i < resolved.length; i++) {
            String location = resolved[i].trim();
            resolved[i] = location.startsWith("classpath:")
                    ? "filesystem:" + resources.resolve(location.substring("classpath:".length()))
                    : location;
        }
        System.out.println("已加载 profile：" + profiles + "；数据库配置及迁移目录解析成功（不输出连接凭据）。");
        if (action.equals("check")) return;
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .locations(resolved)
                .encoding(env.getProperty("spring.flyway.encoding", "UTF-8"))
                .table(env.getProperty("spring.flyway.table", "flyway_schema_history"))
                .cleanDisabled(true).load();
        switch (action) {
            case "consolidate-v8" -> {
                consolidate(flyway, resolved, 8, 16, "V8__xray_and_subscription_management.sql");
                flyway.repair();
                flyway.validate();
                System.out.println("迁移历史已合并为 V1～V8，校验通过；业务表和数据未修改。");
            }
            case "consolidate-v9" -> {
                consolidate(flyway, resolved, 9, 19, "V9__subscription_commerce_and_menu_cleanup.sql");
                flyway.repair();
                flyway.validate();
                System.out.println("迁移历史已合并为 V1～V9，校验通过；业务表和数据未重复迁移。");
            }
            case "consolidate-v10" -> {
                consolidate(flyway, resolved, 10, 11, "V10__customer_support_and_clash_rules.sql");
                flyway.repair();
                flyway.validate();
                System.out.println("迁移历史已合并为 V1～V10，校验通过；业务表和数据未重复迁移。");
            }
            case "migrate" -> flyway.migrate();
            case "repair" -> { flyway.repair(); flyway.validate(); }
            case "validate" -> flyway.validate();
            case "info" -> {
                for (var migration : flyway.info().all()) {
                    System.out.printf("%s  %s  %s%n", migration.getVersion(),
                            migration.getDescription(), migration.getState());
                }
            }
        }
    }

    private static void consolidate(Flyway flyway, String[] locations, int retained, int last, String filename) throws Exception {
        String table = flyway.getConfiguration().getTable();
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("不支持的历史表名称");
        if (java.util.Arrays.stream(locations).noneMatch(location -> location.startsWith("filesystem:")
                && java.nio.file.Files.isRegularFile(Path.of(location.substring("filesystem:".length()),
                    filename)))) {
            throw new IllegalStateException("未找到合并后的迁移文件：" + filename);
        }
        String backup = table + "_backup_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            checkHistory(connection, table, false, last);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE `" + backup + "` LIKE `" + table + "`");
            }
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                checkHistory(connection, table, true, last);
                int copied = statement.executeUpdate("INSERT INTO `" + backup + "` SELECT * FROM `" + table + "`");
                if (copied != last) throw new IllegalStateException("备份记录数异常");
                String versions = java.util.stream.IntStream.rangeClosed(retained + 1, last)
                        .mapToObj(version -> "'" + version + "'")
                        .collect(java.util.stream.Collectors.joining(","));
                int deleted = statement.executeUpdate("DELETE FROM `" + table
                        + "` WHERE version IN (" + versions + ") AND success=1");
                if (deleted != last - retained) throw new IllegalStateException("删除记录数异常");
                try (var update = connection.prepareStatement("UPDATE `" + table
                        + "` SET script=? WHERE version=? AND success=1")) {
                    update.setString(1, filename);
                    update.setString(2, Integer.toString(retained));
                    if (update.executeUpdate() != 1) throw new IllegalStateException("保留版本记录异常");
                }
                connection.commit();
                System.out.println("已备份 " + copied + " 条历史到 " + backup + "，删除 " + deleted + " 条被合并的版本记录。");
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void checkHistory(Connection connection, String table, boolean lock, int last) throws Exception {
        List<String> versions = new ArrayList<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT version, success, type FROM `" + table
                     + "` ORDER BY installed_rank" + (lock ? " FOR UPDATE" : ""))) {
            while (rows.next()) {
                if (!rows.getBoolean("success") || !"SQL".equals(rows.getString("type"))) {
                    throw new IllegalStateException("历史含失败或非 SQL 迁移，停止合并");
                }
                versions.add(rows.getString("version"));
            }
        }
        List<String> expected = java.util.stream.IntStream.rangeClosed(1, last)
                .mapToObj(Integer::toString).toList();
        if (!versions.equals(expected)) throw new IllegalStateException("只支持完整成功的 V1～V" + last + "，当前：" + versions);
    }

    private static void load(StandardEnvironment env, Path path) throws Exception {
        if (!path.toFile().isFile()) throw new IllegalArgumentException("配置文件不存在：" + path);
        var sources = new YamlPropertySourceLoader().load(path.toString(), new FileSystemResource(path));
        // Later files/documents override earlier ones; system properties and environment stay first.
        for (PropertySource<?> source : sources) {
            env.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        }
    }
}
