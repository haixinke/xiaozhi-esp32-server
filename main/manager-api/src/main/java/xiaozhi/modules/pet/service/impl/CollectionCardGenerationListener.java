package xiaozhi.modules.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.event.CollectionCardGenerationEvent;
import xiaozhi.modules.pet.service.CollectionCardImageService;

import java.util.Date;

/**
 * 破壳主事务提交后异步生成收藏卡，避免外部 AI 调用占用数据库行锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionCardGenerationListener {

    private static final String HATCH_STATUS_HATCHED = "HATCHED";

    private final PetDao petDao;
    private final CollectionCardImageService collectionCardImageService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(CollectionCardGenerationEvent event) {
        if (event == null || StringUtils.isBlank(event.petId())) {
            return;
        }

        PetEntity pet = petDao.selectById(event.petId());
        if (pet == null) {
            log.warn("收藏卡生成跳过，宠物不存在，petId={}", event.petId());
            return;
        }
        if (!HATCH_STATUS_HATCHED.equals(pet.getHatchStatus())) {
            log.warn("收藏卡生成跳过，宠物未破壳，petId={}", event.petId());
            return;
        }
        if (StringUtils.isNotBlank(pet.getCollectionCardUrl())) {
            log.info("收藏卡已存在，跳过生成，petId={}", event.petId());
            return;
        }

        String collectionCardUrl = collectionCardImageService.generate(pet);
        if (StringUtils.isBlank(collectionCardUrl)) {
            log.warn("收藏卡生成未返回URL，petId={}", event.petId());
            return;
        }

        UpdateWrapper<PetEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", pet.getId())
                .isNull("collection_card_url")
                .set("collection_card_url", collectionCardUrl)
                .set("updater", pet.getUserId())
                .set("update_date", new Date());

        int updated = petDao.update(null, wrapper);
        if (updated == 0) {
            log.info("收藏卡URL未写入，可能已被其他任务生成，petId={}", event.petId());
            return;
        }
        log.info("收藏卡URL写入成功，petId={}", event.petId());
    }
}
