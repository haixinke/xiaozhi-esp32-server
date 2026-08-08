package xiaozhi.modules.pet.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.storyengine.service.PetStoryQueryService;
import xiaozhi.modules.storyengine.vo.PetStoryHistoryVO;
import xiaozhi.modules.storyengine.vo.PetStoryStateVO;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PetStoryControllerTest {

    private PetStoryQueryService queryService;
    private PetStoryController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(PetStoryQueryService.class);
        controller = new PetStoryController(queryService);
    }

    @Test
    void currentDelegatesOnlyAuthenticatedUserAndPathPetIdInSuccessEnvelope() {
        PetStoryStateVO expected = new PetStoryStateVO();
        when(queryService.getCurrent(7L, "pet-1")).thenReturn(expected);

        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(7L);

            Result<PetStoryStateVO> result = controller.current("pet-1");

            assertThat(result.getCode()).isZero();
            assertThat(result.getMsg()).isEqualTo("success");
            assertThat(result.getData()).isSameAs(expected);
            verify(queryService).getCurrent(7L, "pet-1");
        }
    }

    @Test
    void historyDelegatesAuthenticatedUserPathPetIdAndUnchangedParamsInSuccessEnvelope() {
        Map<String, Object> params = Map.of("page", "2", "limit", "20");
        PageData<PetStoryHistoryVO> expected = new PageData<>(List.of(), 0);
        when(queryService.getHistory(7L, "pet-1", params)).thenReturn(expected);

        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(7L);

            Result<PageData<PetStoryHistoryVO>> result = controller.history("pet-1", params);

            assertThat(result.getCode()).isZero();
            assertThat(result.getMsg()).isEqualTo("success");
            assertThat(result.getData()).isSameAs(expected);
            verify(queryService).getHistory(7L, "pet-1", params);
        }
    }

    @Test
    void controllerAndMethodsExposeExactRouteAndPermissionContracts() throws Exception {
        RequestMapping controllerRoute = PetStoryController.class.getAnnotation(RequestMapping.class);
        assertThat(controllerRoute.value()).containsExactly("/pet");

        Method current = PetStoryController.class.getMethod("current", String.class);
        assertGetContract(current, "/{id}/story-state");

        Method history = PetStoryController.class.getMethod("history", String.class, Map.class);
        assertGetContract(history, "/{id}/story-history");
    }

    private static void assertGetContract(Method method, String path) {
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(path);
        assertThat(method.getAnnotation(RequiresPermissions.class).value())
                .containsExactly("sys:role:normal");
    }
}
