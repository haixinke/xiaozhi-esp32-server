package xiaozhi.modules.wechat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;

@ExtendWith(MockitoExtension.class)
class WechatPhoneGateTest {

    @Mock
    private WechatUserDao wechatUserDao;

    private WechatPhoneGate gate;

    @BeforeEach
    void setUp() {
        gate = new WechatPhoneGate(wechatUserDao);
    }

    @Test
    void rejectsAccountWithoutWechatMapping() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of());

        assertThat(gate.canAccess(7L)).isFalse();
    }

    @Test
    void rejectsNullMappingResult() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(null);

        assertThat(gate.canAccess(7L)).isFalse();
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

    private WechatUserEntity mapping(Long userId, String phone) {
        WechatUserEntity entity = new WechatUserEntity();
        entity.setUserId(userId);
        entity.setPhone(phone);
        return entity;
    }
}
