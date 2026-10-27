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
        SmallObjectEntity entity = selectUndeleted(id);
        if (entity == null) {
            throw new RenException(ErrorCode.SMALL_OBJECT_NOT_FOUND);
        }
        // 只写状态字段，图片与故事保持原值
        entity.setStatus(status);
        updateById(entity);
    }

    @Override
    public List<SmallObjectTypeVO> listObjectTypes() {        List<SysDictDataItem> dictItems = sysDictDataService
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
        // 软删只置位 deleted=1，行保留在库中；对已删除或不存在的 id 不做幂等成功，
        // 与详情同样的「已删除 = 不存在」语义，统一走 selectUndeleted 过滤
        SmallObjectEntity entity = selectUndeleted(id);
        if (entity == null) {
            throw new RenException(ErrorCode.SMALL_OBJECT_NOT_FOUND);
        }
        entity.setDeleted(1);
        updateById(entity);
    }

    /**
     * 校验新增请求的业务规则。
     * 类型必须在字典中存在，防止前端下拉被绕过写入野类型；
     * 同一类型在未删除记录中只允许一条——本表不建 DB 唯一索引（软删下会阻止删除后重建），
     * 唯一性只能由这里承担；图片与故事非空、故事不超长在 Service 再兜一次。
     */
    private void validateForCreate(SmallObjectDTO dto) {
        if (dto == null) {
            throw new RenException(ErrorCode.PARAM_VALUE_NULL);
        }
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
        // 同类型在未删除记录中只允许一条
        if (usedObjectTypes().contains(objectType)) {
            throw new RenException(ErrorCode.SMALL_OBJECT_TYPE_ALREADY_USED);
        }
        // 图片与故事必填、故事不超长。HTTP 入口已由 DTO 注解先拦一道，
        // 这里是 Service 自己的兜底（Service 是唯一安全边界），文案与 validation 文案保持一致
        if (StringUtils.isBlank(dto.getImageUrl())) {
            throw new RenException("物件图片不能为空");
        }
        if (StringUtils.isBlank(dto.getStory())) {
            throw new RenException("物件故事不能为空");
        }
        if (dto.getStory().length() > SmallObjectDTO.STORY_MAX_LENGTH) {
            throw new RenException("物件故事最多" + SmallObjectDTO.STORY_MAX_LENGTH + "字");
        }
        // 落库前统一去掉类型两端空白，避免 "TYPE" 与 "TYPE " 被当成两个类型
        dto.setObjectType(objectType);
    }

    /**
     * 校验更新请求的业务规则，与新增的差异仅在唯一性：排除自身 id 后再查占用，
     * 因此保留原类型不会被误判为重复，改为其他未占用类型也可通过。
     */
    private void validateForUpdate(SmallObjectDTO dto) {
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
        // 同类型在未删除记录中只允许一条；排除自身 id，保留原类型不报重复
        Long occupied = baseDao.selectCount(new QueryWrapper<SmallObjectEntity>()
                .eq("object_type", objectType)
                .eq("deleted", 0)
                .ne("id", dto.getId()));
        if (occupied != null && occupied > 0) {
            throw new RenException(ErrorCode.SMALL_OBJECT_TYPE_ALREADY_USED);
        }
        if (StringUtils.isBlank(dto.getImageUrl())) {
            throw new RenException("物件图片不能为空");
        }
        if (StringUtils.isBlank(dto.getStory())) {
            throw new RenException("物件故事不能为空");
        }
        if (dto.getStory().length() > SmallObjectDTO.STORY_MAX_LENGTH) {
            throw new RenException("物件故事最多" + SmallObjectDTO.STORY_MAX_LENGTH + "字");
        }
        dto.setObjectType(objectType);
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