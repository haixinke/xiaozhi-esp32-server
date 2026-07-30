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
    void writeJobItemTableStoresOnlyUriDigestInsteadOfPlaintext() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/changelog/202607291000.sql"));

        Set<String> writeJobItemTableColumns = tableColumns(sql, "pdc_nfc_write_job_item");

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
