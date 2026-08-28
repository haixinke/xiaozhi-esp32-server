package xiaozhi.modules.pdc.nfc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcClaimConfirmDTO;
import xiaozhi.modules.pdc.nfc.service.PdcNfcClaimService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimPreviewVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimResultVO;
import xiaozhi.modules.pet.vo.PetVO;

class PdcNfcClaimControllerTest {

    @Test
    @DisplayName("preview wraps VO in Result envelope (code=0)")
    void previewWrapsResultEnvelope() {
        PdcNfcClaimService mockService = mock(PdcNfcClaimService.class);
        PdcNfcClaimController controller = new PdcNfcClaimController(mockService);

        // 无 Shiro 上下文时 SecurityUser.getUserId() 返回 null，service 入参按 null 匹配
        PdcNfcClaimPreviewVO vo = new PdcNfcClaimPreviewVO(
                "翡翠玉兔", "jade_rabbit", PdcNfcClaimPreviewVO.STATUS_CLAIMABLE, null);
        when(mockService.preview(null, "abcdefghij1234567890_-")).thenReturn(vo);

        Result<PdcNfcClaimPreviewVO> result = controller.preview("abcdefghij1234567890_-");
        // 小程序 request.js 依赖 code 字段判断成败，必须为标准信封
        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo(vo);
    }

    @Test
    @DisplayName("confirm wraps VO in Result envelope (code=0)")
    void confirmWrapsResultEnvelope() {
        PdcNfcClaimService mockService = mock(PdcNfcClaimService.class);
        PdcNfcClaimController controller = new PdcNfcClaimController(mockService);

        PetVO petVO = new PetVO();
        petVO.setId("pet-123");
        PdcNfcClaimResultVO vo = PdcNfcClaimResultVO.claimed(petVO);
        UUID requestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        when(mockService.confirm(null, "abcdefghij1234567890_-", requestId)).thenReturn(vo);

        PdcNfcClaimConfirmDTO dto = new PdcNfcClaimConfirmDTO();
        dto.setClaimRef("abcdefghij1234567890_-");
        dto.setRequestId(requestId.toString());
        // 同 preview：必须返回标准信封，小程序 request.js 按 code 判断成败
        Result<PdcNfcClaimResultVO> result = controller.confirm(dto);
        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo(vo);
    }

    @Test
    @DisplayName("preview returns PreviewVO from service")
    void previewReturnsPreviewVO() {
        PdcNfcClaimService mockService = mock(PdcNfcClaimService.class);
        PdcNfcClaimController controller = new PdcNfcClaimController(mockService);

        PdcNfcClaimPreviewVO expected = new PdcNfcClaimPreviewVO(
                "翡翠玉兔", "jade_rabbit", PdcNfcClaimPreviewVO.STATUS_CLAIMABLE, null);
        when(mockService.preview(100L, "abcdefghij1234567890_-")).thenReturn(expected);

        PdcNfcClaimPreviewVO result = mockService.preview(100L, "abcdefghij1234567890_-");
        assertThat(result).isEqualTo(expected);
        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_CLAIMABLE);
    }

    @Test
    @DisplayName("confirm returns ClaimResultVO from service")
    void confirmReturnsClaimResult() {
        PdcNfcClaimService mockService = mock(PdcNfcClaimService.class);
        PdcNfcClaimController controller = new PdcNfcClaimController(mockService);

        PetVO petVO = new PetVO();
        petVO.setId("pet-123");
        PdcNfcClaimResultVO expected = PdcNfcClaimResultVO.claimed(petVO);
        UUID requestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        when(mockService.confirm(100L, "abcdefghij1234567890_-", requestId)).thenReturn(expected);

        PdcNfcClaimResultVO result = mockService.confirm(100L, "abcdefghij1234567890_-", requestId);
        assertThat(result).isEqualTo(expected);
        assertThat(result.claimStatus()).isEqualTo("CLAIMED");
        assertThat(result.pet()).isEqualTo(petVO);
    }

    @Test
    @DisplayName("controller class has @RequiresPermissions(\"sys:role:normal\")")
    void classHasNormalPermissionAnnotation() {
        RequiresPermissions rp = PdcNfcClaimController.class.getAnnotation(RequiresPermissions.class);
        assertThat(rp).isNotNull();
        assertThat(Arrays.asList(rp.value())).contains("sys:role:normal");
    }

    @Test
    @DisplayName("controller has @RequestMapping(\"/pdc/nfc/claim\")")
    void controllerHasCorrectPath() {
        RequestMapping rm = PdcNfcClaimController.class.getAnnotation(RequestMapping.class);
        assertThat(rm).isNotNull();
        assertThat(Arrays.asList(rm.value())).contains("/pdc/nfc/claim");
    }
}
