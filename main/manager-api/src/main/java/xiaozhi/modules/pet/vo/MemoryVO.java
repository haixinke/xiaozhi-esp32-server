package xiaozhi.modules.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 记忆视图对象
 *
 * @author TDD
 * @version 1.0, 2025-05-16
 * @since 1.0.0
 */
@Data
@Schema(description = "记忆视图对象")
public class MemoryVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "记忆分类")
    private String category;

    @Schema(description = "记忆文档内容")
    private String document;

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "更新时间")
    private Date updatedAt;
}
