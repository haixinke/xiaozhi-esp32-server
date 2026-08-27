package xiaozhi.modules.pdc.nfc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 NFC 迁移在真实 MySQL 兼容数据库中创建了种子数据和唯一约束。
 *
 * <p>直接执行迁移 SQL 文件，避免加载完整 Spring Boot 上下文。
 * 连接到本地 OceanBase (MySQL 兼容模式，端口 2881)。
 * 当 Docker 环境可用时，可切换为继承 {@link MySqlContainerSupport} 使用 Testcontainers。
 */
class PdcNfcLiquibaseIntegrationTest {

    private static Connection connection;
    private static JdbcTemplate jdbc;

    /** 测试专用库：严禁指向开发库 egg_database，本类会清空表数据 */
    private static final String TEST_DATABASE = "egg_nfc_test";

    @BeforeAll
    static void setUp() throws Exception {
        // 先连服务器级创建测试库，再连测试库；
        // 历史上本测试直连 egg_database 并 delete 全表，会清掉开发数据，不得回退。
        try (Connection bootstrap = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:2881/?useSSL=false&allowPublicKeyRetrieval=true",
                "root", "123456");
             Statement stmt = bootstrap.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + TEST_DATABASE);
        }
        connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:2881/" + TEST_DATABASE + "?useUnicode=true" +
                "&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" +
                "&nullCatalogMeansCurrent=true&useSSL=false&allowPublicKeyRetrieval=true",
                "root", "123456");

        // 如果 pdc_nfc_product_type 表不存在，执行 NFC 迁移 SQL
        if (!tableExists("pdc_nfc_product_type")) {
            String sql = Files.readString(Path.of(
                    "src/main/resources/db/changelog/202607291000.sql"));
            try (Statement stmt = connection.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        }

        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    private static boolean tableExists(String tableName) throws Exception {
        try (ResultSet rs = connection.getMetaData().getTables(
                connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    @AfterAll
    static void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @BeforeEach
    void cleanUp() {
        jdbc.update("delete from pdc_nfc_asset");
        jdbc.update("delete from pdc_nfc_batch");
    }

    @Test
    void migrationCreatesSeedAndUniqueConstraints() {
        Integer count = jdbc.queryForObject(
                "select count(*) from pdc_nfc_product_type where type_code='EGG_BABY_NFC'",
                Integer.class);
        assertThat(count).isEqualTo(1);

        Date now = new Date();
        byte[] nonce = new byte[12];
        byte[] ciphertext = new byte[32];

        jdbc.update("insert into pdc_nfc_batch " +
                "(id, batch_no, product_type_id, sku_code, prototype, planned_quantity, status, create_date) " +
                "values (1, 'B001', 1, 'SKU-KOI', '锦鲤', 1, 'DRAFT', ?)", now);
        jdbc.update("insert into pdc_nfc_asset " +
                "(id, asset_no, batch_id, item_no, sku_code, prototype, wechat_sn, " +
                "claim_ref_hash, claim_ref_hash_version, claim_ref_key_version, " +
                "claim_ref_nonce, claim_ref_ciphertext, status, create_date) " +
                "values (1, 'A1', 1, '000001', 'SKU-KOI', '锦鲤', 'EB001', " +
                "repeat('a',64), 'v1', 'v1', ?, ?, 'CREATED', ?)", nonce, ciphertext, now);

        assertThatThrownBy(() -> {
            jdbc.update("insert into pdc_nfc_asset " +
                    "(id, asset_no, batch_id, item_no, sku_code, prototype, wechat_sn, " +
                    "claim_ref_hash, claim_ref_hash_version, claim_ref_key_version, " +
                    "claim_ref_nonce, claim_ref_ciphertext, status, create_date) " +
                    "values (2, 'A2', 1, '000002', 'SKU-KOI', '锦鲤', 'EB001', " +
                    "repeat('b',64), 'v1', 'v1', ?, ?, 'CREATED', ?)", nonce, ciphertext, now);
        }).hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }
}
