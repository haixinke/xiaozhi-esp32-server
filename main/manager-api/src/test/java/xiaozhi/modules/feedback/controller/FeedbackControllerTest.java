package xiaozhi.modules.feedback.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.feedback.dto.FeedbackSubmitDTO;
import xiaozhi.modules.feedback.service.FeedbackService;
import xiaozhi.modules.feedback.vo.FeedbackSubmitVO;
import xiaozhi.modules.security.user.SecurityUser;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackControllerTest {

    private FeedbackService feedbackService;
    private FeedbackController controller;

    @BeforeEach
    void setUp() {
        feedbackService = mock(FeedbackService.class);
        controller = new FeedbackController(feedbackService);
    }

    @Test
    void submitDelegatesAuthenticatedUserAndDtoInSuccessEnvelope() {
        FeedbackSubmitDTO dto = new FeedbackSubmitDTO();
        dto.setType("OTHER");
        dto.setContent("宠物对话打不开");
        dto.setConsent(true);
        FeedbackSubmitVO expected = new FeedbackSubmitVO();
        expected.setReceiptNumber("FB20260823-000123");
        when(feedbackService.submit(7L, dto)).thenReturn(expected);

        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(7L);

            Result<FeedbackSubmitVO> result = controller.submit(dto);

            assertThat(result.getCode()).isZero();
            assertThat(result.getMsg()).isEqualTo("success");
            assertThat(result.getData()).isSameAs(expected);
            verify(feedbackService).submit(7L, dto);
        }
    }

    @Test
    void controllerExposesExactRouteAndPermissionContract() throws Exception {
        RequestMapping controllerRoute = FeedbackController.class.getAnnotation(RequestMapping.class);
        assertThat(controllerRoute.value()).containsExactly("/feedback");

        Method submit = FeedbackController.class.getMethod("submit", FeedbackSubmitDTO.class);
        assertThat(submit.getAnnotation(PostMapping.class)).isNotNull();
        assertThat(submit.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("sys:role:normal");
    }
}
