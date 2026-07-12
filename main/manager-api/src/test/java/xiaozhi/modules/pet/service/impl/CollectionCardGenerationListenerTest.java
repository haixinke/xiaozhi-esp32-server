package xiaozhi.modules.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.event.CollectionCardGenerationEvent;
import xiaozhi.modules.pet.service.CollectionCardImageService;

import java.lang.reflect.Method;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("收藏卡生成异步监听器测试")
class CollectionCardGenerationListenerTest {

    @Mock
    private PetDao petDao;

    @Mock
    private CollectionCardImageService collectionCardImageService;

    @Test
    @DisplayName("generate - 事务提交后异步监听")
    void generate_isAsyncAfterCommitListener() throws NoSuchMethodException {
        Method method = CollectionCardGenerationListener.class
                .getDeclaredMethod("generate", CollectionCardGenerationEvent.class);

        Async async = method.getAnnotation(Async.class);
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("taskExecutor");
        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("generate - AI生成成功后只回写collection_card_url字段")
    @SuppressWarnings("unchecked")
    void generate_success_updatesOnlyCollectionCardUrl() {
        CollectionCardGenerationListener listener = new CollectionCardGenerationListener(
                petDao, collectionCardImageService);
        PetEntity pet = hatchedPet();
        when(petDao.selectById("pet-1")).thenReturn(pet);
        when(collectionCardImageService.generate(pet)).thenReturn("https://oss.example.com/card.png");
        when(petDao.update(eq(null), org.mockito.ArgumentMatchers.any(UpdateWrapper.class))).thenReturn(1);

        listener.generate(new CollectionCardGenerationEvent("pet-1"));

        verify(collectionCardImageService).generate(pet);
        ArgumentCaptor<UpdateWrapper<PetEntity>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(petDao).update(eq(null), wrapperCaptor.capture());

        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertThat(sqlSet).contains("collection_card_url=");
        assertThat(sqlSet).contains("updater=");
        assertThat(sqlSet).contains("update_date=");
        assertThat(sqlSet).doesNotContain("hatch_status=");
        assertThat(sqlSet).doesNotContain("device_id=");
        verify(petDao, never()).updateById(pet);
    }

    @Test
    @DisplayName("generate - 已有收藏卡时跳过AI调用")
    void generate_existingCardUrl_skipsGeneration() {
        CollectionCardGenerationListener listener = new CollectionCardGenerationListener(
                petDao, collectionCardImageService);
        PetEntity pet = hatchedPet();
        pet.setCollectionCardUrl("https://oss.example.com/existing.png");
        when(petDao.selectById("pet-1")).thenReturn(pet);

        listener.generate(new CollectionCardGenerationEvent("pet-1"));

        verify(collectionCardImageService, never()).generate(pet);
        verify(petDao, never()).update(eq(null), org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
    }

    private PetEntity hatchedPet() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-1");
        pet.setUserId(1001L);
        pet.setHatchStatus("HATCHED");
        pet.setHatchedAt(new Date());
        pet.setNickname("小蛋");
        pet.setPrototype("玉兔");
        return pet;
    }
}
