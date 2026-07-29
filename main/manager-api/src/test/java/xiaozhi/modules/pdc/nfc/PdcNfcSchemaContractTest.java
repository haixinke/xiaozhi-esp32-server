package xiaozhi.modules.pdc.nfc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 NFC 迁移脚本定义了全部 11 张 pdc_ 表和固定商品类型种子。
 */
class PdcNfcSchemaContractTest {

    private static final List<String> TABLES = List.of(
            "pdc_nfc_product_type", "pdc_nfc_batch", "pdc_nfc_asset",
            "pdc_nfc_scheme_job", "pdc_nfc_scheme_attempt",
            "pdc_nfc_write_job", "pdc_nfc_write_job_item",
            "pdc_nfc_write_record", "pdc_nfc_claim_record",
            "pdc_nfc_admin_request", "pdc_nfc_operation_log");

    @Test
    void migrationDefinesEveryPdcTableAndSeed() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/changelog/202607291000.sql"));
        TABLES.forEach(table -> assertThat(sql).contains("CREATE TABLE " + table));
        assertThat(sql).contains("EGG_BABY_NFC")
                .contains("uk_pdc_nfc_asset_wechat_sn")
                .contains("uk_pdc_nfc_claim_asset")
                .doesNotContain("model_id");
    }
}
