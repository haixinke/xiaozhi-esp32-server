package xiaozhi.modules.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户画像视图对象
 *
 * @author TDD
 * @version 1.0, 2025-05-16
 * @since 1.0.0
 */
@Data
@Schema(description = "用户画像视图对象")
public class UserProfileVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "画像内容")
    private String profileContent;

    @Schema(description = "主题标签")
    private String topics;

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "更新时间")
    private Date updatedAt;
}
