package xiaozhi.modules.listen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在真实 MySQL 兼容库上验证题库迁移：建表、联合唯一约束、字典幂等初始化。
 *
 * <p>直接执行迁移 SQL 文件，不加载 Spring Boot 上下文。默认连接本地 OceanBase
 * (MySQL 兼容模式，端口 2881)，主机可用环境变量 DB_HOST 覆盖。
 * 使用独立测试库 {@code egg_listen_test}，严禁指向开发库 egg_database。
 */
class ListenMigrationIntegrationTest {

    /** 测试专用库：本类会清空表数据，严禁指向开发库 */
    private static final String TEST_DATABASE = "egg_listen_test";

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/changelog/202610281000.sql");

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

        // 迁移末尾会向既有基线表 sys_dict_type 写入字典大类，先创建最小基线表（来自仓库基线 changeset 的列集）
        if (!tableExists("sys_dict_type")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE `sys_dict_type` (" +
                        "`id` BIGINT NOT NULL COMMENT '主键ID'," +
                        "`dict_type` VARCHAR(100) NULL COMMENT '字典类型'," +
                        "`dict_name` VARCHAR(100) NULL COMMENT '字典名称'," +
                        "`remark` VARCHAR(255) NULL COMMENT '备注'," +
                        "`sort` INT NULL DEFAULT 0 COMMENT '排序'," +
                        "`creator` BIGINT NULL COMMENT '创建者'," +
                        "`create_date` DATETIME NULL COMMENT '创建时间'," +
                        "`updater` BIGINT NULL COMMENT '更新者'," +
                        "`update_date` DATETIME NULL COMMENT '更新时间'," +
                        "PRIMARY KEY (`id`)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }
        }

        // 建表只执行一次；字典初始化单独再跑一遍以验证幂等（重复执行不产生重复行、不撞主键）
        executeMigration();
        executeDictSeed();

        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterAll
    static void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @BeforeEach
    void cleanUp() {
        jdbc.update("delete from ai_listen_question");
        jdbc.update("delete from ai_listen_group");
    }

    @Test
    @DisplayName("迁移在真实库中建出两张题库表且不含 deleted 列")
    void migrationCreatesTablesWithoutDeletedColumn() throws Exception {
        assertThat(tableExists("ai_listen_group")).isTrue();
        assertThat(tableExists("ai_listen_question")).isTrue();
        assertThat(columnNames("ai_listen_group")).doesNotContain("deleted");
        assertThat(columnNames("ai_listen_question")).doesNotContain("deleted");
    }

    @Test
    @DisplayName("组名在大类内唯一：同大类重名被拒，不同大类可同名")
    void groupNameIsUniqueWithinCategory() {
        insertGroup(1L, "DAILY", "睡前最后那一会儿");
        // 不同大类下的同名分组允许存在
        insertGroup(2L, "WORK", "睡前最后那一会儿");

        assertThatThrownBy(() -> insertGroup(3L, "DAILY", "睡前最后那一会儿"))
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    @Test
    @DisplayName("题干在分组内唯一：同组重复题干被拒，跨组可重复")
    void questionTextIsUniqueWithinGroup() {
        insertGroup(10L, "DAILY", "组一");
        insertGroup(11L, "DAILY", "组二");
        insertQuestion(100L, 10L, "今天最想感谢谁？");
        // 不同分组下的相同题干允许存在
        insertQuestion(101L, 11L, "今天最想感谢谁？");

        assertThatThrownBy(() -> insertQuestion(102L, 10L, "今天最想感谢谁？"))
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    @Test
    @DisplayName("选项以 JSON 列存储，可按 JSON 路径查询")
    void optionsAreStoredAsQueryableJson() {
        insertGroup(20L, "DAILY", "组三");
        insertQuestion(200L, 20L, "题干");

        String firstKey = jdbc.queryForObject(
                "select JSON_UNQUOTE(JSON_EXTRACT(options_json, '$[0].key')) " +
                        "from ai_listen_question where id = 200",
                String.class);
        assertThat(firstKey).isEqualTo("A");
    }

    @Test
    @DisplayName("LISTEN_CATEGORY 字典类型以 id 108 存在，重复执行迁移不产生重复行")
    void listenCategoryDictTypeIsSeededIdempotently() {
        Integer rows = jdbc.queryForObject(
                "select count(*) from sys_dict_type where dict_type = 'LISTEN_CATEGORY'",
                Integer.class);
        Integer byId = jdbc.queryForObject(
                "select count(*) from sys_dict_type where id = 108",
                Integer.class);

        assertThat(rows).isEqualTo(1);
        assertThat(byId).isEqualTo(1);
    }

    private static void executeMigration() throws Exception {
        String sql = Files.readString(MIGRATION);
        try (Statement stmt = connection.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                // 建表不可重入：已存在时跳过（沿用仓库 PdcNfc 集成测试的 tableExists 守卫写法），
                // 使测试可反复运行而不必手工清库。语句可能带前导 -- 注释，故用 contains 判断而非 startsWith
                if (trimmed.contains("CREATE TABLE") && tableExists(tableNameOf(trimmed))) {
                    continue;
                }
                stmt.execute(trimmed);
            }
        }
    }

    private static String tableNameOf(String createStatement) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("CREATE\\s+TABLE\\s+`?(\\w+)`?").matcher(createStatement);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 只执行迁移中针对 sys_dict_type 的语句（delete + insert），跳过 CREATE TABLE，
     * 用于在已建好的表上重复验证字典初始化的幂等性。
     */
    private static void executeDictSeed() throws Exception {
        String sql = Files.readString(MIGRATION);
        try (Statement stmt = connection.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && trimmed.contains("sys_dict_type")) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    private static boolean tableExists(String tableName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(
                connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
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

    private static void insertGroup(Long id, String categoryCode, String name) {
        jdbc.update("insert into ai_listen_group (id, category_code, name, sort_order, create_date) " +
                "values (?, ?, ?, 0, ?)", id, categoryCode, name, new Date());
    }

    private static void insertQuestion(Long id, Long groupId, String text) {
        jdbc.update("insert into ai_listen_question " +
                        "(id, group_id, text, type, options_json, sort_order, create_date) " +
                        "values (?, ?, ?, 'SINGLE', ?, 0, ?)",
                id, groupId, text,
                "[{\"key\":\"A\",\"text\":\"选项A\"},{\"key\":\"B\",\"text\":\"选项B\"}]",
                new Date());
    }
}