package xiaozhi.modules.smallobject.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Data;
import xiaozhi.common.validator.group.AddGroup;
import xiaozhi.common.validator.group.DefaultGroup;
import xiaozhi.common.validator.group.UpdateGroup;

/**
 * 小物件 DTO，用于新增与更新请求。
 * 字段级校验只覆盖「必填/长度」这类与业务无关的形态约束；类型是否在字典中存在、
 * 是否已被占用等业务规则在 Service 中校验，避免把字典查询塞进注解。
 */
@Data
@Schema(description = "小物件")
public class SmallObjectDTO implements Serializable {

    /**
     * 故事长度上限，与 ai_small_object.story 列宽及前端 maxlength 保持一致
     */
    public static final int STORY_MAX_LENGTH = 2000;

    @Schema(description = "id，新增时必须为空，更新时必填")
    @Null(message = "{id.null}", groups = AddGroup.class)
    @NotNull(message = "{id.require}", groups = UpdateGroup.class)
    private Long id;

    @Schema(description = "物件类型(字典 SMALL_OBJECT_TYPE 的 dict_value)")
    @NotBlank(message = "{smallObject.type.require}", groups = DefaultGroup.class)
    private String objectType;

    @Schema(description = "物件图片完整 OSS URL")
    @NotBlank(message = "{smallObject.imageUrl.require}", groups = DefaultGroup.class)
    private String imageUrl;

    @Schema(description = "物件背后的故事，纯文本，最长 2000 字")
    @NotBlank(message = "{smallObject.story.require}", groups = DefaultGroup.class)
    @Size(max = STORY_MAX_LENGTH, message = "{smallObject.story.max}", groups = DefaultGroup.class)
    private String story;

    @Schema(description = "状态：1启用 0停用，新增不传时默认启用")
    private Integer status;
}