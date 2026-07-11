package xiaozhi.modules.wechat.dto;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WechatProfileUpdateDTO Bean Validation")
class WechatProfileUpdateDTOTest {

    private final Validator validator;

    WechatProfileUpdateDTOTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("空 DTO 校验通过（全字段可选）")
    void emptyDto_isValid() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("16 字符昵称通过")
    void nickname_16chars_isValid() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setNickname("一二三四五六七八九〇一二三四五六");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("17 字符昵称不通过")
    void nickname_17chars_isInvalid() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setNickname("一二三四五六七八九〇一二三四五六七");
        assertThat(validator.validate(dto)).hasSize(1);
    }

    @Test
    @DisplayName("合法性别通过")
    void validGender_isValid() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setGender("OTHER");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("非法性别不通过")
    void invalidGender_isInvalid() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setGender("BOY");
        assertThat(validator.validate(dto)).hasSize(1);
    }

    @Test
    @DisplayName("合法 MBTI 通过")
    void validMbti_isValid() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setMbti("ENTP");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("非法 MBTI 不通过")
    void invalidMbti_isInvalid() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setMbti("AAAA");
        assertThat(validator.validate(dto)).hasSize(1);
    }

    @Test
    @DisplayName("生日字段可正常设置")
    void birthday_canBeSet() {
        WechatProfileUpdateDTO dto = new WechatProfileUpdateDTO();
        dto.setBirthday(LocalDate.of(2000, 1, 1));
        assertThat(dto.getBirthday()).isEqualTo(LocalDate.of(2000, 1, 1));
    }
}
