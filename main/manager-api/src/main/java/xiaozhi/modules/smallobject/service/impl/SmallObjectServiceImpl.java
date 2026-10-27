package xiaozhi.modules.smallobject.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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
import xiaozhi.modules.smallobject.entity.SmallObjectEntity;
import xiaozhi.modules.smallobject.service.SmallObjectService;
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