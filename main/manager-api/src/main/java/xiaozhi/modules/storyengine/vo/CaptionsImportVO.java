package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "故事引擎-图片文案批量导入结果")
public class CaptionsImportVO {

    @Schema(description = "实际更新文案的图片张数")
    private int updatedImages;

    @Schema(description = "跳过的Excel数据行数(不含表头)")
    private int skippedRows;

    @Schema(description = "跳过明细:行号+名称链路+原因")
    private List<String> skippedDetails = new ArrayList<>();

    public void addSkipped(int rowNumber, String bigScene, String smallScene, String action, String reason) {
        skippedRows++;
        skippedDetails.add("第" + rowNumber + "行 [" + bigScene + "/" + smallScene + "/" + action + "]: " + reason);
    }
}
