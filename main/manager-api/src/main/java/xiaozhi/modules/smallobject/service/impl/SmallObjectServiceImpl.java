package xiaozhi.modules.smallobject.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.smallobject.dao.SmallObjectDao;
import xiaozhi.modules.smallobject.dto.SmallObjectDTO;
import xiaozhi.modules.smallobject.entity.SmallObjectEntity;
import xiaozhi.modules.smallobject.service.SmallObjectService;
import xiaozhi.modules.smallobject.vo.SmallObjectTypeVO;
import xiaozhi.modules.smallobject.vo.SmallObjectVO;
import xiaozhi.modules.sys.service.SysDictDataService;
import xiaozhi.modules.sys.vo.SysDictDataItem;

/**
 * 小物件 Service 实现。
 * 读取点均显式过滤 deleted = 0，删除语义为「已删除 = 不存在」；不引入 MyBatis-Plus
 * @TableLogic（全仓库无此用法，ADR 0006 已否决：全局自动过滤会波及智控台全量视图）。
 * 类型名称由字典 SMALL_OBJECT_TYPE 的 dict_label 实时翻译，翻译不到时回落原始 dict_value。
 */
@Service
@AllArgsConstructor
public class SmallObjectServiceImpl extends BaseServiceImpl<SmallObjectDao, SmallObjectEntity>
        implements SmallObjectService {

    private final SysDictDataService sysDictDataService;

    @Override
    public PageData<SmallObjectVO> page(Map<String, Object> params) {
        // 列表顺序必须由物件类型的字典 sort 决定（与字典管理页一致），因此剔除调用方可能传入的
        // orderField/order，避免客户端改写排序破坏该约定；分页参数照常透传
        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.remove(Constant.ORDER_FIELD);
        pageParams.remove(Constant.ORDER);
        IPage<SmallObjectEntity> page = baseDao.selectPageOrderByDictSort(
                getPage(pageParams, null, true), Constant.DictType.SMALL_OBJECT_TYPE.getValue());
        List<SmallObjectVO> voList = ConvertUtils.sourceToTarget(page.getRecords(), SmallObjectVO.class);
        fillObjectTypeName(voList);
        return new PageData<>(voList, page.getTotal());
    }

    @Override
    public SmallObjectVO get(Long id) {
        SmallObjectEntity entity = selectUndeleted(id);
        if (entity == null) {
            // 不存在与已删除同样按「物件不存在」处理，不暴露记录是否曾经存在
            throw new RenException(ErrorCode.SMALL_OBJECT_NOT_FOUND);
        }
        SmallObjectVO vo = ConvertUtils.sourceToTarget(entity, SmallObjectVO.class);
        fillObjectTypeName(List.of(vo));
        return vo;
    }

    /**
     * 按 id 查询未删除的记录；已软删的行与不存在等价
     */
    private SmallObjectEntity selectUndeleted(Long id) {
        QueryWrapper<SmallObjectEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("deleted", 0);
        return baseDao.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SmallObjectDTO dto) {
        validateForCreate(dto);
        SmallObjectEntity entity = ConvertUtils.sourceToTarget(dto, SmallObjectEntity.class);
        // 新增一律为未删除；状态未传时默认启用，与表默认值一致
        entity.setDeleted(0);
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SmallObjectDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new RenException(ErrorCode.PARAM_VALUE_NULL);
        }
        SmallObjectEntity entity = selectUndeleted(dto.getId());
        if (entity == null) {
            // 不存在与已删除同样按「物件不存在」处理，不做静默成功
            throw new RenException(ErrorCode.SMALL_OBJECT_NOT_FOUND);
        }
        validateForUpdate(dto);
        entity.setObjectType(StringUtils.trim(dto.getObjectType()));
        entity.setImageUrl(dto.getImageUrl());
        entity.setStory(dto.getStory());
        // 状态不在全量更新范围内：启用/停用只能走 updateStatus，避免编辑弹窗覆盖他人在列表里刚切换的状态
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (id == null || status == null) {
            throw new RenException(ErrorCode.PARAM_VALUE_NULL);
        }
        // 状态白名单 0/1：HTTP 入口已由 DTO 的 @Min/@Max 拦一道，
        // 这里是 Service 的兜底（Service 是唯一安全边界，与 GameServiceImpl 的数值校验一致）
        if (status < 0 || status > 1) {
            throw new RenException(ErrorCode.SMALL_OBJECT_STATUS_INVALID);
        }
        // 只把 status 放入待更新实体，生成单条定向 UPDATE：不读全量再写回，
        // 因此不会用本请求读到的陈旧副本覆盖他人刚编辑的图片/故事。
        // 传实体（而非 null）是为了保留 MetaObjectHandler 对 updater/update_date 的自动填充
        SmallObjectEntity changes = new SmallObjectEntity();
        changes.setStatus(status);
        // 条件带 deleted = 0：已删记录与不存在同样影响 0 行，统一报物件不存在
        UpdateWrapper<SmallObjectEntity> wrapper = new UpdateWrapper<SmallObjectEntity>()
                .eq("id", id)
                .eq("deleted", 0);
        if (!update(changes, wrapper)) {
            throw new RenException(ErrorCode.SMALL_OBJECT_NOT_FOUND);
        }
    }

    @Override
    public List<SmallObjectTypeVO> listObjectTypes() {
        List<SysDictDataItem> dictItems = sysDictDataService
                .getDictDataByType(Constant.DictType.SMALL_OBJECT_TYPE.getValue());
        if (dictItems == null || dictItems.isEmpty()) {
            return List.of();
        }
        // 一次性取出所有已占用类型，避免为每个小类各查一次库
        Set<String> usedTypes = usedObjectTypes();
        return dictItems.stream()
                .map(item -> new SmallObjectTypeVO(item.getKey(), item.getName(), usedTypes.contains(item.getKey())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 软删只把 deleted 置 1，行保留在库中；同样是定向 UPDATE，不读全量再写回，
        // 避免把并发编辑中的图片/故事用陈旧副本覆盖。条件带 deleted = 0：
        // 对已删除或不存在的 id 影响 0 行，与详情同样的「已删除 = 不存在」语义
        SmallObjectEntity changes = new SmallObjectEntity();
        changes.setDeleted(1);
        UpdateWrapper<SmallObjectEntity> wrapper = new UpdateWrapper<SmallObjectEntity>()
                .eq("id", id)
                .eq("deleted", 0);
        if (!update(changes, wrapper)) {
            throw new RenException(ErrorCode.SMALL_OBJECT_NOT_FOUND);
        }
    }

    /**
     * 校验新增请求的业务规则：公共校验 + 类型唯一性（新增时无自身可排除）。
     */
    private void validateForCreate(SmallObjectDTO dto) {
        if (dto == null) {
            throw new RenException(ErrorCode.PARAM_VALUE_NULL);
        }
        ensureTypeUnique(validateCommon(dto), null);
    }

    /**
     * 校验更新请求的业务规则：公共校验 + 唯一性（排除自身 id 后再查占用），
     * 因此保留原类型不会被误判为重复，改为其他未占用类型也可通过。
     */
    private void validateForUpdate(SmallObjectDTO dto) {
        ensureTypeUnique(validateCommon(dto), dto.getId());
    }

    /**
     * 同一类型在未删除记录中只允许一条。
     * 本表不建 DB 唯一索引（软删下会阻止删除后重建），唯一性只能由这里承担；
     * excludeId 用于更新场景排除自身，避免保留原类型时误判为重复。
     */
    private void ensureTypeUnique(String objectType, Long excludeId) {
        Long occupied = baseDao.selectCount(new QueryWrapper<SmallObjectEntity>()
                .eq("object_type", objectType)
                .eq("deleted", 0)
                .ne(excludeId != null, "id", excludeId));
        if (occupied != null && occupied > 0) {
            throw new RenException(ErrorCode.SMALL_OBJECT_TYPE_ALREADY_USED);
        }
    }

    /**
     * 新增与更新共用的校验：类型非空且在 SMALL_OBJECT_TYPE 字典中存在、图片与故事非空、故事不超长。
     * 返回去掉两端空白后的类型值，并回写 dto，避免 "TYPE" 与 "TYPE " 被当成两个类型。
     * 图片/故事必填在 HTTP 入口已由 DTO 注解先拦一道，这里是 Service 的兜底（Service 是唯一安全边界）。
     */
    private String validateCommon(SmallObjectDTO dto) {
        String objectType = StringUtils.trimToNull(dto.getObjectType());
        if (objectType == null) {
            throw new RenException(ErrorCode.PARAM_VALUE_NULL);
        }
        // 类型必须存在于 SMALL_OBJECT_TYPE 字典（不信前端下拉）
        List<SysDictDataItem> dictItems = sysDictDataService
                .getDictDataByType(Constant.DictType.SMALL_OBJECT_TYPE.getValue());
        boolean typeExists = dictItems != null && dictItems.stream()
                .anyMatch(item -> objectType.equals(item.getKey()));
        if (!typeExists) {
            throw new RenException(ErrorCode.SMALL_OBJECT_TYPE_NOT_EXIST);
        }
        if (StringUtils.isBlank(dto.getImageUrl())) {
            throw new RenException(ErrorCode.SMALL_OBJECT_IMAGE_REQUIRED);
        }
        if (StringUtils.isBlank(dto.getStory())) {
            throw new RenException(ErrorCode.SMALL_OBJECT_STORY_REQUIRED);
        }
        if (dto.getStory().length() > SmallObjectDTO.STORY_MAX_LENGTH) {
            throw new RenException(ErrorCode.SMALL_OBJECT_STORY_TOO_LONG,
                    String.valueOf(SmallObjectDTO.STORY_MAX_LENGTH));
        }
        dto.setObjectType(objectType);
        return objectType;
    }

    /**
     * 查询已被未删除物件占用的类型集合。
     * 软删语义下必须显式带 deleted = 0，否则删掉的记录会永久占用该类型。
     */
    private Set<String> usedObjectTypes() {
        List<SmallObjectEntity> rows = baseDao.selectList(new QueryWrapper<SmallObjectEntity>()
                .select("DISTINCT object_type")
                .eq("deleted", 0));
        Set<String> types = new HashSet<>();
        for (SmallObjectEntity row : rows) {
            if (row.getObjectType() != null) {
                types.add(row.getObjectType());
            }
        }
        return types;
    }

    /**
     * 用字典 dict_label 填充类型显示名；字典里查不到的 code 回落显示原始值，
     * 这样即使运营在字典管理里删掉某个小类，已有物件仍能正常展示而不是空白。
     */
    private void fillObjectTypeName(List<SmallObjectVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        Map<String, String> labelByValue = objectTypeLabels();
        voList.forEach(vo -> vo.setObjectTypeName(labelByValue.getOrDefault(vo.getObjectType(), vo.getObjectType())));
    }

    /** 字典 dict_value -> dict_label 映射 */
    private Map<String, String> objectTypeLabels() {
        List<SysDictDataItem> dictItems = sysDictDataService
                .getDictDataByType(Constant.DictType.SMALL_OBJECT_TYPE.getValue());
        Map<String, String> labelByValue = new HashMap<>();
        if (dictItems == null) {
            return labelByValue;
        }
        for (SysDictDataItem item : dictItems) {
            if (item.getKey() != null) {
                labelByValue.put(item.getKey(), item.getName());
            }
        }
        return labelByValue;
    }
}