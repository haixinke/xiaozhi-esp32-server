package xiaozhi.modules.wechat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WechatPhoneGateTest {

    @Mock
    private WechatUserDao wechatUserDao;

    private WechatPhoneGate gate;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = org.mockito.Mockito.mock(ApplicationContext.class);
        MessageSource messageSource = org.mockito.Mockito.mock(MessageSource.class);
        lenient().when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
    }

    @BeforeEach
    void setUp() {
        gate = new WechatPhoneGate(wechatUserDao);
    }

    // --- canAccess tests (existing) ---

    @Test
    void allowsNonWechatAccount() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of());

        assertThat(gate.canAccess(7L)).isTrue();
    }

    @Test
    void rejectsWechatAccountWithoutPhone() {
        WechatUserEntity mapping = mapping(7L, null);
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of(mapping));

        assertThat(gate.canAccess(7L)).isFalse();
    }

    @Test
    void allowsWechatAccountWhenAnyMappingHasPhone() {
        WechatUserEntity blankPhone = mapping(7L, " ");
        WechatUserEntity boundPhone = mapping(7L, "13800000000");
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of(blankPhone, boundPhone));

        assertThat(gate.canAccess(7L)).isTrue();
    }

    // --- hasBoundWechatPhone tests (new) ---

    @Test
    void noWechatMappingIsNotEligibleForClaim() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of());

        assertThat(gate.hasBoundWechatPhone(7L)).isFalse();
    }

    @Test
    void emptyMappingIsNotEligibleForClaim() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of());

        assertThat(gate.hasBoundWechatPhone(7L)).isFalse();
    }

    @Test
    void blankPhoneMappingIsNotEligibleForClaim() {
        WechatUserEntity mapping = mapping(7L, " ");
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of(mapping));

        assertThat(gate.hasBoundWechatPhone(7L)).isFalse();
    }

    @Test
    void validPhoneMappingIsEligible() {
        WechatUserEntity mapping = mapping(7L, "13800138000");
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of(mapping));

        assertThat(gate.hasBoundWechatPhone(7L)).isTrue();
    }

    @Test
    void nullUserIdHasBoundWechatPhoneReturnsFalse() {
        assertThat(gate.hasBoundWechatPhone(null)).isFalse();
    }

    private WechatUserEntity mapping(Long userId, String phone) {
        WechatUserEntity entity = new WechatUserEntity();
        entity.setUserId(userId);
        entity.setPhone(phone);
        return entity;
    }
}
