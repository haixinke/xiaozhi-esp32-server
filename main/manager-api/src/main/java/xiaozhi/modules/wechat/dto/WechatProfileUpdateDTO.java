package xiaozhi.modules.wechat.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户资料更新请求
 */
@Data
@Schema(description = "用户资料更新请求")
public class WechatProfileUpdateDTO {

    @Size(max = 16, message = "昵称最多16个字符")
    @Schema(description = "昵称")
    private String nickname;

    @Size(max = 512, message = "头像URL过长")
    @Schema(description = "头像URL")
    private String avatarUrl;

    @Pattern(regexp = "MALE|FEMALE|OTHER", message = "性别格式错误")
    @Schema(description = "性别: MALE/FEMALE/OTHER")
    private String gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Size(max = 32, message = "城市最多32个字符")
    @Schema(description = "常驻城市")
    private String city;

    @Pattern(regexp = "INFP|INFJ|INTJ|INTP|ENFP|ENFJ|ENTJ|ENTP|ISFP|ISFJ|ISTJ|ISTP|ESFP|ESFJ|ESTJ|ESTP",
            message = "MBTI类型错误")
    @Schema(description = "MBTI类型")
    private String mbti;

    @Size(max = 50, message = "年龄区间过长")
    @Schema(description = "年龄区间（字典 EGG_AGE_RANGE 的 dict_value）")
    private String ageRange;
}
