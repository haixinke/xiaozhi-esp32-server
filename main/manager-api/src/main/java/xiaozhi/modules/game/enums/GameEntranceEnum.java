package xiaozhi.modules.game.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 游戏入口枚举（写死代码，变更需发版）。
 * 标识游戏在小程序中的投放位置。
 */
public enum GameEntranceEnum {

    /**
     * 小纸条入口
     */
    NOTE("小纸条"),

    /**
     * 活动入口
     */
    ACTIVITY("活动");

    private final String label;

    GameEntranceEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 所有合法 code 的集合，用于校验 entrances 字段
     */
    public static Set<String> codes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toSet());
    }
}
