package xiaozhi.modules.pet.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class PetNicknameGenerator {

    private static final List<String> DEFAULT_NICKNAMES = List.of(
            "小团子", "豆豆", "年糕", "糯米", "芒果",
            "布丁", "奶茶", "汤圆", "麻薯", "蛋挞",
            "泡芙", "曲奇", "核桃", "花生", "栗子",
            "松果", "糖果", "棉花", "椰果", "芋圆",
            "蜜桃", "柠檬", "樱桃", "蓝莓", "奥利奥"
    );

    private PetNicknameGenerator() {
    }

    public static String generate() {
        return DEFAULT_NICKNAMES.get(ThreadLocalRandom.current().nextInt(DEFAULT_NICKNAMES.size()));
    }
}
