package xiaozhi.modules.star;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 每日奖励表迁移契约测试：Liquibase 只追加 changeSet，
 * ai_star_daily_reward 的列、双唯一约束与"不存连续天数/外键"约定以文件内容兜底。
 */
@DisplayName("每日星星奖励 Liquibase 迁移契约测试")
class StarDailyRewardLiquibaseContractTest {

    @Test
    @DisplayName("ai_star_daily_reward 建表 changeset 已登记且包含确定的列与双唯一约束")
    void dailyRewardTableMigrationIsRegistered() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/changelog/202610301000.sql"));
        String master = Files.readString(Path.of(
                "src/main/resources/db/changelog/db.changelog-master.yaml"));

        assertThat(master).contains("202610301000");
        assertThat(migration)
                .contains("ai_star_daily_reward")
                .contains("user_id")
                .contains("source_type")
                .contains("source_key")
                .contains("reward_date")
                .contains("amount")
                .contains("star_transaction_id")
                .contains("create_date")
                // 用户+来源+日期联合唯一：每日每来源一次；流水唯一：奖励与流水一对一
                .contains("uk_star_daily_reward_user_source_date")
                .contains("uk_star_daily_reward_transaction");

        String ddl = migration.substring(migration.indexOf("CREATE TABLE"));
        // 雪花主键，非自增；不建物理外键；不存连续天数、宠物或设备维度列
        assertThat(ddl).doesNotContain("AUTO_INCREMENT");
        assertThat(ddl).doesNotContainIgnoringCase("FOREIGN KEY");
        assertThat(ddl).doesNotContain("streak");
        assertThat(ddl).doesNotContain("pet_id");
        assertThat(ddl).doesNotContain("device_id");
        // 无 reward_date 单列索引：没有按日期扫描全量用户的需求
        assertThat(ddl).doesNotContain("KEY `idx_star_daily_reward_date`");
    }
}
