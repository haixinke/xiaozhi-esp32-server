package xiaozhi.modules.game.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.game.dto.GameDTO;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.vo.GameVO;

/**
 * 游戏配置 Service
 */
public interface GameService extends BaseService<GameEntity> {

    /**
     * 分页查询游戏列表
     *
     * @param params 查询参数，包含分页信息
     * @return 分页的游戏数据
     */
    PageData<GameVO> page(Map<String, Object> params);

    /**
     * 根据ID获取游戏详情
     *
     * @param id 游戏ID
     * @return 游戏VO
     */
    GameVO get(Long id);

    /**
     * 新增游戏配置
     *
     * @param dto 游戏配置DTO
     */
    void save(GameDTO dto);

    /**
     * 更新游戏配置
     *
     * @param dto 游戏配置DTO
     */
    void update(GameDTO dto);

    /**
     * 物理删除游戏配置
     *
     * @param id 游戏ID
     */
    void delete(Long id);

    /**
     * 小程序端查询启用游戏列表（status=1）。
     *
     * @param entrance 入口枚举 code，可空；传入时只返回 entrances 包含该入口的游戏
     * @return 启用游戏列表
     */
    List<GameVO> listEnabled(String entrance);
}
