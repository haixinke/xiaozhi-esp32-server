package xiaozhi.modules.storyengine.service;

import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;
import xiaozhi.modules.storyengine.vo.CaptionsImportVO;

public interface StoryActionImageService extends BaseService<ActionImageEntity> {

    /**
     * 上传动作图片到OSS并落库。
     *
     * @param actionId     所属动作ID
     * @param petPrototype 宠物原型: 锦鲤/玉兔
     * @param timeOfDay    时段类型: 白天/落日/黑夜
     * @param captions     图片配文，多句用|分隔
     * @param tag          图片标签(管理端分类标注,单标签,最长64字符)，传空表示不打标签
     * @param file         图片文件
     */
    void uploadImage(String actionId, String petPrototype, String timeOfDay, String captions, String tag,
            MultipartFile file);

    /**
     * 修改已上传图片的配文与标签（整体更新语义，前端需将两个字段一起提交）。
     *
     * @param id       图片ID
     * @param captions 图片配文，多句用|分隔；传空表示清空配文
     * @param tag      图片标签(单标签,最长64字符)；传空表示清空标签
     */
    void updateInfo(String id, String captions, String tag);

    /**
     * 删除单张图片，同时清理OSS文件。
     */
    void delete(String id);

    /**
     * 删除某个动作下的全部图片，同时清理OSS文件。
     */
    void deleteByActionId(String actionId);

    /**
     * 通过Excel模版批量更新图片配文。
     * <p>
     * 模版列: 大场景 | 小场景 | 动作 | 时段 | 宠物类型 | 图片文案。
     * 按名称逐级匹配大场景/小场景/动作，任一级对不上则跳过该行并记录原因；
     * 命中后按 宠物类型+时段 定位图片分组，组内全部图片统一更新为该文案。
     * 命中特殊场景规则的组合(如:在家/卧室)时，带特殊标签(如:窗户)的图片不更新。
     *
     * @param file Excel文件(.xlsx)
     * @return 更新图片数与跳过行明细
     */
    CaptionsImportVO importCaptions(MultipartFile file);
}
