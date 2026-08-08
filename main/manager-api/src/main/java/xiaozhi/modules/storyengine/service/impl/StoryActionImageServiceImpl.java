package xiaozhi.modules.storyengine.service.impl;

import com.aliyun.oss.model.CannedAccessControlList;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.storyengine.dao.ActionImageDao;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;
import xiaozhi.modules.storyengine.service.StoryActionImageService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class StoryActionImageServiceImpl extends BaseServiceImpl<ActionImageDao, ActionImageEntity>
        implements StoryActionImageService {

    /**
     * OSS 公网访问域名前缀，与用户头像/默认素材保持一致。
     */
    private static final String OSS_URL_PREFIX = "https://oss.eggbabe.com/";

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");

    /**
     * OSS Key 中的原型/时段目录名使用英文，避免中文路径带来的编码问题。
     */
    private static final Map<String, String> PROTOTYPE_KEY_MAP = Map.of("锦鲤", "koi", "玉兔", "rabbit");

    private static final Map<String, String> TIME_KEY_MAP = Map.of("白天", "day", "落日", "sunset", "黑夜", "night");

    private final ActionImageDao actionImageDao;
    private final OssService ossService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadImage(String actionId, String petPrototype, String timeOfDay, String captions,
            MultipartFile file) {
        if (StringUtils.isBlank(actionId)) {
            throw new RenException("动作ID不能为空");
        }
        if (StringUtils.isBlank(petPrototype)) {
            throw new RenException("宠物原型不能为空");
        }
        if (StringUtils.isBlank(timeOfDay)) {
            throw new RenException("时段类型不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.UPLOAD_FILE_EMPTY);
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new RenException("仅支持 png/jpeg/webp/gif 格式的图片");
        }
        if (!ossService.isEnabled()) {
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR);
        }

        String ext = extensionOf(file.getContentType());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String protoKey = PROTOTYPE_KEY_MAP.getOrDefault(petPrototype, petPrototype);
        String timeKey = TIME_KEY_MAP.getOrDefault(timeOfDay, timeOfDay);
        String ossKey = "story-engine/" + protoKey + "/" + actionId + "/" + timeKey + "_" + suffix + "." + ext;
        try {
            ossService.upload(ossKey, file.getBytes(), CannedAccessControlList.PublicRead);
        } catch (Exception e) {
            log.error("故事引擎动作图片上传OSS失败 actionId={}, ossKey={}", actionId, ossKey, e);
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR);
        }

        ActionImageEntity entity = new ActionImageEntity();
        entity.setActionId(actionId);
        entity.setPetPrototype(petPrototype);
        entity.setTimeOfDay(timeOfDay);
        entity.setImageUrl(OSS_URL_PREFIX + ossKey);
        entity.setCaptions(captions);
        entity.setSortOrder(nextSortOrder(actionId, petPrototype, timeOfDay));
        actionImageDao.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCaptions(String id, String captions) {
        if (StringUtils.isBlank(id)) {
            throw new RenException("图片ID不能为空");
        }
        ActionImageEntity entity = actionImageDao.selectById(id);
        if (entity == null) {
            throw new RenException("动作图片不存在");
        }
        // captions 为空时需要显式置空，MyBatis-Plus 默认忽略 null 字段，故使用 UpdateWrapper
        UpdateWrapper<ActionImageEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("captions", StringUtils.isBlank(captions) ? null : captions.trim());
        actionImageDao.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ActionImageEntity entity = actionImageDao.selectById(id);
        if (entity == null) {
            throw new RenException("动作图片不存在");
        }
        // OSS原始图片不删除，快照履历场景仍需访问原始URL
        actionImageDao.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByActionId(String actionId) {
        QueryWrapper<ActionImageEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("action_id", actionId);
        List<ActionImageEntity> images = actionImageDao.selectList(wrapper);
        if (images.isEmpty()) {
            return;
        }
        // OSS原始图片不删除，快照履历场景仍需访问原始URL
        actionImageDao.delete(wrapper);
    }

    /**
     * 同一动作+原型+时段分组内的下一个排序序号。
     */
    private Integer nextSortOrder(String actionId, String petPrototype, String timeOfDay) {
        QueryWrapper<ActionImageEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("action_id", actionId)
                .eq("pet_prototype", petPrototype)
                .eq("time_of_day", timeOfDay);
        return Math.toIntExact(actionImageDao.selectCount(wrapper));
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new RenException("仅支持 png/jpeg/webp/gif 格式的图片");
        };
    }
}
