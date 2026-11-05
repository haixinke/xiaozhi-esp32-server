package xiaozhi.modules.drawing.entity;

import java.time.LocalDate;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 一起画局次：只保存局次归属与完成事实，不保存定价、钱包或绘画轨迹。 */
@Data
@TableName("ai_drawing_session")
public class DrawingSessionEntity {
    @TableId(type = IdType.INPUT) private String id;
    private Long userId;
    private String requestId;
    private String referenceId;
    private LocalDate startedDay;
    private Date startedAt;
    private Date completedAt;
    private String rewardStatus;
}
