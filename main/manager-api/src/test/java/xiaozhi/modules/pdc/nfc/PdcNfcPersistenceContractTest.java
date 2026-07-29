package xiaozhi.modules.pdc.nfc;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持久化契约测试：验证所有 NFC 实体的 @TableName 以 pdc_ 开头、
 * 资产含 @Version、Mapper XML 含 ORDER BY id 和 FOR UPDATE。
 */
class PdcNfcPersistenceContractTest {

    private static final String ENTITY_PACKAGE = "xiaozhi.modules.pdc.nfc.entity";
    private static final List<Class<?>> ENTITY_CLASSES = List.of(
        xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeAttemptEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteRecordEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcClaimRecordEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity.class
    );

    @Test
    void allEntitiesHavePdcPrefixTableName() {
        for (Class<?> entityClass : ENTITY_CLASSES) {
            TableName annotation = entityClass.getAnnotation(TableName.class);
            assertThat(annotation)
                    .as("%s must have @TableName", entityClass.getSimpleName())
                    .isNotNull();
            assertThat(annotation.value())
                    .as("%s @TableName must start with pdc_", entityClass.getSimpleName())
                    .startsWith("pdc_");
        }
    }

    @Test
    void entityCountIsEleven() {
        assertThat(ENTITY_CLASSES).hasSize(11);
    }

    @Test
    void assetEntityHasVersionAnnotation() {
        Class<?> assetEntity = xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity.class;
        boolean hasVersionField = Arrays.stream(assetEntity.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(Version.class));
        assertThat(hasVersionField)
                .as("PdcNfcAssetEntity must have a @Version field")
                .isTrue();
    }

    @Test
    void assetMapperXmlContainsOrderByIdAndForUpdate() throws IOException {
        Path xmlPath = Paths.get(
            "src/main/resources/mapper/pdc/nfc/PdcNfcAssetDao.xml"
        );
        assertThat(xmlPath)
                .as("PdcNfcAssetDao.xml must exist")
                .exists();

        String content = Files.readString(xmlPath);
        assertThat(content)
                .as("PdcNfcAssetDao.xml must contain ORDER BY id")
                .contains("ORDER BY id");
        assertThat(content)
                .as("PdcNfcAssetDao.xml must contain FOR UPDATE")
                .contains("FOR UPDATE");
    }

    @ParameterizedTest
    @ValueSource(classes = {
        xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeAttemptEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteRecordEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcClaimRecordEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity.class,
        xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity.class
    })
    void entityUsesAssignIdType(Class<?> entityClass) {
        Field idField = Arrays.stream(entityClass.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(
                        com.baomidou.mybatisplus.annotation.TableId.class))
                .findFirst()
                .orElse(null);
        assertThat(idField)
                .as("%s must have @TableId", entityClass.getSimpleName())
                .isNotNull();
        com.baomidou.mybatisplus.annotation.TableId tableId =
                idField.getAnnotation(com.baomidou.mybatisplus.annotation.TableId.class);
        assertThat(tableId.type())
                .as("%s @TableId must be ASSIGN_ID", entityClass.getSimpleName())
                .isEqualTo(com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID);
    }
}
