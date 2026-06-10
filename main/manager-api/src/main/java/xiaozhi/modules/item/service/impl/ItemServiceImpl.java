package xiaozhi.modules.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.item.dao.ItemConsumeLogDao;
import xiaozhi.modules.item.dao.ItemGrantLogDao;
import xiaozhi.modules.item.dao.ItemSkuDao;
import xiaozhi.modules.item.dao.UserItemDao;
import xiaozhi.modules.item.entity.ItemConsumeLogEntity;
import xiaozhi.modules.item.entity.ItemGrantLogEntity;
import xiaozhi.modules.item.entity.ItemSkuEntity;
import xiaozhi.modules.item.entity.UserItemEntity;
import xiaozhi.modules.item.enums.ItemCategory;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.item.vo.ItemSkuVO;
import xiaozhi.modules.item.vo.UserItemVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemSkuDao itemSkuDao;
    private final UserItemDao userItemDao;
    private final ItemGrantLogDao grantLogDao;
    private final ItemConsumeLogDao consumeLogDao;

    /** 查询上架的道具SKU列表，可按分类过滤 */
    @Override
    public List<ItemSkuVO> listSkus(String category) {
        QueryWrapper<ItemSkuEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        if (StringUtils.isNotBlank(category)) {
            wrapper.eq("category", category);
        }
        wrapper.orderByAsc("sort");
        List<ItemSkuEntity> entities = itemSkuDao.selectList(wrapper);
        List<ItemSkuVO> result = new ArrayList<>(entities.size());
        for (ItemSkuEntity entity : entities) {
            result.add(ItemSkuVO.toVO(entity));
        }
        return result;
    }

    /** 根据道具编码查询SKU实体 */
    @Override
    public ItemSkuEntity getBySkuCode(String skuCode) {
        if (StringUtils.isBlank(skuCode)) {
            return null;
        }
        return itemSkuDao.selectOne(new QueryWrapper<ItemSkuEntity>().eq("sku_code", skuCode));
    }

    /** 根据道具ID查询SKU实体，不存在则抛异常 */
    @Override
    public ItemSkuEntity getBySkuId(Long skuId) {
        if (skuId == null) {
            throw new RenException(ErrorCode.ITEM_SKU_NOT_FOUND);
        }
        ItemSkuEntity entity = itemSkuDao.selectById(skuId);
        if (entity == null) {
            throw new RenException(ErrorCode.ITEM_SKU_NOT_FOUND);
        }
        return entity;
    }

    /** 查询用户道具库存 */
    @Override
    public List<UserItemVO> myInventory(Long userId) {
        QueryWrapper<UserItemEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        List<UserItemEntity> items = userItemDao.selectList(wrapper);
        if (items.isEmpty()) {
            return List.of();
        }

        // 批量查询 SKU 元信息
        List<String> codes = items.stream().map(UserItemEntity::getSkuCode).toList();
        QueryWrapper<ItemSkuEntity> skuWrapper = new QueryWrapper<>();
        skuWrapper.in("sku_code", codes);
        List<ItemSkuEntity> skus = itemSkuDao.selectList(skuWrapper);
        Map<String, ItemSkuEntity> skuMap = new HashMap<>();
        for (ItemSkuEntity sku : skus) {
            skuMap.put(sku.getSkuCode(), sku);
        }

        List<UserItemVO> result = new ArrayList<>(items.size());
        for (UserItemEntity item : items) {
            ItemSkuEntity sku = skuMap.get(item.getSkuCode());
            String name = sku != null ? sku.getSkuName() : item.getSkuCode();
            String cat = sku != null ? sku.getCategory() : "";
            result.add(UserItemVO.toVO(item, name, cat));
        }
        return result;
    }

    /** 发放道具到用户库存（幂等，基于sourceRef+skuCode去重） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grant(Long userId, String skuCode, int count, String source, String sourceRef) {
        if (count <= 0) {
            return;
        }
        ItemSkuEntity sku = getBySkuCode(skuCode);
        if (sku == null) {
            throw new RenException(ErrorCode.ITEM_SKU_NOT_FOUND);
        }

        // 1) 写入发放流水（基于 uk_source_ref_sku 实现幂等）
        ItemGrantLogEntity grant = new ItemGrantLogEntity();
        grant.setUserId(userId);
        grant.setSkuCode(skuCode);
        grant.setCount(count);
        grant.setSource(source);
        grant.setSourceRef(sourceRef);
        try {
            grantLogDao.insert(grant);
        } catch (DuplicateKeyException dup) {
            // 已发放过，幂等返回
            log.info("道具已发放过，跳过：userId={}, sku={}, ref={}", userId, skuCode, sourceRef);
            return;
        }

        // 2) 更新或创建用户库存（处理并发 INSERT 冲突）
        UserItemEntity userItem = userItemDao.selectOne(
                new QueryWrapper<UserItemEntity>().eq("user_id", userId).eq("sku_code", skuCode));
        if (userItem == null) {
            try {
                userItem = new UserItemEntity();
                userItem.setUserId(userId);
                userItem.setSkuCode(skuCode);
                userItem.setTotalCount(count);
                userItem.setUsedCount(0);
                userItem.setRemainCount(count);
                userItemDao.insert(userItem);
                // INSERT 成功，库存已就绪
                return;
            } catch (DuplicateKeyException e) {
                // 并发场景：另一事务已插入，重查后走更新
                log.info("用户库存并发插入冲突，回退到更新：userId={}, sku={}", userId, skuCode);
                userItem = userItemDao.selectOne(
                        new QueryWrapper<UserItemEntity>().eq("user_id", userId).eq("sku_code", skuCode));
            }
        }
        // 已有记录（初始查到 或 并发重查），累加库存
        if (userItem == null) {
            throw new IllegalStateException("用户库存记录不应为空: userId=" + userId + ", sku=" + skuCode);
        }
        userItem.setTotalCount(userItem.getTotalCount() + count);
        userItem.setRemainCount(userItem.getRemainCount() + count);
        userItemDao.updateById(userItem);
    }

    /** 消耗道具（服装类仅校验拥有不扣减，其余按次原子扣减） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long userId, String skuCode, int count, String bizType, String bizRefId) {
        if (count <= 0) {
            return;
        }
        ItemSkuEntity sku = getBySkuCode(skuCode);
        if (sku == null) {
            throw new RenException(ErrorCode.ITEM_SKU_NOT_FOUND);
        }

        if (ItemCategory.isOutfit(sku.getCategory())) {
            // 服装：拥有即可，不递减
            if (!owns(userId, skuCode)) {
                throw new RenException(ErrorCode.ITEM_INSUFFICIENT);
            }
            // 记录穿戴流水
            recordConsume(userId, skuCode, 0, bizType, bizRefId, "outfit equip");
            return;
        }

        // 一次性消耗：原子扣减
        int affected = userItemDao.deductRemain(userId, skuCode, count);
        if (affected <= 0) {
            throw new RenException(ErrorCode.ITEM_INSUFFICIENT);
        }
        recordConsume(userId, skuCode, count, bizType, bizRefId, null);
    }

    /** 判断用户是否拥有指定道具 */
    @Override
    public boolean owns(Long userId, String skuCode) {
        UserItemEntity ui = userItemDao.selectOne(
                new QueryWrapper<UserItemEntity>().eq("user_id", userId).eq("sku_code", skuCode));
        return ui != null && ui.getRemainCount() != null && ui.getRemainCount() > 0;
    }

    private void recordConsume(Long userId, String skuCode, int count, String bizType, String bizRefId, String remark) {
        ItemConsumeLogEntity entry = new ItemConsumeLogEntity();
        entry.setUserId(userId);
        entry.setSkuCode(skuCode);
        entry.setCount(count);
        entry.setBizType(bizType);
        entry.setBizRefId(bizRefId);
        entry.setRemark(remark);
        consumeLogDao.insert(entry);
    }
}
