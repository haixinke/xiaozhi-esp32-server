package xiaozhi.modules.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在真实 MySQL 兼容库上验证游戏分类迁移：为 ai_game 增加 game_category / jump_url。
 *
 * <p>直接执行迁移 SQL 文件，不加载 Spring Boot 上下文。默认连接本地 OceanBase
 * (MySQL 兼容模式，端口 2881)，主机可用环境变量 DB_HOST 覆盖。
 * 使用独立测试库 {@code egg_game_test}，严禁指向开发库 egg_database。
 *
 * <p>测试先按迁移前的旧表结构建 ai_game（不含两个新列），写入存量风格数据，
 * 再执行新增列的 changeset，验证存量行回填 game_category='INTERNAL'、jump_url 为 NULL，
 * 且原有列（类型、星星、次数、入口、状态）保持不变。
 */
class GameMigrationIntegrationTest {

    /** 测试专用库：本类会清空表数据，严禁指向开发库 */
    private static final String TEST_DATABASE = "egg_game_test";

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/changelog/202611261000.sql");

    private static Connection connection;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() throws Exception {
        try (Connection bootstrap = DriverManager.getConnection(
                "jdbc:mysql://" + DB_HOST + ":2881/?useSSL=false&allowPublicKeyRetrieval=true",
                "root", "123456");
             Statement stmt = bootstrap.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + TEST_DATABASE);
        }
        connection = DriverManager.getConnection(
                "jdbc:mysql://" + DB_HOST + ":2881/" + TEST_DATABASE + "?useUnicode=true" +
                "&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" +
                "&nullCatalogMeansCurrent=true&useSSL=false&allowPublicKeyRetrieval=true",
                "root", "123456");

        // 按迁移前的旧 schema 建 ai_game（202610161000 的列集 + 202610171000 的唯一索引），
        // 模拟含存量游戏的升级现场；已存在则先删，保证每次运行都是从旧结构升级
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS ai_game");
            stmt.execute("CREATE TABLE `ai_game` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                    "`game_type` VARCHAR(64) NOT NULL COMMENT '游戏类型(字典GAME_TYPE的code)'," +
                    "`earn_stars` INT NOT NULL DEFAULT 0 COMMENT '赚取星星数(非负)'," +
                    "`consume_stars` INT NOT NULL DEFAULT 0 COMMENT '消耗星星数(非负)'," +
                    "`daily_limit` INT NOT NULL DEFAULT 0 COMMENT '每日每设备可玩次数上限(0=不限)'," +
                    "`icon` VARCHAR(512) NULL COMMENT '游戏图标OSS URL'," +
                    "`description` VARCHAR(500) NULL COMMENT '游戏说明'," +
                    "`entrances` VARCHAR(128) NOT NULL COMMENT '游戏入口(逗号分隔枚举code,如NOTE,ACTIVITY)'," +
                    "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用'," +
                    "`creator` BIGINT NULL COMMENT '创建者'," +
                    "`create_date` DATETIME NULL COMMENT '创建时间'," +
                    "`updater` BIGINT NULL COMMENT '更新者'," +
                    "`update_date` DATETIME NULL COMMENT '更新时间'," +
                    "PRIMARY KEY (`id`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏配置'");
            stmt.execute("ALTER TABLE `ai_game` ADD UNIQUE KEY `uk_ai_game_game_type` (`game_type`)");
        }

        // 写入存量风格数据（旧结构，无新列），再执行迁移
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.update("insert into ai_game " +
                        "(game_type, earn_stars, consume_stars, daily_limit, icon, description, entrances, status, creator, create_date) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "DRAW_TOGETHER", 5, 0, 3, "https://oss.example/icon.png", "一起画", "NOTE,ACTIVITY", 1, 1L, new Date());
        jdbc.update("insert into ai_game " +
                        "(game_type, earn_stars, consume_stars, daily_limit, icon, description, entrances, status, creator, create_date) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "LISTEN_TO_YOU", 0, 2, 0, null, "听你说", "NOTE", 0, 1L, new Date());

        executeMigration();
    }

    @AfterAll
    static void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    @DisplayName("迁移为 ai_game 增加 game_category 与 jump_url 两列")
    void migrationAddsCategoryAndJumpUrlColumns() throws Exception {
        assertThat(columnNames("ai_game")).contains("game_category", "jump_url");
    }

    @Test
    @DisplayName("存量行迁移后 game_category=INTERNAL、jump_url 为 NULL，原有列保持不变")
    void existingRowsBackfilledAsInternalWithNullJumpUrl() {
        var rows = jdbc.queryForList(
                "select game_type, game_category, jump_url, earn_stars, consume_stars, daily_limit, entrances, status " +
                        "from ai_game order by id");

        assertThat(rows).hasSize(2);

        var drawTogether = rows.get(0);
        assertThat(drawTogether.get("GAME_TYPE")).isEqualTo("DRAW_TOGETHER");
        assertThat(drawTogether.get("GAME_CATEGORY")).isEqualTo("INTERNAL");
        assertThat(drawTogether.get("JUMP_URL")).isNull();
        // 原有列逐项核对，确认迁移只加列不改数据
        assertThat(((Number) drawTogether.get("EARN_STARS")).intValue()).isEqualTo(5);
        assertThat(((Number) drawTogether.get("CONSUME_STARS")).intValue()).isEqualTo(0);
        assertThat(((Number) drawTogether.get("DAILY_LIMIT")).intValue()).isEqualTo(3);
        assertThat(drawTogether.get("ENTRANCES")).isEqualTo("NOTE,ACTIVITY");
        assertThat(((Number) drawTogether.get("STATUS")).intValue()).isEqualTo(1);

        var listenToYou = rows.get(1);
        assertThat(listenToYou.get("GAME_TYPE")).isEqualTo("LISTEN_TO_YOU");
        assertThat(listenToYou.get("GAME_CATEGORY")).isEqualTo("INTERNAL");
        assertThat(listenToYou.get("JUMP_URL")).isNull();
        assertThat(((Number) listenToYou.get("EARN_STARS")).intValue()).isEqualTo(0);
        assertThat(((Number) listenToYou.get("CONSUME_STARS")).intValue()).isEqualTo(2);
        assertThat(((Number) listenToYou.get("DAILY_LIMIT")).intValue()).isEqualTo(0);
        assertThat(listenToYou.get("ENTRANCES")).isEqualTo("NOTE");
        assertThat(((Number) listenToYou.get("STATUS")).intValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("迁移后新增行缺省 game_category 落为 INTERNAL，jump_url 允许为空")
    void newRowsDefaultToInternalCategory() {
        jdbc.update("insert into ai_game (game_type, entrances) values (?, ?)", "MEET_YOU", "ACTIVITY");
        try {
            var row = jdbc.queryForMap(
                    "select game_category, jump_url from ai_game where game_type = 'MEET_YOU'");
            assertThat(row.get("GAME_CATEGORY")).isEqualTo("INTERNAL");
            assertThat(row.get("JUMP_URL")).isNull();
        } finally {
            jdbc.update("delete from ai_game where game_type = 'MEET_YOU'");
        }
    }

    @Test
    @DisplayName("game_category 为非空列：显式写 NULL 被拒")
    void gameCategoryIsNotNull() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "insert into ai_game (game_type, entrances, game_category) values (?, ?, ?)",
                        "TRAVEL", "NOTE", (String) null))
                .hasRootCauseInstanceOf(SQLException.class);
    }

    private static void executeMigration() throws Exception {
        String sql = Files.readString(MIGRATION);
        try (Statement stmt = connection.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    private static java.util.Set<String> columnNames(String tableName) throws SQLException {
        java.util.Set<String> columns = new java.util.HashSet<>();
        try (ResultSet rs = connection.getMetaData().getColumns(
                connection.getCatalog(), null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }
}
