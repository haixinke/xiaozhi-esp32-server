package xiaozhi.modules.game.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
     * 游戏分类：INTERNAL 内部游戏(原生玩法)，EXTERNAL 外部游戏(H5跳转)，非空
     */
    private String gameCategory;

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
     * 跳转链接，仅外部游戏(EXTERNAL)使用；内部游戏恒为 NULL。
     * updateStrategy=ALWAYS：显式保存为内部游戏时服务端将本字段置空，
     * 默认的 NOT_NULL 策略会让 update 语句跳过 null 值导致旧链接清不掉。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String jumpUrl;

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
