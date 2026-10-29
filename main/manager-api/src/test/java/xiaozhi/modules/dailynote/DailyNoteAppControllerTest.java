package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import xiaozhi.common.utils.Result;
import xiaozhi.modules.dailynote.controller.DailyNoteAppController;
import xiaozhi.modules.dailynote.service.DailyNoteTodayService;
import xiaozhi.modules.dailynote.vo.DailyNoteTodayVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 当日小纸条应用端控制器契约测试。
 * 覆盖：路由与权限注解、Token 用户身份传递、成功空响应与统一响应结构。
 * Service 各业务分支由 DailyNoteTodayServiceTest 覆盖，这里不重复。
 */
@DisplayName("当日小纸条应用端控制器契约测试")
class DailyNoteAppControllerTest {

    @Test
    @DisplayName("控制器映射到 /app/daily-note，today 端点为 GET /today 且要求普通用户权限")
    void routeAndPermissionContract() throws Exception {
        RequestMapping controllerRoute = DailyNoteAppController.class.getAnnotation(RequestMapping.class);
        assertThat(controllerRoute.value()).containsExactly("/app/daily-note");

        Method today = DailyNoteAppController.class.getMethod("today");
        assertThat(today.getAnnotation(GetMapping.class).value()).containsExactly("/today");
        // 应用端要求普通用户认证权限；用户身份只来自认证 Token，不接受请求参数
        assertThat(today.getAnnotation(RequiresPermissions.class).value()).containsExactly("sys:role:normal");
        assertThat(today.getParameterCount()).isZero();
    }

    @Test
    @DisplayName("today 将 Token 解析出的用户 ID 传给 Service，结果包装进统一信封")
    void todayPassesTokenUserIdAndWrapsEnvelope() {
        DailyNoteTodayService service = mock(DailyNoteTodayService.class);
        DailyNoteAppController controller = new DailyNoteAppController(service);
        DailyNoteTodayVO expected = new DailyNoteTodayVO();
        when(service.today(7L)).thenReturn(expected);

        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(7L);

            Result<DailyNoteTodayVO> result = controller.today();

            assertThat(result.getCode()).isZero();
            assertThat(result.getMsg()).isEqualTo("success");
            assertThat(result.getData()).isSameAs(expected);
            verify(service).today(7L);
        }
    }

    @Test
    @DisplayName("无内容或无合格游戏时 Service 返回 null，响应为 code=0 且 data=null 的成功空结果")
    void todayNullBecomesSuccessWithNullData() {
        DailyNoteTodayService service = mock(DailyNoteTodayService.class);
        DailyNoteAppController controller = new DailyNoteAppController(service);
        when(service.today(null)).thenReturn(null);

        Result<DailyNoteTodayVO> result = controller.today();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNull();
    }
}
