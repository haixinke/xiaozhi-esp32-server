package xiaozhi.modules.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 宠物明信片视图对象（公开只读）：仅暴露分享所需字段，
 * 不含 user_id / pet_id 等内部标识，避免公开接口泄露归属性信息。
 */
@Data
@Schema(description = "宠物明信片(公开视图)")
public class PetPostcardPublicVO {

    @Schema(description = "宠物昵称")
    private String petName;

    @Schema(description = "宠物原型: 锦鲤/玉兔")
    private String prototype;

    @Schema(description = "明信片照片URL")
    private String imageUrl;

    @Schema(description = "明信片文字")
    private String caption;

    @Schema(description = "寄出时间(yyyy-MM-dd HH:mm)")
    private String createDate;
}
