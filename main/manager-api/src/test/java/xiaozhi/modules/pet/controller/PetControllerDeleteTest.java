package xiaozhi.modules.pet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.lang.reflect.Method;
import java.util.Locale;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.DeleteMapping;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.service.HatchActionService;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.security.user.SecurityUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * PetController 删除宠物端点契约测试：路由、权限注解、用户身份透传、异常透传
 */
class PetControllerDeleteTest {

    @BeforeAll
    static void initMessageSource() {
        // RenException 构造依赖 i18n MessageSource，测试环境需 mock 掉 Spring 上下文
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    private PetService petService;
    private PetController controller;

    @BeforeEach
    void setUp() {
        petService = mock(PetService.class);
        controller = new PetController(petService, mock(HatchActionService.class));
    }

    @Test
    void deleteDelegatesAuthenticatedUserIdAndPathPetId() {
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(7L);

            Result<Void> result = controller.delete("pet-1");

            assertThat(result.getCode()).isZero();
            verify(petService).deleteByUserId(7L, "pet-1");
        }
    }

    @Test
    void deletePropagatesServiceBusinessException() {
        // 非本人/已删除/不存在统一由 service 抛 PET_NOT_FOUND，controller 原样透传
        doThrow(new RenException(ErrorCode.PET_NOT_FOUND))
                .when(petService).deleteByUserId(7L, "pet-1");

        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> controller.delete("pet-1"))
                    .isInstanceOfSatisfying(RenException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
        }
    }

    @Test
    void deleteExposesExactRouteAndPermissionContract() throws Exception {
        Method delete = PetController.class.getMethod("delete", String.class);

        DeleteMapping mapping = delete.getAnnotation(DeleteMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/{id}");

        RequiresPermissions permission = delete.getAnnotation(RequiresPermissions.class);
        assertThat(permission.value()).containsExactly("sys:role:normal");
    }
}
