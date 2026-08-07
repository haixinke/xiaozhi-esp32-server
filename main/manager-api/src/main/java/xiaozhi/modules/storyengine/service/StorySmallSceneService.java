package xiaozhi.modules.storyengine.service;

import xiaozhi.common.service.BaseService;
import xiaozhi.modules.storyengine.dto.BatchWeightUpdateDTO;
import xiaozhi.modules.storyengine.dto.SmallSceneDTO;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;
import xiaozhi.modules.storyengine.vo.SmallSceneVO;
import xiaozhi.modules.storyengine.vo.WeightSummaryVO;

import java.util.List;

public interface StorySmallSceneService extends BaseService<SmallSceneEntity> {

    /**
     * 查询指定大场景下的小场景列表，按排序序号升序。
     */
    List<SmallSceneVO> listByBigSceneId(String bigSceneId);

    /**
     * 新增小场景，写入前校验同大场景下各时段权重合计不超过100%。
     */
    void save(SmallSceneDTO dto);

    /**
     * 修改小场景，写入前校验同大场景下各时段权重合计不超过100%。
     */
    void update(SmallSceneDTO dto);

    /**
     * 批量修改权重，整体校验通过后一次性落库。
     */
    void batchUpdateWeights(BatchWeightUpdateDTO dto);

    /**
     * 删除小场景，并级联删除其下所有动作与动作图片（含OSS文件）。
     */
    void delete(String id);

    /**
     * 统计指定大场景下已启用小场景的各时段权重合计。
     */
    WeightSummaryVO getWeightSummary(String bigSceneId);
}
