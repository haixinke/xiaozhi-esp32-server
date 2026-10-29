package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 小纸条建表迁移契约测试：Liquibase 只追加 changeSet，
 * ai_daily_note 的列、唯一索引与"无软删字段"约定以文件内容兜底。
 */
@DisplayName("小纸条 Liquibase 迁移契约测试")
class DailyNoteLiquibaseContractTest {

    @Test
    @DisplayName("ai_daily_note 建表 changeset 已登记且包含确定的列与唯一索引")
    void dailyNoteTableMigrationIsRegistered() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/changelog/202610291000.sql"));
        String master = Files.readString(Path.of(
                "src/main/resources/db/changelog/db.changelog-master.yaml"));

        assertThat(master).contains("202610291000");
        assertThat(migration)
                .contains("ai_daily_note")
                .contains("note_date")
                .contains("title")
                .contains("content")
                .contains("solar_term")
                .contains("creator")
                .contains("create_date")
                .contains("updater")
                .contains("update_date")
                // 完整日期全局唯一
                .contains("uk_ai_daily_note_date");
        // 物理删除决策：无软删字段（ADR 0010 决策方式）；按反引号列名判断，避开注释文字
        assertThat(migration).doesNotContain("`deleted`");
        // 雪花主键，非自增（只看 DDL 语句本体，注释里允许提到该约定）
        String ddl = migration.substring(migration.indexOf("CREATE TABLE"));
        assertThat(ddl).doesNotContain("AUTO_INCREMENT");
    }

    @Test
    @DisplayName("ai_daily_note_opened 建表 changeset 已登记且包含确定的列、联合唯一索引，无 note_id 与外键")
    void dailyNoteOpenedTableMigrationIsRegistered() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/changelog/202610291100.sql"));
        String master = Files.readString(Path.of(
                "src/main/resources/db/changelog/db.changelog-master.yaml"));

        assertThat(master).contains("202610291100");
        assertThat(migration)
                .contains("ai_daily_note_opened")
                .contains("user_id")
                .contains("note_date")
                .contains("opened_at")
                .contains("create_date")
                // 用户+完整日期联合唯一：并发重复上报由数据库兜底
                .contains("uk_ai_daily_note_opened_user_date");
        String ddl = migration.substring(migration.indexOf("CREATE TABLE"));
        // 雪花主键，非自增；不保存 note_id、不建外键（内容维护不影响已打开记录）
        assertThat(ddl).doesNotContain("AUTO_INCREMENT");
        assertThat(ddl).doesNotContain("note_id");
        assertThat(ddl).doesNotContainIgnoringCase("FOREIGN KEY");
        // 无 note_date 单列索引：没有按日期查询全部用户的需求
        assertThat(ddl).doesNotContain("KEY `idx_ai_daily_note_opened_note_date`");
    }
}
