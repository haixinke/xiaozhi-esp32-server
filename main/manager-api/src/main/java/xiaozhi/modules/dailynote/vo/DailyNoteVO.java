package xiaozhi.modules.dailynote.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小纸条 VO：列表与详情共用同一份完整内容。
 * 列表的"正文摘要"由智控台前端截断展示，后端不下发第二种字段，
 * 避免摘要截断规则双写漂移。
 */
@Data
@Schema(description = "小纸条")
public class DailyNoteVO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "完整日期")
    // LocalDate 是纯日历日，无时区概念，序列化无需指定 timezone
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate noteDate;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文（完整内容，保留内部换行）")
    private String content;

    @Schema(description = "节气（可选）")
    private String solarTerm;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateDate;
}
