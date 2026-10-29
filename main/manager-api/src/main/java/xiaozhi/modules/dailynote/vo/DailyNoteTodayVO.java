package xiaozhi.modules.dailynote.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.game.vo.GameVO;

/**
 * 当日小纸条应用端 VO。
 * 只暴露客户端展示所需的字段（日期、标题、正文、节气、游戏），
 * 不下发小纸条主键 ID 与后台审计字段（创建人/更新时间等）。
 */
@Data
@Schema(description = "当日小纸条")
public class DailyNoteTodayVO implements Serializable {

    @Schema(description = "完整日期（yyyy-MM-dd，Asia/Shanghai 当天）")
    private String date;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文（纯文本，保留内部换行）")
    private String content;

    @Schema(description = "节气（可选，任意文案）")
    private String solarTerm;

    @Schema(description = "随机选中的启用游戏（入口含 NOTE），结构复用游戏配置 VO")
    private GameVO game;
}
