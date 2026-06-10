package xiaozhi.modules.item.service;

import xiaozhi.modules.item.entity.ItemSkuEntity;
import xiaozhi.modules.item.vo.ItemSkuVO;
import xiaozhi.modules.item.vo.UserItemVO;

import java.util.List;

/**
 * 道具服务
 */
public interface ItemService {

    /** 列出 SKU；category 为 null 返回全部上架 */
    List<ItemSkuVO> listSkus(String category);

    /** 根据 SKU 编码查询；不存在返回 null */
    ItemSkuEntity getBySkuCode(String skuCode);

    /** 根据 SKU id 查询；不存在抛 ITEM_SKU_NOT_FOUND */
    ItemSkuEntity getBySkuId(Long skuId);

    /** 列出当前用户全部道具库存 */
    List<UserItemVO> myInventory(Long userId);

    /**
     * 发放道具到用户库存（幂等：基于 source_ref + sku_code 唯一索引去重）
     */
    void grant(Long userId, String skuCode, int count, String source, String sourceRef);

    /**
     * 消耗用户道具：库存不足抛 ITEM_INSUFFICIENT
     * - 服装类(outfit) 仅校验拥有 (remain>0)，不递减
     * - 其余按 count 扣减
     */
    void consume(Long userId, String skuCode, int count, String bizType, String bizRefId);

    /** 用户是否拥有某 outfit / sku（remain>0） */
    boolean owns(Long userId, String skuCode);
}
