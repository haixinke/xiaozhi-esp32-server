package xiaozhi.modules.storyengine.service;

import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;

public interface StoryActionImageService extends BaseService<ActionImageEntity> {

    /**
     * 上传动作图片到OSS并落库。
     *
     * @param actionId     所属动作ID
     * @param petPrototype 宠物原型: 锦鲤/玉兔
     * @param timeOfDay    时段类型: 白天/落日/黑夜
     * @param captions     图片配文，多句用|分隔
     * @param file         图片文件
     */
    void uploadImage(String actionId, String petPrototype, String timeOfDay, String captions, MultipartFile file);

    /**
     * 修改已上传图片的配文。
     *
     * @param id       图片ID
     * @param captions 图片配文，多句用|分隔；传空表示清空配文
     */
    void updateCaptions(String id, String captions);

    /**
     * 删除单张图片，同时清理OSS文件。
     */
    void delete(String id);

    /**
     * 删除某个动作下的全部图片，同时清理OSS文件。
     */
    void deleteByActionId(String actionId);
}
