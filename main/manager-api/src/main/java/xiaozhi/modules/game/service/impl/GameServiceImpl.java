package xiaozhi.modules.game.service.impl;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
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
            throw new RenException(ErrorCode.GAME_NOT_FOUND);
        }
        return ConvertUtils.sourceToTarget(entity, GameVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(GameDTO dto) {
        validate(dto);
        // 同 game_type 只允许一条配置；DB 层由 uk_ai_game_game_type 唯一索引兜底
        ensureGameTypeUnique(dto.getGameType(), null);
        GameEntity entity = ConvertUtils.sourceToTarget(dto, GameEntity.class);
        try {
            insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发场景：前置 count 检查后、insert 前另一事务抢先提交，由唯一索引兜底，转业务码
            throw new RenException(ErrorCode.GAME_TYPE_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(GameDTO dto) {
        validate(dto);
        // 目标行必须存在，避免 updateById 静默 0 行影响返回成功
        if (baseDao.selectById(dto.getId()) == null) {
            throw new RenException(ErrorCode.GAME_NOT_FOUND);
        }
        // 编辑场景允许 game_type 保持原值，需排除自身 id
        ensureGameTypeUnique(dto.getGameType(), dto.getId());
        GameEntity entity = ConvertUtils.sourceToTarget(dto, GameEntity.class);
        try {
            updateById(entity);
        } catch (DuplicateKeyException e) {
            // 并发场景：前置 count 检查后、update 前另一事务抢先提交，由唯一索引兜底，转业务码
            throw new RenException(ErrorCode.GAME_TYPE_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        deleteById(id);
    }

    @Override
    public List<GameVO> listEnabled(String entrance) {
        QueryWrapper<GameEntity> wrapper = new QueryWrapper<>();
        // 只返回启用游戏，停用配置不对小程序暴露
        wrapper.eq("status", 1);
        // 按入口过滤：FIND_IN_SET 精确匹配逗号分隔列表中的某一项，
        // 避免 LIKE 把 "NOTE" 误匹配到未来可能存在的 "NOTE_XXX" 之类的前缀 code
        wrapper.apply(StringUtils.isNotBlank(entrance), "FIND_IN_SET({0}, entrances)", entrance);
        wrapper.orderByAsc("id");
        List<GameEntity> entities = baseDao.selectList(wrapper);
        return ConvertUtils.sourceToTarget(entities, GameVO.class);
    }

    /**
     * 校验同一 game_type 不允许存在多条配置。
     * DB 层已由 uk_ai_game_game_type 唯一索引兜底，此处前置拦截以返回友好错误码；
     * excludeId 用于 update 场景排除自身，避免误判。
     */
    private void ensureGameTypeUnique(String gameType, Long excludeId) {
        QueryWrapper<GameEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("game_type", gameType);
        wrapper.ne(excludeId != null, "id", excludeId);
        Long count = baseDao.selectCount(wrapper);
        if (count > 0) {
            throw new RenException(ErrorCode.GAME_TYPE_ALREADY_EXISTS);
        }
    }

    /**
     * 统一校验 save/update 的业务规则。
     * game_type 必须在 GAME_TYPE 字典中存在，防止配置指向无效类型；
     * entrances 至少一项且全在 GameEntranceEnum 内，保证投放位置合法；
     * 三个数值字段非负，保持配置语义正确。
     */
    private void validate(GameDTO dto) {
        // 校验 game_type 在 GAME_TYPE 字典中存在
        List<SysDictDataItem> dictItems = sysDictDataService.getDictDataByType(Constant.DictType.GAME_TYPE.getValue());
        boolean typeExists = dictItems.stream()
                .anyMatch(item -> item.getKey().equals(dto.getGameType()));
        if (!typeExists) {
            throw new RenException(ErrorCode.GAME_TYPE_NOT_EXIST);
        }

        // 校验 entrances 非空且全在枚举内
        if (StringUtils.isBlank(dto.getEntrances())) {
            throw new RenException(ErrorCode.GAME_ENTRANCE_REQUIRED);
        }
        String[] entranceArr = dto.getEntrances().split(",");
        for (String entrance : entranceArr) {
            if (!GameEntranceEnum.codes().contains(entrance.trim())) {
                throw new RenException(ErrorCode.GAME_ENTRANCE_INVALID, entrance);
            }
        }

        // 校验数值字段非负
        if (dto.getEarnStars() != null && dto.getEarnStars() < 0) {
            throw new RenException(ErrorCode.GAME_EARN_STARS_NEGATIVE);
        }
        if (dto.getConsumeStars() != null && dto.getConsumeStars() < 0) {
            throw new RenException(ErrorCode.GAME_CONSUME_STARS_NEGATIVE);
        }
        if (dto.getDailyLimit() != null && dto.getDailyLimit() < 0) {
            throw new RenException(ErrorCode.GAME_DAILY_LIMIT_NEGATIVE);
        }
    }
}
