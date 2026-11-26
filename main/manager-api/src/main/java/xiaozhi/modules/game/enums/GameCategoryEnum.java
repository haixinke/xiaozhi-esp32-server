package xiaozhi.modules.game.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 游戏分类枚举（写死代码，变更需发版）。
 * 区分游戏的打开方式：内部游戏进入小程序原生玩法，外部游戏在 web-view 中打开 H5。
 */
public enum GameCategoryEnum {

    /**
     * 内部游戏：小程序原生玩法（如一起画、听你说），走现有局次与星星结算
     */
    INTERNAL,

    /**
     * 外部游戏：H5 游戏，经 jump_url 在小程序 web-view 中打开，无星星结算与次数限制
     */
    EXTERNAL;

    /**
     * 所有合法 code 的集合，用于校验 gameCategory 字段
     */
    public static Set<String> codes() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toSet());
    }
}
