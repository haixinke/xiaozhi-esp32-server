package xiaozhi.modules.listen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import xiaozhi.common.constant.Constant;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 题库建表与字典初始化的静态契约测试。
 *
 * <p>直接断言 Liquibase 迁移脚本与实体骨架的内容，不启动 Spring 上下文：
 * 物理删除是 ADR 0006 软删惯例的例外，因此「两张表都不得出现 deleted 列」是本测试的核心防线，
 * 防止后续有人照抄 smallobject 的软删写法。
 */
class ListenSchemaContractTest {

    private static final String MIGRATION = "src/main/resources/db/changelog/202610281000.sql";
    private static final String MASTER = "src/main/resources/db/changelog/db.changelog-master.yaml";

    @Test
    @DisplayName("迁移脚本建出题目分组表，字段齐全且无 deleted 列")
    void migrationCreatesGroupTableWithoutDeletedColumn() throws Exception {
        String sql = Files.readString(Path.of(MIGRATION));

        Set<String> columns = tableColumns(sql, "ai_listen_group");

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "category_code", "name", "sort_order",
                "creator", "create_date", "updater", "update_date");
    }

    @Test
    @DisplayName("迁移脚本建出题目表，选项内联为 JSON 列且无 deleted 列")
    void migrationCreatesQuestionTableWithOptionsJsonColumn() throws Exception {
        String sql = Files.readString(Path.of(MIGRATION));

        Set<String> columns = tableColumns(sql, "ai_listen_question");

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "group_id", "text", "type", "options_json", "sort_order",
                "creator", "create_date", "updater", "update_date");
    }

    @Test
    @DisplayName("组名在大类内唯一、题干在分组内唯一，由数据库联合唯一约束兜底")
    void migrationDeclaresCompositeUniqueConstraints() throws Exception {
        String sql = Files.readString(Path.of(MIGRATION));

        assertThat(uniqueKeys(sql, "ai_listen_group")).contains("category_code,name");
        assertThat(uniqueKeys(sql, "ai_listen_question")).contains("group_id,text");
    }

    @Test
    @DisplayName("LISTEN_CATEGORY 字典类型以 id 108 幂等初始化")
    void migrationSeedsListenCategoryDictTypeIdempotently() throws Exception {
        String sql = Files.readString(Path.of(MIGRATION));

        // 先按主键删除再插入，使 changeset 重复执行时不会主键冲突
        assertThat(sql).containsPattern("delete\\s+from\\s+`?sys_dict_type`?\\s+where\\s+`?id`?\\s*=\\s*108");
        assertThat(sql).containsPattern("[(,]\\s*108\\s*,\\s*'LISTEN_CATEGORY'");
    }

    @Test
    @DisplayName("master changelog 末尾注册新 changeset，且不重复注册")
    void masterChangelogRegistersMigrationOnce() throws Exception {
        String master = Files.readString(Path.of(MASTER));

        assertThat(master).contains("id: 202610281000");
        assertThat(master.split("202610281000", -1)).hasSize(3);
    }

    @Test
    @DisplayName("实体骨架暴露预期业务字段且不含 deleted")
    void entitiesExposeBusinessFieldsWithoutDeleted() {
        assertThat(fieldNames(ListenGroupEntity.class))
                .contains("categoryCode", "name", "sortOrder")
                .doesNotContain("deleted");
        assertThat(fieldNames(ListenQuestionEntity.class))
                .contains("groupId", "text", "type", "optionsJson", "sortOrder")
                .doesNotContain("deleted");
    }

    @Test
    @DisplayName("Constant.DictType 新增 LISTEN_CATEGORY 枚举")
    void dictTypeEnumExposesListenCategory() {
        assertThat(Arrays.stream(Constant.DictType.values())
                .filter(type -> "LISTEN_CATEGORY".equals(type.getValue()))
                .map(Constant.DictType::name)
                .collect(Collectors.toSet()))
                .containsExactly("LISTEN_CATEGORY");
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());
    }

    /**
     * 从建表语句中解析列名集合，跳过主键/索引行。表名可带或不带反引号。
     */
    private static Set<String> tableColumns(String sql, String tableName) {
        String body = tableBody(sql, tableName);
        return body.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("PRIMARY ")
                        && !line.startsWith("UNIQUE ")
                        && !line.startsWith("KEY ")
                        && !line.startsWith("CONSTRAINT "))
                .map(line -> line.split("\\s+", 2)[0].replace("`", ""))
                .collect(Collectors.toSet());
    }

    /**
     * 解析表内 UNIQUE KEY 的列组合，统一成 (a,b) 便于断言，忽略索引名与反引号。
     */
    private static Set<String> uniqueKeys(String sql, String tableName) {
        String body = tableBody(sql, tableName);
        Matcher matcher = Pattern.compile("UNIQUE\\s+KEY\\s+`?\\w+`?\\s*\\(([^)]*)\\)").matcher(body);
        Set<String> keys = new java.util.HashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1).replace("`", "").replace(" ", ""));
        }
        return keys;
    }

    private static String tableBody(String sql, String tableName) {
        Matcher start = Pattern.compile("CREATE\\s+TABLE\\s+`?" + tableName + "`?\\s*\\(").matcher(sql);
        assertThat(start.find()).as("表 %s 必须存在建表语句", tableName).isTrue();
        int bodyStart = start.end();
        int bodyEnd = sql.indexOf(") ENGINE=", bodyStart);
        assertThat(bodyEnd).as("表 %s 的建表语句必须正常结束", tableName).isGreaterThan(bodyStart);
        return sql.substring(bodyStart, bodyEnd);
    }
}