package xiaozhi.modules.storyengine.service.impl;

import com.aliyun.oss.model.CannedAccessControlList;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.storyengine.dao.ActionDao;
import xiaozhi.modules.storyengine.dao.ActionImageDao;
import xiaozhi.modules.storyengine.dao.BigSceneDao;
import xiaozhi.modules.storyengine.dao.SmallSceneDao;
import xiaozhi.modules.storyengine.entity.ActionEntity;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;
import xiaozhi.modules.storyengine.entity.BigSceneEntity;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;
import xiaozhi.modules.storyengine.service.SpecialSceneTagRegistry;
import xiaozhi.modules.storyengine.service.StoryActionImageService;
import xiaozhi.modules.storyengine.vo.CaptionsImportVO;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class StoryActionImageServiceImpl extends BaseServiceImpl<ActionImageDao, ActionImageEntity>
        implements StoryActionImageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");

    /**
     * OSS Key 中的原型/时段目录名使用英文，避免中文路径带来的编码问题。
     */
    private static final Map<String, String> PROTOTYPE_KEY_MAP = Map.of("锦鲤", "koi", "玉兔", "rabbit");

    private static final Map<String, String> TIME_KEY_MAP = Map.of("白天", "day", "落日", "sunset", "黑夜", "night");

    /**
     * 图片标签最大长度，与 ai_story_action_image.tag 列宽一致。
     */
    private static final int TAG_MAX_LENGTH = 64;

    /** 文案导入模版的表头，需与《图片文案模版.xlsx》保持一致 */
    private static final List<String> CAPTIONS_IMPORT_HEADERS = List.of(
            "大场景", "小场景", "动作", "时段", "宠物类型", "图片文案");

    private final ActionImageDao actionImageDao;
    private final OssService ossService;
    private final BigSceneDao bigSceneDao;
    private final SmallSceneDao smallSceneDao;
    private final ActionDao actionDao;
    private final SpecialSceneTagRegistry specialSceneTagRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadImage(String actionId, String petPrototype, String timeOfDay, String captions, String tag,
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
        String normalizedTag = normalizeTag(tag);

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
        entity.setImageUrl(ossService.buildPublicUrl(ossKey));
        entity.setCaptions(captions);
        entity.setTag(normalizedTag);
        entity.setSortOrder(nextSortOrder(actionId, petPrototype, timeOfDay));
        actionImageDao.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInfo(String id, String captions, String tag) {
        if (StringUtils.isBlank(id)) {
            throw new RenException("图片ID不能为空");
        }
        ActionImageEntity entity = actionImageDao.selectById(id);
        if (entity == null) {
            throw new RenException("动作图片不存在");
        }
        // captions/tag 为空时需要显式置空，MyBatis-Plus 默认忽略 null 字段，故使用 UpdateWrapper
        UpdateWrapper<ActionImageEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("captions", StringUtils.isBlank(captions) ? null : captions.trim())
                .set("tag", normalizeTag(tag));
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

    /**
     * 规范化标签：trim 后空串置 NULL；超长直接抛错（列宽 64，超长落库会截断或报错）。
     */
    private static String normalizeTag(String tag) {
        if (StringUtils.isBlank(tag)) {
            return null;
        }
        String trimmed = tag.trim();
        if (trimmed.length() > TAG_MAX_LENGTH) {
            throw new RenException("标签长度不能超过" + TAG_MAX_LENGTH + "字符");
        }
        return trimmed;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaptionsImportVO importCaptions(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.UPLOAD_FILE_EMPTY);
        }
        CaptionsImportVO result = new CaptionsImportVO();
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new RenException("Excel文件中没有工作表");
            }
            validateHeaders(sheet.getRow(0), formatter);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String bigSceneName = cellText(row, 0, formatter);
                String smallSceneName = cellText(row, 1, formatter);
                String actionName = cellText(row, 2, formatter);
                String timeOfDay = cellText(row, 3, formatter);
                String petPrototype = cellText(row, 4, formatter);
                String captions = cellText(row, 5, formatter);
                if (StringUtils.isAllBlank(bigSceneName, smallSceneName, actionName, timeOfDay, petPrototype,
                        captions)) {
                    continue;
                }
                // Excel行号从1开始，与运营在Excel里看到的行号一致
                importRow(i + 1, bigSceneName, smallSceneName, actionName, timeOfDay, petPrototype, captions,
                        result);
            }
        } catch (RenException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量导入图片文案失败", e);
            throw new RenException("Excel解析失败，请使用文案模版导出的 .xlsx 文件");
        }
        return result;
    }

    private void importRow(int rowNumber, String bigSceneName, String smallSceneName, String actionName,
            String timeOfDay, String petPrototype, String captions, CaptionsImportVO result) {
        if (StringUtils.isAnyBlank(bigSceneName, smallSceneName, actionName, timeOfDay, petPrototype)) {
            result.addSkipped(rowNumber, bigSceneName, smallSceneName, actionName,
                    "大场景/小场景/动作/时段/宠物类型不能为空");
            return;
        }
        if (StringUtils.isBlank(captions)) {
            result.addSkipped(rowNumber, bigSceneName, smallSceneName, actionName, "图片文案为空");
            return;
        }

        QueryWrapper<BigSceneEntity> bigWrapper = new QueryWrapper<>();
        bigWrapper.eq("name", bigSceneName);
        BigSceneEntity bigScene = bigSceneDao.selectOne(bigWrapper);
        if (bigScene == null) {
            result.addSkipped(rowNumber, bigSceneName, smallSceneName, actionName, "大场景名称不匹配");
            return;
        }
        QueryWrapper<SmallSceneEntity> smallWrapper = new QueryWrapper<>();
        smallWrapper.eq("big_scene_id", bigScene.getId()).eq("name", smallSceneName);
        SmallSceneEntity smallScene = smallSceneDao.selectOne(smallWrapper);
        if (smallScene == null) {
            result.addSkipped(rowNumber, bigSceneName, smallSceneName, actionName, "小场景名称不匹配");
            return;
        }
        QueryWrapper<ActionEntity> actionWrapper = new QueryWrapper<>();
        actionWrapper.eq("small_scene_id", smallScene.getId()).eq("name", actionName);
        ActionEntity action = actionDao.selectOne(actionWrapper);
        if (action == null) {
            result.addSkipped(rowNumber, bigSceneName, smallSceneName, actionName, "动作名称不匹配");
            return;
        }

        QueryWrapper<ActionImageEntity> imageWrapper = new QueryWrapper<>();
        imageWrapper.eq("action_id", action.getId())
                .eq("pet_prototype", petPrototype)
                .eq("time_of_day", timeOfDay);
        List<ActionImageEntity> images = actionImageDao.selectList(imageWrapper);
        if (images.isEmpty()) {
            result.addSkipped(rowNumber, bigSceneName, smallSceneName, actionName,
                    "该宠物类型+时段下没有图片");
            return;
        }

        // 命中特殊场景规则时(如:在家/卧室)，带特殊标签(如:窗户)的图片不更新文案
        Optional<String> specialTag = specialSceneTagRegistry.specialTagOf(bigScene.getName(),
                smallScene.getName());
        List<ActionImageEntity> targets = images.stream()
                .filter(image -> specialTag.isEmpty() || !specialTag.get().equals(StringUtils.trim(image.getTag())))
                .toList();
        if (targets.isEmpty()) {
            result.addSkipped(rowNumber, bigSceneName, smallSceneName, actionName,
                    "组内图片均为特殊标签图(" + specialTag.orElse("") + ")，按规则不更新");
            return;
        }
        for (ActionImageEntity image : targets) {
            UpdateWrapper<ActionImageEntity> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", image.getId()).set("captions", captions);
            actionImageDao.update(null, updateWrapper);
        }
        result.setUpdatedImages(result.getUpdatedImages() + targets.size());
    }

    private static void validateHeaders(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new RenException("Excel第一行必须是表头: " + String.join(" | ", CAPTIONS_IMPORT_HEADERS));
        }
        for (int i = 0; i < CAPTIONS_IMPORT_HEADERS.size(); i++) {
            String actual = cellText(headerRow, i, formatter);
            if (!CAPTIONS_IMPORT_HEADERS.get(i).equals(actual)) {
                throw new RenException("表头第" + (i + 1) + "列应为「" + CAPTIONS_IMPORT_HEADERS.get(i) + "」，实际为「"
                        + StringUtils.defaultString(actual, "空") + "」，请使用文案模版");
            }
        }
    }

    private static String cellText(Row row, int index, DataFormatter formatter) {
        return StringUtils.trimToNull(formatter.formatCellValue(row.getCell(index)));
    }
}
