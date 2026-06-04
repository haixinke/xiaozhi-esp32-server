package xiaozhi.modules.companion.util;

import java.util.Map;

public final class CharacterAge {

    private static final Map<String, Integer> AGE_MAP = Map.of(
            "linjiamei", 18,
            "erciyuan", 20,
            "baiyueguang", 22,
            "zhixingyujie", 25
    );

    private static final Map<String, String> NAME_MAP = Map.of(
            "linjiamei", "元气邻家妹",
            "erciyuan", "潮酷二次元",
            "baiyueguang", "高冷白月光",
            "zhixingyujie", "知性御姐"
    );

    private CharacterAge() {
    }

    public static int getAge(String characterCode) {
        Integer age = AGE_MAP.get(characterCode);
        if (age == null) {
            throw new IllegalArgumentException("未知角色编码: " + characterCode);
        }
        return age;
    }

    public static String getName(String characterCode) {
        String name = NAME_MAP.get(characterCode);
        if (name == null) {
            throw new IllegalArgumentException("未知角色编码: " + characterCode);
        }
        return name;
    }
}
