package xiaozhi.modules.companion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建AI伴侣请求")
public class CompanionCreateDTO {

    @NotBlank(message = "设备ID不能为空")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;

    @NotBlank(message = "伴侣类型不能为空")
    @Schema(description = "伴侣类型: gf=女友, bf=男友", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotBlank(message = "头像不能为空")
    @Schema(description = "头像URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String avatar;

    @NotBlank(message = "默认图片不能为空")
    @Schema(description = "默认图片URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String defaultImage;

    @NotBlank(message = "角色不能为空")
    @Schema(description = "角色编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String character;

    @NotBlank(message = "职业不能为空")
    @Schema(description = "职业编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String occupation;

    @NotBlank(message = "音色不能为空")
    @Schema(description = "音色编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String voice;

    @Schema(description = "职业病描述")
    private String quirksText;

    @NotBlank(message = "灵魂特质不能为空")
    @Schema(description = "灵魂特质,逗号分隔", requiredMode = Schema.RequiredMode.REQUIRED)
    private String soulTraits;

    @NotBlank(message = "小任性不能为空")
    @Schema(description = "小任性", requiredMode = Schema.RequiredMode.REQUIRED)
    private String soulQuirk;

    @NotBlank(message = "关系类型不能为空")
    @Schema(description = "关系类型编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String relationType;

    @NotBlank(message = "宠物类型不能为空")
    @Schema(description = "宠物类型: cat/dog", requiredMode = Schema.RequiredMode.REQUIRED)
    private String petType;

    @NotBlank(message = "宠物名不能为空")
    @Schema(description = "宠物名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String petName;
}
