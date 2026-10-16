package xiaozhi.modules.game.dto;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import xiaozhi.common.utils.DateUtils;
import xiaozhi.common.validator.group.AddGroup;
import xiaozhi.common.validator.group.DefaultGroup;
import xiaozhi.common.validator.group.UpdateGroup;

/**
 * 游戏配置 DTO，用于新增和更新请求。
 */
@Data
@Schema(description = "游戏配置")
public class GameDTO implements Serializable {

    @Schema(description = "id")
    @Null(message = "{id.null}", groups = AddGroup.class)
    @NotNull(message = "{id.require}", groups = UpdateGroup.class)
    private Long id;

    @Schema(description = "游戏类型")
    @NotBlank(message = "{game.type.require}", groups = DefaultGroup.class)
    private String gameType;

    @Schema(description = "赚取星星数")
    @NotNull(message = "{game.earnStars.require}", groups = DefaultGroup.class)
    @Min(value = 0, message = "{game.earnStars.min}", groups = DefaultGroup.class)
    private Integer earnStars;

    @Schema(description = "消耗星星数")
    @NotNull(message = "{game.consumeStars.require}", groups = DefaultGroup.class)
    @Min(value = 0, message = "{game.consumeStars.min}", groups = DefaultGroup.class)
    private Integer consumeStars;

    @Schema(description = "每日次数上限，0=不限")
    @NotNull(message = "{game.dailyLimit.require}", groups = DefaultGroup.class)
    @Min(value = 0, message = "{game.dailyLimit.min}", groups = DefaultGroup.class)
    private Integer dailyLimit;

    @Schema(description = "图标 OSS URL")
    private String icon;

    @Schema(description = "游戏说明")
    private String description;

    @Schema(description = "游戏入口，逗号分隔")
    @NotBlank(message = "{game.entrances.require}", groups = DefaultGroup.class)
    private String entrances;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "创建时间")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date createDate;

    @Schema(description = "更新时间")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(pattern = DateUtils.DATE_TIME_PATTERN)
    private Date updateDate;
}
