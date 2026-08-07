package xiaozhi.modules.storyengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.storyengine.dao.BigSceneDao;
import xiaozhi.modules.storyengine.dao.SmallSceneDao;
import xiaozhi.modules.storyengine.dto.BigSceneDTO;
import xiaozhi.modules.storyengine.entity.BigSceneEntity;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;
import xiaozhi.modules.storyengine.service.StoryBigSceneService;
import xiaozhi.modules.storyengine.service.StorySmallSceneService;
import xiaozhi.modules.storyengine.vo.BigSceneVO;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class StoryBigSceneServiceImpl extends BaseServiceImpl<BigSceneDao, BigSceneEntity>
        implements StoryBigSceneService {

    private final BigSceneDao bigSceneDao;
    private final SmallSceneDao smallSceneDao;
    private final StorySmallSceneService smallSceneService;

    @Override
    public List<BigSceneVO> listAll() {
        QueryWrapper<BigSceneEntity> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_order").orderByAsc("id");
        return bigSceneDao.selectList(wrapper).stream()
                .map(BigSceneVO::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(BigSceneDTO dto) {
        BigSceneEntity entity = new BigSceneEntity();
        entity.setName(dto.getName());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        bigSceneDao.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BigSceneDTO dto) {
        if (StringUtils.isBlank(dto.getId())) {
            throw new RenException("大场景ID不能为空");
        }
        BigSceneEntity existing = bigSceneDao.selectById(dto.getId());
        if (existing == null) {
            throw new RenException("大场景不存在");
        }

        existing.setName(dto.getName());
        if (dto.getSortOrder() != null) {
            existing.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        bigSceneDao.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        BigSceneEntity existing = bigSceneDao.selectById(id);
        if (existing == null) {
            throw new RenException("大场景不存在");
        }

        // 逐个委托小场景服务删除，由其继续级联清理动作与动作图片（含OSS文件）
        QueryWrapper<SmallSceneEntity> wrapper = new QueryWrapper<>();
        wrapper.select("id").eq("big_scene_id", id);
        for (SmallSceneEntity smallScene : smallSceneDao.selectList(wrapper)) {
            smallSceneService.delete(smallScene.getId());
        }
        bigSceneDao.deleteById(id);
    }
}
