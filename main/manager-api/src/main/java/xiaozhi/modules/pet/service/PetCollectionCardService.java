package xiaozhi.modules.pet.service;

import java.util.List;

import xiaozhi.modules.pet.vo.CollectionCardVO;

public interface PetCollectionCardService {

    /**
     * 按 sortOrder 升序返回宠物全部收藏卡。
     */
    List<CollectionCardVO> listByPetId(String petId);

    /**
     * 创建新收藏卡：校验上限(10张)、选不重复图片、算 sort_order、插入记录。
     *
     * @param petId     宠物ID
     * @param prototype 宠物原型(锦鲤/玉兔)
     * @param brief     一句话简介
     * @param source    来源类型(如 HATCH)
     * @return 创建后的收藏卡 VO
     */
    CollectionCardVO createCard(String petId, String prototype, String brief, String source);
}
