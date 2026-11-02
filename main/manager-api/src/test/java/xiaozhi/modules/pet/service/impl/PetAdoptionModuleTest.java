package xiaozhi.modules.pet.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.wechat.service.WechatPhoneGate;
import java.util.List;
import java.util.Locale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过领养 interface 验证名额、替换和存量读取，不依赖展示或 LLM。 */
class PetAdoptionModuleTest {
    private PetDao dao;
    private WechatPhoneGate phoneGate;
    private PetAdoptionModule module;

    @BeforeAll
    static void messages() {
        ApplicationContext context = mock(ApplicationContext.class);
        MessageSource messages = mock(MessageSource.class);
        when(context.getBean("messageSource")).thenReturn(messages);
        when(messages.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(call -> call.getArgument(2));
        SpringContextUtils.applicationContext = context;
    }

    @BeforeEach
    void setup() {
        dao = mock(PetDao.class);
        phoneGate = mock(WechatPhoneGate.class);
        module = new PetAdoptionModule(dao, phoneGate);
        when(phoneGate.canAccess(7L)).thenReturn(true);
    }

    @Test
    void nfcReplacesInviteBeforeCreatingEgg() {
        PetEntity invite = pet("old", "INVITE_CODE");
        when(dao.selectList(any())).thenReturn(List.of(invite));
        var result = module.claimNfcEgg(7L, "锦鲤");
        assertThat(result.replacedInvitePet()).isTrue();
        assertThat(result.pet().getSource()).isEqualTo("NFC");
        assertThat(invite.getDeletedAt()).isPositive();
        var order = inOrder(dao);
        order.verify(dao).lockAdoptionUser(7L);
        order.verify(dao).selectList(any());
        order.verify(dao).updateById(invite);
        order.verify(dao).insert(result.pet());
        verifyNoInteractions(phoneGate);
    }

    @Test
    void duplicateNfcDoesNotDeleteOrCreate() {
        when(dao.selectList(any())).thenReturn(List.of(pet("owned", "NFC")));
        assertThatThrownBy(() -> module.claimNfcEgg(7L, "锦鲤")).isInstanceOf(RenException.class);
        verify(dao, never()).updateById(any(PetEntity.class));
        verify(dao, never()).insert(any(PetEntity.class));
    }

    @Test
    void nfcOtherPrototypeDoesNotOccupyQuota() {
        when(dao.selectList(any())).thenReturn(List.of(pet("owned", "NFC")));
        assertThat(module.claimNfcEgg(7L, "玉兔").pet().getPrototype()).isEqualTo("玉兔");
    }

    @Test
    void noInviteMeansNoReplacement() {
        assertThat(module.claimNfcEgg(7L, "锦鲤").replacedInvitePet()).isFalse();
    }

    @Test
    void inviteCannotCoexistWithActivePet() {
        when(dao.selectList(any())).thenReturn(List.of(pet("owned", "NFC")));
        assertThatThrownBy(() -> module.createInviteEgg(7L, "锦鲤")).isInstanceOf(RenException.class);
        verify(dao, never()).insert(any(PetEntity.class));
    }

    @Test
    void inviteUsesSharedLockAndPreservesSource() {
        assertThat(module.createInviteEgg(7L, "玉兔").getSource()).isEqualTo("INVITE_CODE");
        verify(dao).lockAdoptionUser(7L);
    }

    @Test
    void invitePhoneGatePrecedesWriting() {
        when(phoneGate.canAccess(7L)).thenReturn(false);
        assertThatThrownBy(() -> module.createInviteEgg(7L, "玉兔")).isInstanceOf(RenException.class);
        verifyNoInteractions(dao);
    }

    @Test
    void eligibilityFailurePropagates() {
        when(dao.selectOne(any())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> module.findOwnedNfcPet(7L, "锦鲤")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void storedReadDoesNotWrite() {
        PetEntity pet = pet("p", "NFC");
        when(dao.selectById("p")).thenReturn(pet);
        assertThat(module.getStoredById(7L, "p")).isSameAs(pet);
        verify(dao, never()).updateById(any(PetEntity.class));
    }

    @Test
    void storedReadHidesDeletedPet() {
        PetEntity pet = pet("p", "NFC");
        pet.setDeletedAt(2L);
        when(dao.selectById("p")).thenReturn(pet);
        assertThatThrownBy(() -> module.getStoredById(7L, "p")).isInstanceOf(RenException.class);
    }

    @Test
    void failedReplacementPreventsCreation() {
        when(dao.selectList(any())).thenReturn(List.of(pet("old", "INVITE_CODE")));
        when(dao.updateById(any(PetEntity.class))).thenThrow(new IllegalStateException("write failed"));
        assertThatThrownBy(() -> module.claimNfcEgg(7L, "锦鲤")).isInstanceOf(IllegalStateException.class);
        verify(dao, never()).insert(any(PetEntity.class));
    }

    private PetEntity pet(String id, String source) {
        PetEntity result = new PetEntity();
        result.setId(id);
        result.setUserId(7L);
        result.setSource(source);
        result.setPrototype("锦鲤");
        result.setDeletedAt(0L);
        return result;
    }
}
