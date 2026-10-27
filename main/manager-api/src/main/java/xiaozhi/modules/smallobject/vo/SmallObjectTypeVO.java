package xiaozhi.modules.smallobject.vo;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可选物件类型 VO，供新增弹窗的类型下拉使用。
 * key/name 来自字典 SMALL_OBJECT_TYPE 的小类；used 由后端统计未删除物件得出——
 * 前端列表是分页的，跨页的占用关系在前端不可见，所以不能靠前端自行推算。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "可选物件类型")
public class SmallObjectTypeVO implements Serializable {

    @Schema(description = "类型编码(字典 dict_value)")
    private String key;

    @Schema(description = "类型显示名(字典 dict_label)")
    private String name;

    @Schema(description = "是否已被未删除的物件占用")
    private Boolean used;
}