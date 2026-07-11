package xiaozhi.modules.wechat.vo;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户资料视图
 */
@Data
@Schema(description = "用户资料视图")
public class WechatProfileVO {

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatarUrl;

    @Schema(description = "性别: MALE/FEMALE/OTHER")
    private String gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "常驻城市")
    private String city;

    @Schema(description = "MBTI类型")
    private String mbti;

    @Schema(description = "星座")
    private String zodiac;

    @Schema(description = "手机号（脱敏）")
    private String phone;
}
