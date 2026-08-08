package xiaozhi.modules.storyengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.storyengine.dao.SmallSceneDao;
import xiaozhi.modules.storyengine.dto.BatchWeightUpdateDTO;
import xiaozhi.modules.storyengine.dto.SmallSceneDTO;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;
import xiaozhi.modules.storyengine.service.StoryActionService;
import xiaozhi.modules.storyengine.service.StorySmallSceneService;
import xiaozhi.modules.storyengine.vo.ActionVO;
import xiaozhi.modules.storyengine.vo.SmallSceneVO;
import xiaozhi.modules.storyengine.vo.WeightSummaryVO;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class StorySmallSceneServiceImpl extends BaseServiceImpl<SmallSceneDao, SmallSceneEntity>
        implements StorySmallSceneService {

    /**
     * 四个时段的中文名，与 WEIGHT_COLUMNS 顺序一一对应。
     */
    private static final String[] PERIOD_LABELS = { "深夜", "上午", "下午", "傍晚" };

    private static final String[] SUM_ALIASES = { "total_night", "total_morning", "total_afternoon", "total_evening" };

    private static final int MAX_WEIGHT_TOTAL = 100;

    private static final int STATUS_ENABLED = 1;

    private final SmallSceneDao smallSceneDao;
    private final StoryActionService actionService;

    @Override
    public List<SmallSceneVO> listByBigSceneId(String bigSceneId) {
        if (StringUtils.isBlank(bigSceneId)) {
            throw new RenException("大场景ID不能为空");
        }
        QueryWrapper<SmallSceneEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("big_scene_id", bigSceneId)
                .orderByAsc("sort_order")
                .orderByAsc("id");
        return smallSceneDao.selectList(wrapper).stream()
                .map(SmallSceneVO::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SmallSceneDTO dto) {
        if (StringUtils.isBlank(dto.getBigSceneId())) {
            throw new RenException("所属大场景ID不能为空");
        }
        int status = dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus();
        int[] weights = { nz(dto.getWeightNight()), nz(dto.getWeightMorning()),
                nz(dto.getWeightAfternoon()), nz(dto.getWeightEvening()) };
        if (status == STATUS_ENABLED) {
            validateTotals(sumOtherWeights(List.of()), weights);
        }

        SmallSceneEntity entity = new SmallSceneEntity();
        entity.setBigSceneId(dto.getBigSceneId());
        entity.setName(dto.getName());
        entity.setWeightNight(weights[0]);
        entity.setWeightMorning(weights[1]);
        entity.setWeightAfternoon(weights[2]);
        entity.setWeightEvening(weights[3]);
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        entity.setStatus(status);
        smallSceneDao.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SmallSceneDTO dto) {
        if (StringUtils.isBlank(dto.getId())) {
            throw new RenException("小场景ID不能为空");
        }
        SmallSceneEntity existing = smallSceneDao.selectById(dto.getId());
        if (existing == null) {
            throw new RenException("小场景不存在");
        }

        String bigSceneId = StringUtils.isBlank(dto.getBigSceneId()) ? existing.getBigSceneId() : dto.getBigSceneId();
        int status = dto.getStatus() == null ? existing.getStatus() : dto.getStatus();
        int[] weights = {
                dto.getWeightNight() == null ? nz(existing.getWeightNight()) : dto.getWeightNight(),
                dto.getWeightMorning() == null ? nz(existing.getWeightMorning()) : dto.getWeightMorning(),
                dto.getWeightAfternoon() == null ? nz(existing.getWeightAfternoon()) : dto.getWeightAfternoon(),
                dto.getWeightEvening() == null ? nz(existing.getWeightEvening()) : dto.getWeightEvening()
        };
        // 校验时排除自身，避免把自身旧权重重复计入合计
        if (status == STATUS_ENABLED) {
            validateTotals(sumOtherWeights(List.of(existing.getId())), weights);
        }

        existing.setBigSceneId(bigSceneId);
        existing.setName(dto.getName());
        existing.setWeightNight(weights[0]);
        existing.setWeightMorning(weights[1]);
        existing.setWeightAfternoon(weights[2]);
        existing.setWeightEvening(weights[3]);
        if (dto.getSortOrder() != null) {
            existing.setSortOrder(dto.getSortOrder());
        }
        existing.setStatus(status);
        smallSceneDao.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateWeights(BatchWeightUpdateDTO dto) {
        List<BatchWeightUpdateDTO.SmallSceneWeightItem> items = dto.getItems();
        if (items == null || items.isEmpty()) {
            throw new RenException("权重列表不能为空");
        }

        Map<String, BatchWeightUpdateDTO.SmallSceneWeightItem> itemById = new LinkedHashMap<>();
        for (BatchWeightUpdateDTO.SmallSceneWeightItem item : items) {
            itemById.put(item.getId(), item);
        }
        List<SmallSceneEntity> entities = smallSceneDao.selectByIds(itemById.keySet());
        if (entities.size() != itemById.size()) {
            throw new RenException("部分小场景不存在，请刷新后重试");
        }

        // 全局校验：所有提交项的启用场景权重值合计，加上全局其他启用场景的现有值
        List<String> groupIds = entities.stream().map(SmallSceneEntity::getId).toList();
        int[] submitted = new int[PERIOD_LABELS.length];
        for (SmallSceneEntity entity : entities) {
            if (!Integer.valueOf(STATUS_ENABLED).equals(entity.getStatus())) {
                continue;
            }
            BatchWeightUpdateDTO.SmallSceneWeightItem item = itemById.get(entity.getId());
            submitted[0] += item.getWeightNight() == null ? nz(entity.getWeightNight()) : item.getWeightNight();
            submitted[1] += item.getWeightMorning() == null ? nz(entity.getWeightMorning())
                    : item.getWeightMorning();
            submitted[2] += item.getWeightAfternoon() == null ? nz(entity.getWeightAfternoon())
                    : item.getWeightAfternoon();
            submitted[3] += item.getWeightEvening() == null ? nz(entity.getWeightEvening())
                    : item.getWeightEvening();
        }
        validateTotals(sumOtherWeights(groupIds), submitted);

        for (SmallSceneEntity entity : entities) {
            BatchWeightUpdateDTO.SmallSceneWeightItem item = itemById.get(entity.getId());
            if (item.getWeightNight() != null) {
                entity.setWeightNight(item.getWeightNight());
            }
            if (item.getWeightMorning() != null) {
                entity.setWeightMorning(item.getWeightMorning());
            }
            if (item.getWeightAfternoon() != null) {
                entity.setWeightAfternoon(item.getWeightAfternoon());
            }
            if (item.getWeightEvening() != null) {
                entity.setWeightEvening(item.getWeightEvening());
            }
            smallSceneDao.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SmallSceneEntity existing = smallSceneDao.selectById(id);
        if (existing == null) {
            throw new RenException("小场景不存在");
        }
        for (ActionVO action : actionService.listBySmallSceneId(id)) {
            actionService.delete(action.getId());
        }
        smallSceneDao.deleteById(id);
    }

    @Override
    public WeightSummaryVO getWeightSummary() {
        int[] totals = sumOtherWeights(List.of());
        WeightSummaryVO vo = new WeightSummaryVO();
        vo.setTotalNight(totals[0]);
        vo.setTotalMorning(totals[1]);
        vo.setTotalAfternoon(totals[2]);
        vo.setTotalEvening(totals[3]);
        return vo;
    }

    /**
     * 统计所有大场景下已启用小场景的各时段权重合计，可排除指定ID（用于修改场景的自我排除）。
     *
     * @return 长度为4的数组，顺序与 PERIOD_LABELS 一致
     */
    private int[] sumOtherWeights(Collection<String> excludeIds) {
        QueryWrapper<SmallSceneEntity> wrapper = new QueryWrapper<>();
        wrapper.select(
                "IFNULL(SUM(weight_night), 0) AS " + SUM_ALIASES[0],
                "IFNULL(SUM(weight_morning), 0) AS " + SUM_ALIASES[1],
                "IFNULL(SUM(weight_afternoon), 0) AS " + SUM_ALIASES[2],
                "IFNULL(SUM(weight_evening), 0) AS " + SUM_ALIASES[3]);
        wrapper.eq("status", STATUS_ENABLED);
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn("id", excludeIds);
        }

        List<Map<String, Object>> rows = smallSceneDao.selectMaps(wrapper);
        int[] totals = new int[PERIOD_LABELS.length];
        if (rows.isEmpty() || rows.get(0) == null) {
            return totals;
        }
        Map<String, Object> row = normalizeKeys(rows.get(0));
        for (int i = 0; i < totals.length; i++) {
            Object value = row.get(SUM_ALIASES[i]);
            totals[i] = value instanceof Number number ? number.intValue() : 0;
        }
        return totals;
    }

    /**
     * 各数据库驱动返回的列标签大小写可能不同，统一转为小写后再取值。
     */
    private static Map<String, Object> normalizeKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new HashMap<>(row.size());
        row.forEach((key, value) -> normalized.put(key.toLowerCase(), value));
        return normalized;
    }

    /**
     * 校验「全局其它场景合计 + 本次提交值」不超过100%。
     */
    private static void validateTotals(int[] otherTotals, int[] currentValues) {
        for (int i = 0; i < PERIOD_LABELS.length; i++) {
            int sum = otherTotals[i] + currentValues[i];
            if (sum > MAX_WEIGHT_TOTAL) {
                throw new RenException(PERIOD_LABELS[i] + "时段权重总和超过100%，当前合计：" + sum);
            }
        }
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
