package xiaozhi.modules.pdc.nfc.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 共享 MySQL Testcontainers 基类，为 NFC 模块集成测试提供真实 MySQL 8 实例。
 */
@Testcontainers
@ActiveProfiles("nfc-test")
public abstract class MySqlContainerSupport {

    @Container
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("xiaozhi_nfc_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.liquibase.enabled", () -> true);
    }
}
