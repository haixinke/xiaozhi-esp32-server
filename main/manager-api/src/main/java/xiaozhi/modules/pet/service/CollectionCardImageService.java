package xiaozhi.modules.pet.service;

import xiaozhi.modules.pet.entity.PetEntity;

/**
 * 破壳收藏卡图片生成服务
 */
public interface CollectionCardImageService {

    /**
     * 为指定宠物生成破壳收藏卡图片，上传到 OSS 后返回可访问 URL。
     * 生成失败时不抛异常，返回 null，由调用方降级处理。
     *
     * @param pet 已破壳的宠物实体
     * @return OSS 图片 URL，失败返回 null
     */
    String generate(PetEntity pet);
}
