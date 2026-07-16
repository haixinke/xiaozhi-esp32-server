package xiaozhi.modules.pet.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pet.config.PetCollectionCardProperties;
import xiaozhi.modules.pet.dao.PetCollectionCardDao;
import xiaozhi.modules.pet.entity.PetCollectionCardEntity;
import xiaozhi.modules.pet.service.PetCollectionCardService;
import xiaozhi.modules.pet.vo.CollectionCardVO;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetCollectionCardServiceImpl implements PetCollectionCardService {

    private static final int MAX_CARDS = 10;

    private final PetCollectionCardDao petCollectionCardDao;
    private final PetCollectionCardProperties petCollectionCardProperties;

    @Override
    public List<CollectionCardVO> listByPetId(String petId) {
        QueryWrapper<PetCollectionCardEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("pet_id", petId);
        wrapper.orderByAsc("sort_order");
        List<PetCollectionCardEntity> entities = petCollectionCardDao.selectList(wrapper);
        return entities.stream()
                .sorted(Comparator.comparingInt(PetCollectionCardEntity::getSortOrder))
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public CollectionCardVO createCard(String petId, String prototype, String brief, String source) {
        // 查询已有卡片
        QueryWrapper<PetCollectionCardEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("pet_id", petId);
        List<PetCollectionCardEntity> existing = petCollectionCardDao.selectList(wrapper);

        if (existing.size() >= MAX_CARDS) {
            throw new RenException(ErrorCode.PET_COLLECTION_CARD_LIMIT_REACHED);
        }

        // 已有图片集合
        List<String> usedUrls = existing.stream()
                .map(PetCollectionCardEntity::getImageUrl)
                .collect(Collectors.toList());

        // 从配置池中选不重复的图片
        String imageUrl = selectNonDuplicateUrl(prototype, usedUrls);

        // 计算 sort_order
        int sortOrder = existing.stream()
                .mapToInt(PetCollectionCardEntity::getSortOrder)
                .max()
                .orElse(-1) + 1;

        // 插入记录
        PetCollectionCardEntity entity = new PetCollectionCardEntity();
        entity.setPetId(petId);
        entity.setImageUrl(imageUrl);
        entity.setBrief(brief);
        entity.setSource(source);
        entity.setSortOrder(sortOrder);
        petCollectionCardDao.insert(entity);

        log.info("收藏卡创建 petId={}, sortOrder={}, source={}", petId, sortOrder, source);
        return toVO(entity);
    }

    /**
     * 从配置池中随机选一张未被该宠物使用的图片 URL。
     */
    private String selectNonDuplicateUrl(String prototype, List<String> usedUrls) {
        PetCollectionCardProperties.Prototype config = selectConfig(prototype);
        if (config == null || !config.hasImage()) {
            return petCollectionCardProperties.getFallbackUrl() == null ? "" : petCollectionCardProperties.getFallbackUrl();
        }

        // 生成所有可用 URL，排除已使用的
        List<String> available = java.util.stream.IntStream.range(0, config.getCount())
                .mapToObj(i -> config.getBaseUrl() + config.getPrefix() + "-" + i + ".webp")
                .filter(url -> !usedUrls.contains(url))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            throw new RenException(ErrorCode.PET_COLLECTION_CARD_LIMIT_REACHED);
        }

        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private PetCollectionCardProperties.Prototype selectConfig(String prototype) {
        if ("锦鲤".equals(prototype)) {
            return petCollectionCardProperties.getKoi();
        }
        if ("玉兔".equals(prototype)) {
            return petCollectionCardProperties.getRabbit();
        }
        return null;
    }

    private CollectionCardVO toVO(PetCollectionCardEntity entity) {
        CollectionCardVO vo = new CollectionCardVO();
        vo.setId(entity.getId());
        vo.setImageUrl(entity.getImageUrl());
        vo.setBrief(entity.getBrief());
        vo.setSource(entity.getSource());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreateDate(entity.getCreateDate());
        return vo;
    }
}
