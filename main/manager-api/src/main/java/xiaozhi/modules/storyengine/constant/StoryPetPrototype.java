package xiaozhi.modules.storyengine.constant;

/**
 * 后端固定支持的宠物原型。故事状态按原型维度计算与共享，同原型所有宠物读取同一条记录。
 * 当前固定为锦鲤、玉兔，新增原型需同步扩展状态占位数据。
 */
public enum StoryPetPrototype {
    KOI("锦鲤"),
    RABBIT("玉兔");

    private final String value;

    StoryPetPrototype(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
