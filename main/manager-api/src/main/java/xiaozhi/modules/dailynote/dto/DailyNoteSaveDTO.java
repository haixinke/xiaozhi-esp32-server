package xiaozhi.modules.dailynote.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小纸条保存 DTO（新增 POST /admin/daily-note 与编辑 PUT /admin/daily-note/{id} 共用）。
 * noteDate 只接收严格 yyyy-MM-dd 字符串（Jackson 对 Date 的反序列化会宽松接受时间戳等格式，
 * 字符串接收后由 Service 用 ResolverStyle.STRICT 解析，非法日期明确拒绝）。
 * 首尾空白去除、Unicode 字符长度上限在 Service 层统一校验（trim 后判空/判长，
 * 注解只能基于原始值校验，无法表达"trim 后"语义）。
 */
@Data
@Schema(description = "小纸条保存")
public class DailyNoteSaveDTO implements Serializable {

    @Schema(description = "完整日期，严格 yyyy-MM-dd；允许过去、今天和未来")
    @NotBlank(message = "日期不能为空")
    private String noteDate;

    @Schema(description = "标题，去首尾空白后 1-50 字")
    @NotBlank(message = "标题不能为空")
    private String title;

    @Schema(description = "正文，去首尾空白后 1-200 字，保留内部换行")
    @NotBlank(message = "正文不能为空")
    private String content;

    @Schema(description = "节气（可选，任意文案，最多 50 字）")
    private String solarTerm;
}
