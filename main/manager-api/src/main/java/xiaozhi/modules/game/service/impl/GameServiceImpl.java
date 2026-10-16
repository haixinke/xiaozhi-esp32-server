package xiaozhi.modules.game.service.impl;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.dto.GameDTO;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.enums.GameEntranceEnum;
import xiaozhi.modules.game.service.GameService;
import xiaozhi.modules.game.vo.GameVO;
import xiaozhi.modules.sys.service.SysDictDataService;
import xiaozhi.modules.sys.vo.SysDictDataItem;

/**
 * 游戏配置 Service 实现。
 * 校验规则：game_type 必须在 GAME_TYPE 字典中存在、entrances 非空且全在枚举内、
 * earn_stars/consume_stars/daily_limit 非负。
 */
@Service
@AllArgsConstructor
public class GameServiceImpl extends BaseServiceImpl<GameDao, GameEntity> implements GameService {

    private final SysDictDataService sysDictDataService;

    @Override
    public PageData<GameVO> page(Map<String, Object> params) {
        IPage<GameEntity> page = baseDao.selectPage(getPage(params, "id", true), getWrapper(params));
        return getPageData(page, GameVO.class);
    }

    private QueryWrapper<GameEntity> getWrapper(Map<String, Object> params) {
        String gameType = (String) params.get("gameType");
        QueryWrapper<GameEntity> wrapper = new QueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(gameType), "game_type", gameType);
        return wrapper;
    }

    @Override
    public GameVO get(Long id) {
        GameEntity entity = baseDao.selectById(id);
        if (entity == null) {
            throw new RenException("游戏不存在");
        }
        return ConvertUtils.sourceToTarget(entity, GameVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(GameDTO dto) {
        validate(dto);
        GameEntity entity = ConvertUtils.sourceToTarget(dto, GameEntity.class);
        insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(GameDTO dto) {
        validate(dto);
        GameEntity entity = ConvertUtils.sourceToTarget(dto, GameEntity.class);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        deleteById(id);
    }

    /**
     * 统一校验 save/update 的业务规则。
     * game_type 必须在 GAME_TYPE 字典中存在，防止配置指向无效类型；
     * entrances 至少一项且全在 GameEntranceEnum 内，保证投放位置合法；
     * 三个数值字段非负，保持配置语义正确。
     */
    private void validate(GameDTO dto) {
        // 校验 game_type 在 GAME_TYPE 字典中存在
        List<SysDictDataItem> dictItems = sysDictDataService.getDictDataByType("GAME_TYPE");
        boolean typeExists = dictItems.stream()
                .anyMatch(item -> item.getKey().equals(dto.getGameType()));
        if (!typeExists) {
            throw new RenException("游戏类型不存在");
        }

        // 校验 entrances 非空且全在枚举内
        if (StringUtils.isBlank(dto.getEntrances())) {
            throw new RenException("至少选择一个游戏入口");
        }
        String[] entranceArr = dto.getEntrances().split(",");
        for (String entrance : entranceArr) {
            if (!GameEntranceEnum.codes().contains(entrance.trim())) {
                throw new RenException("非法的游戏入口: " + entrance);
            }
        }

        // 校验数值字段非负
        if (dto.getEarnStars() != null && dto.getEarnStars() < 0) {
            throw new RenException("赚取星星数不能为负数");
        }
        if (dto.getConsumeStars() != null && dto.getConsumeStars() < 0) {
            throw new RenException("消耗星星数不能为负数");
        }
        if (dto.getDailyLimit() != null && dto.getDailyLimit() < 0) {
            throw new RenException("每日次数上限不能为负数");
        }
    }
}
