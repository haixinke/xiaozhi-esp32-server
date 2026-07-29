package xiaozhi.modules.pdc.nfc.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdcNfcProductTypeAdminController 权限和结构测试")
class PdcNfcProductTypeAdminControllerTest {

    @Test
    @DisplayName("Controller 类上存在 superAdmin 权限注解")
    void classHasSuperAdminAnnotation() {
        RequiresPermissions annotation =
                PdcNfcProductTypeAdminController.class.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("sys:role:superAdmin");
    }

    @Test
    @DisplayName("Controller 提供 GET list 方法")
    void hasListMethod() throws NoSuchMethodException {
        assertThat(PdcNfcProductTypeAdminController.class
                .getDeclaredMethod("list")).isNotNull();
    }

    @Test
    @DisplayName("Controller 提供 POST release-evidence 方法")
    void hasReleaseEvidenceMethod() throws NoSuchMethodException {
        assertThat(PdcNfcProductTypeAdminController.class
                .getDeclaredMethod("registerReleaseEvidence",
                        xiaozhi.modules.pdc.nfc.dto.PdcNfcReleaseEvidenceDTO.class))
                .isNotNull();
    }
}
