package xiaozhi.modules.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.dto.GameDTO;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameService;
import xiaozhi.modules.game.vo.GameVO;

/**
 * 游戏管理 Service 集成测试（真实 dev 库）。
 * 每个用例独立数据，AfterEach 清理本用例产生的行。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("游戏管理服务集成测试")
class GameServiceTest {

    @Autowired
    private GameService gameService;
    @Autowired
    private GameDao gameDao;

    /** 本用例产生的 game id 集合，统一清理 */
    private final List<Long> touchedIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : touchedIds) {
            gameDao.deleteById(id);
        }
        touchedIds.clear();
    }

    /** 构造合法 DTO，快速创建一条游戏记录 */
    private GameDTO validDTO() {
        GameDTO dto = new GameDTO();
        dto.setGameType("DRAW_TOGETHER");
        dto.setEarnStars(5);
        dto.setConsumeStars(0);
        dto.setDailyLimit(3);
        dto.setIcon(null);
        dto.setDescription("一起画游戏");
        dto.setEntrances("NOTE,ACTIVITY");
        dto.setStatus(1);
        return dto;
    }

    private Long saveAndTrack(GameDTO dto) {
        gameService.save(dto);
        // 查回刚插入的记录获取 id
        GameEntity entity = gameDao.selectOne(
                new QueryWrapper<GameEntity>().eq("game_type", dto.getGameType()).orderByDesc("id").last("LIMIT 1"));
        touchedIds.add(entity.getId());
        return entity.getId();
    }

    // ---- save 成功路径 ----

    @Test
    @DisplayName("save 正常新增：字段完整写入，可查到")
    void save_normal_createsRecord() {
        GameDTO dto = validDTO();
        Long id = saveAndTrack(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo).isNotNull();
        assertThat(vo.getGameType()).isEqualTo("DRAW_TOGETHER");
        assertThat(vo.getEarnStars()).isEqualTo(5);
        assertThat(vo.getConsumeStars()).isEqualTo(0);
        assertThat(vo.getDailyLimit()).isEqualTo(3);
        assertThat(vo.getEntrances()).isEqualTo("NOTE,ACTIVITY");
        assertThat(vo.getStatus()).isEqualTo(1);
    }

    // ---- save 校验拒绝路径 ----

    @Test
    @DisplayName("save game_type 不在 GAME_TYPE 字典中：拒绝")
    void save_invalidGameType_rejected() {
        GameDTO dto = validDTO();
        dto.setGameType("NOT_EXIST_TYPE");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("save entrances 为空：拒绝")
    void save_emptyEntrances_rejected() {
        GameDTO dto = validDTO();
        dto.setEntrances("");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("save entrances 含非法枚举值：拒绝")
    void save_invalidEntrance_rejected() {
        GameDTO dto = validDTO();
        dto.setEntrances("NOTE,INVALID_ENTRANCE");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("save earnStars 为负数：拒绝")
    void save_negativeEarnStars_rejected() {
        GameDTO dto = validDTO();
        dto.setEarnStars(-1);

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("save consumeStars 为负数：拒绝")
    void save_negativeConsumeStars_rejected() {
        GameDTO dto = validDTO();
        dto.setConsumeStars(-1);

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("save dailyLimit 为负数：拒绝")
    void save_negativeDailyLimit_rejected() {
        GameDTO dto = validDTO();
        dto.setDailyLimit(-1);

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class);
    }

    // ---- update ----

    @Test
    @DisplayName("update 正常修改：字段更新成功")
    void update_normal_updatesRecord() {
        Long id = saveAndTrack(validDTO());

        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setEarnStars(10);
        dto.setDailyLimit(0);
        dto.setDescription("更新后的描述");
        gameService.update(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo.getEarnStars()).isEqualTo(10);
        assertThat(vo.getDailyLimit()).isEqualTo(0);
        assertThat(vo.getDescription()).isEqualTo("更新后的描述");
    }

    @Test
    @DisplayName("update 切换 status 启用/停用：状态变更成功")
    void update_toggleStatus_works() {
        Long id = saveAndTrack(validDTO());

        // 停用
        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setStatus(0);
        gameService.update(dto);
        assertThat(gameService.get(id).getStatus()).isEqualTo(0);

        // 启用
        dto.setStatus(1);
        gameService.update(dto);
        assertThat(gameService.get(id).getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("update 非法 game_type：拒绝")
    void update_invalidGameType_rejected() {
        Long id = saveAndTrack(validDTO());

        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setGameType("FAKE_TYPE");

        assertThatThrownBy(() -> gameService.update(dto))
                .isInstanceOf(RenException.class);
    }

    // ---- delete ----

    @Test
    @DisplayName("delete 物理删除：记录不存在")
    void delete_normal_removesRecord() {
        Long id = saveAndTrack(validDTO());

        gameService.delete(id);

        assertThat(gameDao.selectById(id)).isNull();
    }

    // ---- page ----

    @Test
    @DisplayName("page 分页查询：返回分页数据")
    void page_normal_returnsPagedData() {
        saveAndTrack(validDTO());

        Map<String, Object> params = new HashMap<>();
        params.put("page", "1");
        params.put("limit", "10");
        PageData<GameVO> page = gameService.page(params);

        assertThat(page.getTotal()).isGreaterThanOrEqualTo(1);
        assertThat(page.getList()).isNotEmpty();
    }
}
