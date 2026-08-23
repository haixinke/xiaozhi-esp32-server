package xiaozhi.modules.feedback.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.feedback.dto.FeedbackHandleDTO;
import xiaozhi.modules.feedback.service.FeedbackService;
import xiaozhi.modules.feedback.vo.FeedbackAdminVO;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackAdminControllerTest {

    private FeedbackService feedbackService;
    private FeedbackAdminController controller;

    @BeforeEach
    void setUp() {
        feedbackService = mock(FeedbackService.class);
        controller = new FeedbackAdminController(feedbackService);
    }

    @Test
    void pageDelegatesUnchangedParamsInSuccessEnvelope() {
        Map<String, Object> params = Map.of("page", "1", "limit", "10", "status", "0");
        PageData<FeedbackAdminVO> expected = new PageData<>(List.of(), 0);
        when(feedbackService.page(params)).thenReturn(expected);

        Result<PageData<FeedbackAdminVO>> result = controller.page(params);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isSameAs(expected);
        verify(feedbackService).page(params);
    }

    @Test
    void getDelegatesIdInSuccessEnvelope() {
        FeedbackAdminVO expected = new FeedbackAdminVO();
        when(feedbackService.get(99L)).thenReturn(expected);

        Result<FeedbackAdminVO> result = controller.get(99L);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isSameAs(expected);
        verify(feedbackService).get(99L);
    }

    @Test
    void updateDelegatesDtoInSuccessEnvelope() {
        FeedbackHandleDTO dto = new FeedbackHandleDTO();
        dto.setId(99L);
        dto.setStatus(1);
        dto.setRemark("已联系用户");

        Result<Void> result = controller.update(dto);

        assertThat(result.getCode()).isZero();
        verify(feedbackService).handle(dto);
    }

    @Test
    void controllerExposesExactRouteAndSuperAdminPermissionContract() throws Exception {
        RequestMapping controllerRoute = FeedbackAdminController.class.getAnnotation(RequestMapping.class);
        assertThat(controllerRoute.value()).containsExactly("/admin/feedback");

        Method page = FeedbackAdminController.class.getMethod("page", Map.class);
        assertThat(page.getAnnotation(GetMapping.class).value()).containsExactly("/page");
        assertThat(page.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("sys:role:superAdmin");

        Method get = FeedbackAdminController.class.getMethod("get", Long.class);
        assertThat(get.getAnnotation(GetMapping.class).value()).containsExactly("/{id}");
        assertThat(get.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("sys:role:superAdmin");

        Method update = FeedbackAdminController.class.getMethod("update", FeedbackHandleDTO.class);
        assertThat(update.getAnnotation(PutMapping.class).value()).containsExactly("/update");
        assertThat(update.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("sys:role:superAdmin");
    }
}
