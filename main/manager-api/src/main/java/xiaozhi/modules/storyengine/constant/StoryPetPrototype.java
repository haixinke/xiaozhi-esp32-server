package xiaozhi.modules.storyengine.constant;

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
