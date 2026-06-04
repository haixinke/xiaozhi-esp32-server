package xiaozhi.modules.companion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "修改AI伴侣请求")
public class CompanionUpdateDTO {

    @NotBlank(message = "设备ID不能为空")
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;

    @Schema(description = "伴侣类型: gf=女友, bf=男友")
    private String type;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "默认图片URL")
    private String defaultImage;

    @Schema(description = "角色编码")
    private String character;

    @Schema(description = "职业编码")
    private String occupation;

    @Schema(description = "音色编码")
    private String voice;

    @Schema(description = "职业病描述")
    private String quirksText;

    @Schema(description = "灵魂特质,逗号分隔")
    private String soulTraits;

    @Schema(description = "小任性")
    private String soulQuirk;

    @Schema(description = "关系类型编码")
    private String relationType;

    @Schema(description = "宠物类型: cat/dog")
    private String petType;

    @Schema(description = "宠物名")
    private String petName;

    @Schema(description = "今日心情")
    private String mood;

    @Schema(description = "前世秘密")
    private String pastLifeSecret;
}
