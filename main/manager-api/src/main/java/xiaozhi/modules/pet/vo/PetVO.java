package xiaozhi.modules.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "宠物视图对象")
public class PetVO {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "归属用户ID")
    private Long userId;

    @Schema(description = "关联设备ID")
    private String deviceId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "出生日期时间")
    private Date birthDate;

    @Schema(description = "八字")
    private String bazi;

    @Schema(description = "五行")
    private String wuxing;

    @Schema(description = "星座英文编码")
    private String zodiac;

    @Schema(description = "MBTI人格")
    private String mbti;

    @Schema(description = "创建时间")
    private Date createDate;
}
