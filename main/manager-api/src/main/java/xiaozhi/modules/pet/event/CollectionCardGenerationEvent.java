package xiaozhi.modules.pet.event;

/**
 * 宠物破壳事务提交后触发收藏卡生成。
 */
public record CollectionCardGenerationEvent(String petId) {
}
