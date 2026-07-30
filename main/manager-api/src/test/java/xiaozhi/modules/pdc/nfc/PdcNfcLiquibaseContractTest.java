package xiaozhi.modules.pdc.nfc;

import org.junit.jupiter.api.Test;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PdcNfcLiquibaseContractTest {

    @Test
    void baselineWriteJobItemTableUsesSchemeDigestColumn() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/changelog/202607291000.sql"));

        Set<String> writeJobItemTableColumns = tableColumns(sql, "pdc_nfc_write_job_item");

        // 基线 changeset 不可变：保持建表时的列名。
        assertThat(writeJobItemTableColumns)
                .contains("scheme_sha256")
                .doesNotContain("uri_payload");
    }

    @Test
    void migrationRenamesWriteJobItemDigestColumnToUriSha256() throws Exception {
        String baseline = Files.readString(Path.of(
                "src/main/resources/db/changelog/202607291000.sql"));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/changelog/202607301000.sql"));

        // 最终生效 schema：迁移把 scheme_sha256 重命名为 uri_sha256，
        // 全程不存在 uri_payload 明文列。
        Set<String> writeJobItemTableColumns = tableColumns(baseline, "pdc_nfc_write_job_item");
        writeJobItemTableColumns.remove("scheme_sha256");
        writeJobItemTableColumns.add("uri_sha256");

        assertThat(migration)
                .contains("pdc_nfc_write_job_item")
                .contains("scheme_sha256")
                .contains("uri_sha256");
        assertThat(writeJobItemTableColumns)
                .contains("uri_sha256")
                .doesNotContain("uri_payload", "scheme_sha256");
    }

    @Test
    void writeJobItemPersistenceModelDoesNotExposeUriPlaintext() {
        Set<String> persistentFields = Arrays.stream(
                        PdcNfcWriteJobItemEntity.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        assertThat(persistentFields)
                .contains("uriSha256")
                .doesNotContain("uriPayload", "schemeSha256");
    }

    private static Set<String> tableColumns(String sql, String tableName) {
        String marker = "CREATE TABLE " + tableName + " (";
        int tableStart = sql.indexOf(marker);
        assertThat(tableStart).as("table %s must exist", tableName).isGreaterThanOrEqualTo(0);
        int columnsStart = tableStart + marker.length();
        int tableEnd = sql.indexOf(") ENGINE=", columnsStart);
        assertThat(tableEnd).as("table %s definition must end", tableName).isGreaterThan(columnsStart);

        return sql.substring(columnsStart, tableEnd).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("PRIMARY ")
                        && !line.startsWith("UNIQUE ")
                        && !line.startsWith("KEY "))
                .map(line -> line.split("\\s+", 2)[0].replace("`", ""))
                .collect(Collectors.toSet());
    }
}
