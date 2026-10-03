package xiaozhi.modules.pet.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI写真集视图对象：按天分组的成品写真时间轴。
 *
 * <p>只含 status=SUCCEEDED 的任务，按天倒序（最新日期在顶），无写真的日期不产生节点。
 * 分页单位是"天"而非"张"。
 */
@Data
@Schema(description = "AI写真集（按天分组）")
public class ImageGenGalleryVO {

    @Schema(description = "有写真的总天数")
    private long total;

    @Schema(description = "当前页码（从1开始）")
    private int page;

    @Schema(description = "每页天数")
    private int limit;

    @Schema(description = "本页日期节点，倒序")
    private List<DayVO> list;

    @Data
    @Schema(description = "单日写真节点")
    public static class DayVO {

        @Schema(description = "日期，格式 yyyy-MM-dd（Asia/Shanghai）")
        private String date;

        @Schema(description = "当日写真，按生成时间倒序（最新在顶）")
        private List<PhotoVO> photos;
    }

    @Data
    @Schema(description = "单张写真")
    public static class PhotoVO {

        @Schema(description = "任务ID")
        private String taskId;

        @Schema(description = "结果图OSS URL")
        private String resultUrl;

        @Schema(description = "生成时间，格式 yyyy-MM-dd HH:mm:ss")
        private String createTime;
    }
}
