package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "宠物原型共享故事历史快照")
public class PetStoryHistoryVO extends PetStoryStateVO {

    @Schema(description = "归档时间")
    private Date archivedAt;
}
