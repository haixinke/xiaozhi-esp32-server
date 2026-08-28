package xiaozhi.modules.security.oauth2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.apache.shiro.authc.IncorrectCredentialsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.security.entity.SysUserTokenEntity;
import xiaozhi.modules.security.service.ShiroService;

/**
 * Oauth2Realm 认证防御性测试：覆盖 token 与用户信息不一致的脏数据场景。
 */
class Oauth2RealmTest {

    /**
     * MessageUtils 依赖 Spring 上下文解析 i18n 文案，纯单测环境需要手动塞一个 mock，
     * 否则异常消息构造时会先 NPE。
     */
    @BeforeAll
    static void mockMessageSource() {
        MessageSource messageSource = mock(MessageSource.class);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean("messageSource")).thenReturn(messageSource);
        ReflectionTestUtils.setField(SpringContextUtils.class, "applicationContext", context);
    }

    /**
     * 还原全局静态字段，避免污染同 JVM 内其他测试类。
     */
    @AfterAll
    static void restoreSpringContext() {
        ReflectionTestUtils.setField(SpringContextUtils.class, "applicationContext", null);
        // MessageUtils 内部缓存了 messageSource 静态引用，一并清掉
        ReflectionTestUtils.setField(xiaozhi.common.utils.MessageUtils.class, "messageSource", null);
    }

    /**
     * 孤儿 token 场景：token 行存在且未过期，但对应 sys_user 已被删除。
     * 期望抛出正常的凭证异常（Shiro 会转成 401），而不是 NullPointerException。
     */
    @Test
    @DisplayName("token 有效但用户不存在时抛出 IncorrectCredentialsException 而非 NPE")
    void orphanTokenThrowsIncorrectCredentialsNotNpe() {
        ShiroService shiroService = mock(ShiroService.class);
        SysUserTokenEntity tokenEntity = new SysUserTokenEntity();
        tokenEntity.setUserId(999L);
        tokenEntity.setToken("orphan-token");
        tokenEntity.setExpireDate(new Date(System.currentTimeMillis() + 3600_000));
        when(shiroService.getByToken("orphan-token")).thenReturn(tokenEntity);
        // 用户已被手动删除：查不到
        when(shiroService.getUser(999L)).thenReturn(null);

        Oauth2Realm realm = new Oauth2Realm();
        ReflectionTestUtils.setField(realm, "shiroService", shiroService);

        assertThatThrownBy(() -> realm.doGetAuthenticationInfo(new Oauth2Token("orphan-token")))
                .isInstanceOf(IncorrectCredentialsException.class);
    }
}
