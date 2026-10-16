package xiaozhi.modules.game.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 游戏配置实体，对应 ai_game 表。
 * 游戏是星星激励体系的一部分，运营在智控台配置游戏参数。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_game")
public class GameEntity extends BaseEntity {

    /**
     * 游戏类型，对应字典 GAME_TYPE 的 code
     */
    private String gameType;

    /**
     * 赚取星星数，非负
     */
    private Integer earnStars;

    /**
     * 消耗星星数，非负
     */
    private Integer consumeStars;

    /**
     * 每日每设备可玩次数上限，0=不限
     */
    private Integer dailyLimit;

    /**
     * 游戏图标 OSS URL，可空
     */
    private String icon;

    /**
     * 游戏说明，可空
     */
    private String description;

    /**
     * 游戏入口，逗号分隔枚举 code，如 NOTE,ACTIVITY
     */
    private String entrances;

    /**
     * 状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateDate;
}
