package xiaozhi.modules.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;

import xiaozhi.modules.security.oauth2.Oauth2Realm;
import xiaozhi.modules.sys.service.SysParamsService;

class ShiroConfigTest {

    @Test
    void shiroFilterFactoryUsesCurrentSignatureAndStaysABeanPostProcessor() throws Exception {
        // 现行实现为实例 @Bean 工厂（非 static），签名以 SecurityManager 接口声明
        Method lifecycleFactory = ShiroConfig.class.getDeclaredMethod("lifecycleBeanPostProcessor");
        Method filterFactory = ShiroConfig.class.getDeclaredMethod(
                "shirFilter", SecurityManager.class, SysParamsService.class);

        assertTrue(Modifier.isPublic(lifecycleFactory.getModifiers()));
        assertTrue(BeanPostProcessor.class.isAssignableFrom(lifecycleFactory.getReturnType()));
        // ShiroFilterFactoryBean 本身是 BeanPostProcessor，这是过早实例化警告的根源
        assertTrue(BeanPostProcessor.class.isAssignableFrom(ShiroFilterFactoryBean.class));
        assertEquals(ShiroFilterFactoryBean.class, filterFactory.getReturnType());
    }

    @Test
    void authorizationAdvisorUsesCurrentSignature() throws Exception {
        // 现行实现无 @Lazy / @Role 注解，参数以 SecurityManager 接口声明
        Method advisorFactory = ShiroConfig.class.getDeclaredMethod(
                "authorizationAttributeSourceAdvisor", SecurityManager.class);

        assertTrue(Modifier.isPublic(advisorFactory.getModifiers()));
        assertEquals(AuthorizationAttributeSourceAdvisor.class, advisorFactory.getReturnType());
    }

    @Test
    void securityManagerBeanSatisfiesTheWebSecurityContractRequiredByShiroFilter() throws Exception {
        Method securityManagerFactory = ShiroConfig.class.getDeclaredMethod(
                "securityManager", Oauth2Realm.class, SessionManager.class);

        // 声明返回类型为 SecurityManager 接口；ShiroFilterFactoryBean 要求实际 bean 是 WebSecurityManager
        assertEquals(SecurityManager.class, securityManagerFactory.getReturnType());
        SecurityManager bean = (SecurityManager) securityManagerFactory.invoke(
                new ShiroConfig(), mock(Oauth2Realm.class), mock(SessionManager.class));
        assertInstanceOf(WebSecurityManager.class, bean);
    }
}
