package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import xiaozhi.modules.dailynote.controller.DailyNoteController;
import xiaozhi.modules.dailynote.dto.DailyNoteSaveDTO;

/**
 * 小纸条管理控制器权限与路由契约测试。
 * 验证 /admin/daily-note 前缀下五个端点全部要求超级管理员权限（统一错误结构
 * 由全局 RenExceptionHandler 兜底，权限拒绝由 Shiro 过滤器链保证）。
 */
@DisplayName("小纸条管理控制器权限契约测试")
class DailyNoteControllerPermissionContractTest {

    private static final String SUPER_ADMIN_PERM = "sys:role:superAdmin";

    @Test
    @DisplayName("控制器映射到 /admin/daily-note 且全部端点要求超级管理员权限")
    void allEndpointsRequireSuperAdmin() throws Exception {
        RequestMapping controllerRoute = DailyNoteController.class.getAnnotation(RequestMapping.class);
        assertThat(controllerRoute.value()).containsExactly("/admin/daily-note");

        Method page = DailyNoteController.class.getMethod("page", Map.class);
        assertThat(page.getAnnotation(GetMapping.class).value()).containsExactly("/page");
        assertThat(page.getAnnotation(RequiresPermissions.class).value()).containsExactly(SUPER_ADMIN_PERM);

        Method get = DailyNoteController.class.getMethod("get", Long.class);
        assertThat(get.getAnnotation(GetMapping.class).value()).containsExactly("/{id}");
        assertThat(get.getAnnotation(RequiresPermissions.class).value()).containsExactly(SUPER_ADMIN_PERM);

        Method create = DailyNoteController.class.getMethod("create", DailyNoteSaveDTO.class);
        assertThat(create.getAnnotation(PostMapping.class)).isNotNull();
        assertThat(create.getAnnotation(RequiresPermissions.class).value()).containsExactly(SUPER_ADMIN_PERM);

        Method update = DailyNoteController.class.getMethod("update", Long.class, DailyNoteSaveDTO.class);
        assertThat(update.getAnnotation(PutMapping.class).value()).containsExactly("/{id}");
        assertThat(update.getAnnotation(RequiresPermissions.class).value()).containsExactly(SUPER_ADMIN_PERM);

        Method delete = DailyNoteController.class.getMethod("delete", Long.class);
        assertThat(delete.getAnnotation(DeleteMapping.class).value()).containsExactly("/{id}");
        assertThat(delete.getAnnotation(RequiresPermissions.class).value()).containsExactly(SUPER_ADMIN_PERM);

        // JSON 导入：multipart file，沿用超级管理员权限
        Method importNotes = DailyNoteController.class.getMethod("importNotes",
                org.springframework.web.multipart.MultipartFile.class);
        assertThat(importNotes.getAnnotation(PostMapping.class).value()).containsExactly("/import");
        assertThat(importNotes.getAnnotation(RequiresPermissions.class).value()).containsExactly(SUPER_ADMIN_PERM);
    }
}
