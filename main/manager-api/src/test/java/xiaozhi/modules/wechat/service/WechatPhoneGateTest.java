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
import xiaozhi.modules.pet.dao.PetDao;


@ExtendWith(MockitoExtension.class)
class WechatPhoneGateTest {

    @Mock
    private WechatUserDao wechatUserDao;

    @Mock
    private PetDao petDao;

    private WechatPhoneGate gate;

    @BeforeEach
    void setUp() {
        gate = new WechatPhoneGate(wechatUserDao, petDao);
    }

    @Test
    void nullMappingResultDoesNotRequireBinding() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(null);

        assertThat(gate.requiresPhoneBinding(7L)).isFalse();
    }

    @Test
    void anyBoundMappingReleasesBindingRequirement() {
        WechatUserEntity blankPhone = mapping(7L, " ");
        WechatUserEntity boundPhone = mapping(7L, "13800000000");
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.<Wrapper<WechatUserEntity>>any()))
                .thenReturn(List.of(blankPhone, boundPhone));

        // 已绑定任一微信映射的账号不再要求绑定，破壳宠物也不参与判定。
        assertThat(gate.requiresPhoneBinding(7L)).isFalse();
    }

    /** 门槛依据账号持有的已破壳宠物，不取决于客户端当前选择。 */
    @Test
    void unboundWechatAccountWithHatchedPetRequiresBinding() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(mapping(7L, " ")));
        when(petDao.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

        assertThat(gate.requiresPhoneBinding(7L)).isTrue();
    }

    @Test
    void unboundWechatAccountWithoutHatchedPetCanExperienceIncubation() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(mapping(7L, null)));
        when(petDao.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        assertThat(gate.requiresPhoneBinding(7L)).isFalse();
    }

    @Test
    void ordinaryAccountWithoutWechatMappingKeepsExistingAccess() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        assertThat(gate.requiresPhoneBinding(7L)).isFalse();
    }

    @Test
    void boundAccountDoesNotRequireBindingAgain() {
        when(wechatUserDao.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(mapping(7L, "13800138000")));

        assertThat(gate.requiresPhoneBinding(7L)).isFalse();
    }

    @Test
    void anonymousRequestDoesNotUseAccountGate() {
        assertThat(gate.requiresPhoneBinding(null)).isFalse();
    }

    private WechatUserEntity mapping(Long userId, String phone) {
        WechatUserEntity entity = new WechatUserEntity();
        entity.setUserId(userId);
        entity.setPhone(phone);
        return entity;
    }
}
