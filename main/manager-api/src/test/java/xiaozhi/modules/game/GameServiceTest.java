package xiaozhi.modules.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.validator.ValidatorUtils;
import xiaozhi.common.validator.group.AddGroup;
import xiaozhi.common.validator.group.DefaultGroup;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.dto.GameDTO;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameService;
import xiaozhi.modules.game.vo.GameVO;
import xiaozhi.modules.sys.dao.SysDictDataDao;
import xiaozhi.modules.sys.dao.SysDictTypeDao;
import xiaozhi.modules.sys.dto.SysDictDataDTO;
import xiaozhi.modules.sys.entity.SysDictDataEntity;
import xiaozhi.modules.sys.entity.SysDictTypeEntity;
import xiaozhi.modules.sys.service.SysDictDataService;

/**
 * 游戏管理 Service 集成测试（真实 dev 库）。
 * 每个用例独立数据，AfterEach 清理本用例产生的行。
 *
 * <p>ai_game.game_type 唯一索引 + GAME_TYPE 字典校验意味着测试不能使用真实游戏类型：
 * 真实类型（如 DRAW_TOGETHER）随时可能被运营在智控台配置，导致 save 报 10357。
 * 因此本类在 @BeforeAll 向 GAME_TYPE 字典注入专用测试类型，@AfterAll 删除，
 * 使测试与真实运维数据互不干扰。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("游戏管理服务集成测试")
class GameServiceTest {

    /** 专用测试游戏类型，仅在本类执行期间存在于 GAME_TYPE 字典中 */
    private static final List<String> TEST_GAME_TYPES = List.of("TEST_GAME_A", "TEST_GAME_B", "TEST_GAME_C");
    private static final String PRIMARY_GAME_TYPE = TEST_GAME_TYPES.get(0);

    @Autowired
    private GameService gameService;
    @Autowired
    private GameDao gameDao;
    @Autowired
    private SysDictDataService sysDictDataService;
    @Autowired
    private SysDictDataDao sysDictDataDao;
    @Autowired
    private SysDictTypeDao sysDictTypeDao;

    /** GAME_TYPE 字典类型 id，由 @BeforeAll 从库中查出 */
    private Long gameTypeDictTypeId;

    /** 本用例产生的 game id 集合，统一清理 */
    private final List<Long> touchedIds = new ArrayList<>();

    /**
     * 向 GAME_TYPE 字典注入专用测试类型。
     * 若上次异常退出残留同名项，先复用（记录其 id 以便 @AfterAll 一并清理）。
     */
    @BeforeAll
    void ensureTestGameTypes() {
        SysDictTypeEntity dictType = sysDictTypeDao.selectOne(
                new QueryWrapper<SysDictTypeEntity>().eq("dict_type", Constant.DictType.GAME_TYPE.getValue()));
        gameTypeDictTypeId = dictType.getId();

        for (int i = 0; i < TEST_GAME_TYPES.size(); i++) {
            String code = TEST_GAME_TYPES.get(i);
            Long exists = sysDictDataDao.selectCount(new QueryWrapper<SysDictDataEntity>()
                    .eq("dict_type_id", gameTypeDictTypeId)
                    .eq("dict_value", code));
            if (exists != null && exists > 0) {
                // 残留项直接复用，避免重复插入
                continue;
            }
            SysDictDataDTO dto = new SysDictDataDTO();
            dto.setDictTypeId(gameTypeDictTypeId);
            dto.setDictLabel("测试游戏类型" + (char) ('A' + i));
            dto.setDictValue(code);
            dto.setSort(90 + i);
            // 走 service 而非直接 insert：顺带失效 Redis 中的字典缓存
            sysDictDataService.save(dto);
        }
    }

    /** 删除专用测试类型及其残留游戏配置，不触碰真实运维数据 */
    @AfterAll
    void removeTestGameTypes() {
        gameDao.delete(new QueryWrapper<GameEntity>().in("game_type", TEST_GAME_TYPES));

        List<SysDictDataEntity> testDictItems = sysDictDataDao.selectList(new QueryWrapper<SysDictDataEntity>()
                .eq("dict_type_id", gameTypeDictTypeId)
                .in("dict_value", TEST_GAME_TYPES));
        if (!testDictItems.isEmpty()) {
            sysDictDataService.delete(testDictItems.stream().map(SysDictDataEntity::getId).toArray(Long[]::new));
        }
    }

    @AfterEach
    void cleanup() {
        for (Long id : touchedIds) {
            gameDao.deleteById(id);
        }
        touchedIds.clear();
    }

    /** 构造合法 DTO，快速创建一条游戏记录（使用专用测试类型，避开真实数据） */
    private GameDTO validDTO() {
        GameDTO dto = new GameDTO();
        dto.setGameType(PRIMARY_GAME_TYPE);
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
        assertThat(vo.getGameType()).isEqualTo(PRIMARY_GAME_TYPE);
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

    // ---- DTO Bean Validation（控制器层 @NotNull 必填校验） ----

    @Test
    @DisplayName("DTO 数值字段为 null：Bean Validation 拒绝（@NotNull）")
    void dto_nullNumericFields_rejectedByBeanValidation() {
        GameDTO dto = validDTO();

        // earnStars 为 null
        dto.setEarnStars(null);
        assertThatThrownBy(() -> ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class))
                .isInstanceOf(RenException.class);
        dto.setEarnStars(5);

        // consumeStars 为 null
        dto.setConsumeStars(null);
        assertThatThrownBy(() -> ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class))
                .isInstanceOf(RenException.class);
        dto.setConsumeStars(0);

        // dailyLimit 为 null
        dto.setDailyLimit(null);
        assertThatThrownBy(() -> ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("DTO 数值字段为负数：Bean Validation 拒绝（@Min(0)）")
    void dto_negativeNumericFields_rejectedByBeanValidation() {
        GameDTO dto = validDTO();
        dto.setEarnStars(-1);

        assertThatThrownBy(() -> ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class))
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

    // ---- listEnabled（小程序端游戏列表） ----

    /** 用专用测试 gameType 隔离本组用例，避免受库中既有数据影响 */
    private Long saveTrackedWith(String gameType, String entrances, Integer status) {
        GameDTO dto = validDTO();
        dto.setGameType(gameType);
        dto.setEntrances(entrances);
        dto.setStatus(status);
        return saveAndTrack(dto);
    }

    @Test
    @DisplayName("listEnabled 不传入口：只返回启用游戏，停用游戏不出现")
    void listEnabled_noEntrance_returnsOnlyEnabled() {
        Long enabledId = saveTrackedWith(TEST_GAME_TYPES.get(0), "NOTE,ACTIVITY", 1);
        Long disabledId = saveTrackedWith(TEST_GAME_TYPES.get(1), "NOTE", 0);

        List<GameVO> list = gameService.listEnabled(null);

        List<Long> ids = list.stream().map(GameVO::getId).toList();
        assertThat(ids).contains(enabledId);
        assertThat(ids).doesNotContain(disabledId);
        assertThat(list).allMatch(vo -> vo.getStatus() == 1);
    }

    @Test
    @DisplayName("listEnabled 按入口过滤：匹配单入口与逗号分隔多入口的记录")
    void listEnabled_withEntrance_filtersByContains() {
        Long multiEntranceId = saveTrackedWith(TEST_GAME_TYPES.get(0), "NOTE,ACTIVITY", 1);
        Long singleEntranceId = saveTrackedWith(TEST_GAME_TYPES.get(1), "NOTE", 1);
        Long otherEntranceId = saveTrackedWith(TEST_GAME_TYPES.get(2), "ACTIVITY", 1);

        List<GameVO> list = gameService.listEnabled("NOTE");

        List<Long> ids = list.stream().map(GameVO::getId).toList();
        // NOTE 应匹配 entrances 为 "NOTE" 和 "NOTE,ACTIVITY" 的记录
        assertThat(ids).contains(multiEntranceId, singleEntranceId);
        // 不应匹配只有 ACTIVITY 的记录
        assertThat(ids).doesNotContain(otherEntranceId);
    }

    @Test
    @DisplayName("listEnabled 入口过滤同时排除停用游戏")
    void listEnabled_withEntrance_excludesDisabled() {
        Long enabledId = saveTrackedWith(TEST_GAME_TYPES.get(0), "NOTE", 1);
        Long disabledId = saveTrackedWith(TEST_GAME_TYPES.get(1), "NOTE", 0);

        List<GameVO> list = gameService.listEnabled("NOTE");

        List<Long> ids = list.stream().map(GameVO::getId).toList();
        assertThat(ids).contains(enabledId);
        assertThat(ids).doesNotContain(disabledId);
    }
}
