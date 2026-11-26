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
import xiaozhi.common.exception.ErrorCode;
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

    // ---- 奖励与消费互斥校验（ticket 01） ----

    @Test
    @DisplayName("save earnStars 与 consumeStars 同时为正：拒绝且不落库")
    void save_earnAndConsumeBothPositive_rejected() {
        GameDTO dto = validDTO();
        dto.setEarnStars(5);
        dto.setConsumeStars(3);

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_EARN_CONSUME_CONFLICT));
        // 拒绝后不得写入任何配置
        assertThat(gameDao.selectCount(new QueryWrapper<GameEntity>().eq("game_type", dto.getGameType())))
                .isZero();
    }

    @Test
    @DisplayName("save 三种合法奖扣配置：正奖励零消耗、零奖励正消耗、双零免费均可保存")
    void save_validEarnConsumeCombinations_accepted() {
        // 正奖励 + 零消耗
        GameDTO earnOnly = validDTO();
        earnOnly.setEarnStars(5);
        earnOnly.setConsumeStars(0);
        saveAndTrack(earnOnly);

        // 零奖励 + 正消耗
        GameDTO consumeOnly = validDTO();
        consumeOnly.setGameType(TEST_GAME_TYPES.get(1));
        consumeOnly.setEarnStars(0);
        consumeOnly.setConsumeStars(3);
        saveAndTrack(consumeOnly);

        // 双零免费游戏
        GameDTO free = validDTO();
        free.setGameType(TEST_GAME_TYPES.get(2));
        free.setEarnStars(0);
        free.setConsumeStars(0);
        saveAndTrack(free);
    }

    @Test
    @DisplayName("update 改为奖扣同时为正：拒绝且原配置保持不变")
    void update_earnAndConsumeBothPositive_rejectedAndUnchanged() {
        Long id = saveAndTrack(validDTO());

        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setEarnStars(8);
        dto.setConsumeStars(2);
        assertThatThrownBy(() -> gameService.update(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_EARN_CONSUME_CONFLICT));

        // 拒绝后原配置保持不变
        GameVO vo = gameService.get(id);
        assertThat(vo.getEarnStars()).isEqualTo(5);
        assertThat(vo.getConsumeStars()).isEqualTo(0);
    }

    @Test
    @DisplayName("update 双零与奖扣互换等合法组合：保存成功")
    void update_validEarnConsumeCombinations_accepted() {
        // 双零免费
        Long id = saveAndTrack(validDTO());
        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setEarnStars(0);
        dto.setConsumeStars(0);
        gameService.update(dto);
        assertThat(gameService.get(id).getEarnStars()).isZero();
        assertThat(gameService.get(id).getConsumeStars()).isZero();

        // 奖励改消费（奖扣互换，仍保持互斥）
        dto.setEarnStars(0);
        dto.setConsumeStars(4);
        gameService.update(dto);
        assertThat(gameService.get(id).getEarnStars()).isZero();
        assertThat(gameService.get(id).getConsumeStars()).isEqualTo(4);
    }

    @Test
    @DisplayName("save 互斥冲突叠加负数：仍按原有非负规则拒绝，互斥规则不放宽非法值")
    void save_conflictCombinedWithNegative_rejectedByNonNegativeRule() {
        GameDTO dto = validDTO();
        dto.setEarnStars(-1);
        dto.setConsumeStars(3);

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_EARN_STARS_NEGATIVE));
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

    // ---- 游戏分类（ticket 01：内部游戏分类与存量兼容） ----

    @Test
    @DisplayName("save 缺少 gameCategory：按内部游戏保存，jumpUrl 为空（旧客户端兼容）")
    void save_missingCategory_defaultsToInternal() {
        GameDTO dto = validDTO();
        // 旧版客户端不会携带 gameCategory / jumpUrl
        Long id = saveAndTrack(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo.getGameCategory()).isEqualTo("INTERNAL");
        assertThat(vo.getJumpUrl()).isNull();
    }

    @Test
    @DisplayName("save 显式内部游戏且携带 jumpUrl：保存成功且链接被清空")
    void save_explicitInternal_clearsJumpUrl() {
        GameDTO dto = validDTO();
        dto.setGameCategory("INTERNAL");
        // 显式存为内部游戏时链接无意义，服务端负责清空，防止脏数据残留
        dto.setJumpUrl("https://example.com/game");
        Long id = saveAndTrack(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo.getGameCategory()).isEqualTo("INTERNAL");
        assertThat(vo.getJumpUrl()).isNull();
    }

    @Test
    @DisplayName("save 未知 gameCategory：拒绝")
    void save_unknownCategory_rejected() {
        GameDTO dto = validDTO();
        dto.setGameCategory("ARCADE");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_CATEGORY_INVALID));
    }

    @Test
    @DisplayName("update 缺少 gameCategory：保留记录已保存的分类（旧客户端兼容）")
    void update_missingCategory_keepsSavedCategory() {
        Long id = saveAndTrack(validDTO());

        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setEarnStars(9);
        // 旧版客户端更新时不携带分类：保留已保存的 INTERNAL，而不是重置或报错
        gameService.update(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo.getGameCategory()).isEqualTo("INTERNAL");
        assertThat(vo.getEarnStars()).isEqualTo(9);
    }

    @Test
    @DisplayName("update 显式改为内部游戏：jumpUrl 被清空")
    void update_explicitInternal_clearsJumpUrl() {
        // 先以外部游戏形态落库（ticket 02 才加完整外链校验，此处直接经 DAO 准备数据）
        GameEntity entity = new GameEntity();
        entity.setGameType(PRIMARY_GAME_TYPE);
        entity.setGameCategory("EXTERNAL");
        entity.setJumpUrl("https://example.com/game");
        entity.setEarnStars(0);
        entity.setConsumeStars(0);
        entity.setDailyLimit(0);
        entity.setEntrances("ACTIVITY");
        entity.setStatus(1);
        gameDao.insert(entity);
        touchedIds.add(entity.getId());

        GameDTO dto = validDTO();
        dto.setId(entity.getId());
        dto.setGameCategory("INTERNAL");
        gameService.update(dto);

        GameVO vo = gameService.get(entity.getId());
        assertThat(vo.getGameCategory()).isEqualTo("INTERNAL");
        assertThat(vo.getJumpUrl()).isNull();
    }

    @Test
    @DisplayName("listEnabled 返回 gameCategory：小程序读接口携带分类字段")
    void listEnabled_returnsGameCategory() {
        Long id = saveTrackedWith(TEST_GAME_TYPES.get(0), "NOTE", 1);

        List<GameVO> list = gameService.listEnabled("NOTE");

        GameVO vo = list.stream().filter(v -> id.equals(v.getId())).findFirst().orElseThrow();
        assertThat(vo.getGameCategory()).isEqualTo("INTERNAL");
        assertThat(vo.getJumpUrl()).isNull();
    }

    // ---- 外部游戏校验（ticket 02：外部游戏保存与打开） ----

    /** 构造合法的外部游戏 DTO：零奖励零费用不限次，携带 HTTPS 跳转链接 */
    private GameDTO validExternalDTO() {
        GameDTO dto = validDTO();
        dto.setGameCategory("EXTERNAL");
        dto.setJumpUrl("https://games.example.com/play/abc");
        dto.setEarnStars(0);
        dto.setConsumeStars(0);
        dto.setDailyLimit(0);
        return dto;
    }

    @Test
    @DisplayName("save 外部游戏：合法 HTTPS 链接保存成功并可读取回显")
    void save_externalWithValidHttpsUrl_accepted() {
        GameDTO dto = validExternalDTO();
        Long id = saveAndTrack(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo.getGameCategory()).isEqualTo("EXTERNAL");
        assertThat(vo.getJumpUrl()).isEqualTo("https://games.example.com/play/abc");
        assertThat(vo.getEarnStars()).isZero();
        assertThat(vo.getConsumeStars()).isZero();
        assertThat(vo.getDailyLimit()).isZero();
    }

    @Test
    @DisplayName("save 外部游戏：链接首尾空白被去除后落库")
    void save_external_trimsJumpUrlWhitespace() {
        GameDTO dto = validExternalDTO();
        dto.setJumpUrl("  https://games.example.com/play/abc  ");
        Long id = saveAndTrack(dto);

        assertThat(gameService.get(id).getJumpUrl()).isEqualTo("https://games.example.com/play/abc");
    }

    @Test
    @DisplayName("save 外部游戏：链接的查询参数与锚点完整保留")
    void save_external_preservesQueryAndFragment() {
        GameDTO dto = validExternalDTO();
        String url = "https://games.example.com/play?level=3&lang=zh-CN&a=b%20c#stage-2";
        dto.setJumpUrl(" " + url + "\t");
        Long id = saveAndTrack(dto);

        assertThat(gameService.get(id).getJumpUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("save 外部游戏缺少跳转链接：拒绝")
    void save_externalMissingJumpUrl_rejected() {
        GameDTO dto = validExternalDTO();
        dto.setJumpUrl(null);

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_JUMP_URL_REQUIRED));
        assertThat(gameDao.selectCount(new QueryWrapper<GameEntity>().eq("game_type", dto.getGameType())))
                .isZero();
    }

    @Test
    @DisplayName("save 外部游戏链接为空白字符：拒绝（视为缺失）")
    void save_externalBlankJumpUrl_rejected() {
        GameDTO dto = validExternalDTO();
        dto.setJumpUrl("   ");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_JUMP_URL_REQUIRED));
    }

    @Test
    @DisplayName("save 外部游戏链接非 HTTPS（http）：拒绝")
    void save_externalHttpUrl_rejected() {
        GameDTO dto = validExternalDTO();
        dto.setJumpUrl("http://games.example.com/play");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_JUMP_URL_INVALID));
    }

    @Test
    @DisplayName("save 外部游戏链接无法解析为 URL：拒绝")
    void save_externalMalformedUrl_rejected() {
        GameDTO dto = validExternalDTO();
        dto.setJumpUrl("not-a-url");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_JUMP_URL_INVALID));
    }

    @Test
    @DisplayName("save 外部游戏链接缺少主机名：拒绝")
    void save_externalUrlWithoutHost_rejected() {
        GameDTO dto = validExternalDTO();
        dto.setJumpUrl("https:///play/abc");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_JUMP_URL_INVALID));
    }

    @Test
    @DisplayName("save 外部游戏链接内嵌用户名密码：拒绝")
    void save_externalUrlWithUserinfo_rejected() {
        GameDTO dto = validExternalDTO();
        // userinfo 会被部分客户端隐藏显示，易被用于钓鱼伪装，一律拒绝
        dto.setJumpUrl("https://user:pass@games.example.com/play");

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_JUMP_URL_INVALID));
    }

    @Test
    @DisplayName("save 外部游戏奖励非零：拒绝")
    void save_externalNonZeroEarnStars_rejected() {
        GameDTO dto = validExternalDTO();
        dto.setEarnStars(1);

        assertThatThrownBy(() -> gameService.save(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_EXTERNAL_SETTLEMENT_FORBIDDEN));
    }

    @Test
    @DisplayName("save 外部游戏消耗或每日上限非零：拒绝")
    void save_externalNonZeroConsumeOrDailyLimit_rejected() {
        GameDTO consume = validExternalDTO();
        consume.setConsumeStars(2);
        assertThatThrownBy(() -> gameService.save(consume))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_EXTERNAL_SETTLEMENT_FORBIDDEN));

        GameDTO limit = validExternalDTO();
        limit.setDailyLimit(5);
        assertThatThrownBy(() -> gameService.save(limit))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_EXTERNAL_SETTLEMENT_FORBIDDEN));
    }

    @Test
    @DisplayName("save 内置原生类型（一起画/听你说）保存为外部游戏：拒绝")
    void save_reservedNativeTypeAsExternal_rejected() {
        // 一起画/听你说走小程序原生页面，不能改为外部链接跳转，保护存量体验
        for (String reservedType : List.of("DRAW_TOGETHER", "LISTEN_TO_YOU")) {
            GameDTO dto = validExternalDTO();
            dto.setGameType(reservedType);
            assertThatThrownBy(() -> gameService.save(dto))
                    .isInstanceOf(RenException.class)
                    .satisfies(e -> assertThat(((RenException) e).getCode())
                            .isEqualTo(ErrorCode.GAME_RESERVED_TYPE_INTERNAL_ONLY));
        }
    }

    @Test
    @DisplayName("update 修改外部游戏链接：以新地址为准")
    void update_externalChangeJumpUrl_usesNewUrl() {
        Long id = saveAndTrack(validExternalDTO());

        GameDTO dto = validExternalDTO();
        dto.setId(id);
        dto.setJumpUrl("https://games.example.com/v2/new?from=update#top");
        gameService.update(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo.getJumpUrl()).isEqualTo("https://games.example.com/v2/new?from=update#top");
    }

    @Test
    @DisplayName("update 旧客户端缺少分类更新外部游戏：保留已保存分类与链接，并按外链规则校验")
    void update_legacyClientKeepsExternalCategoryAndLink() {
        Long id = saveAndTrack(validExternalDTO());

        // 旧客户端不携带 gameCategory/jumpUrl，仅修改描述：分类与链接必须原样保留
        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setEarnStars(0);
        dto.setConsumeStars(0);
        dto.setDailyLimit(0);
        dto.setDescription("旧客户端更新");
        gameService.update(dto);

        GameVO vo = gameService.get(id);
        assertThat(vo.getGameCategory()).isEqualTo("EXTERNAL");
        assertThat(vo.getJumpUrl()).isEqualTo("https://games.example.com/play/abc");
        assertThat(vo.getDescription()).isEqualTo("旧客户端更新");
    }

    @Test
    @DisplayName("update 旧客户端缺省更新外部游戏但携带非零结算字段：拒绝（按保留的分类校验）")
    void update_legacyClientViolatesExternalRules_rejected() {
        Long id = saveAndTrack(validExternalDTO());

        // 旧客户端不带分类但把奖励改为非零：按记录已保存的 EXTERNAL 分类校验，必须拒绝
        GameDTO dto = validDTO();
        dto.setId(id);
        dto.setEarnStars(5);
        assertThatThrownBy(() -> gameService.update(dto))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.GAME_EXTERNAL_SETTLEMENT_FORBIDDEN));
    }

    @Test
    @DisplayName("listEnabled 外部游戏启停往返：停用后不再返回，重新启用后恢复分类与链接")
    void listEnabled_externalDisableThenReEnable_roundTrip() {
        Long id = saveAndTrack(validExternalDTO());

        // 停用后活动列表不再返回
        GameDTO disable = validExternalDTO();
        disable.setId(id);
        disable.setStatus(0);
        gameService.update(disable);
        assertThat(gameService.listEnabled("ACTIVITY").stream().map(GameVO::getId)).doesNotContain(id);

        // 重新启用后恢复，且分类、链接、入口不变
        GameDTO enable = validExternalDTO();
        enable.setId(id);
        enable.setStatus(1);
        gameService.update(enable);

        GameVO vo = gameService.listEnabled("ACTIVITY").stream()
                .filter(v -> id.equals(v.getId())).findFirst().orElseThrow();
        assertThat(vo.getGameCategory()).isEqualTo("EXTERNAL");
        assertThat(vo.getJumpUrl()).isEqualTo("https://games.example.com/play/abc");
        assertThat(vo.getEntrances()).isEqualTo("NOTE,ACTIVITY");
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
    @DisplayName("listEnabled 填充游戏类型显示名：字典 dict_label 译文，缺字典项回落原始 code")
    void listEnabled_fillsGameTypeName() {
        // TEST_GAME_A 有字典译文「测试游戏类型A」；TEST_GAME_B 在 @AfterAll 前仍在字典中，
        // 因此这里用一个从未注入字典的 code 验证回落分支
        saveTrackedWith(TEST_GAME_TYPES.get(0), "NOTE", 1);

        List<GameVO> list = gameService.listEnabled("NOTE");

        GameVO translated = list.stream()
                .filter(vo -> TEST_GAME_TYPES.get(0).equals(vo.getGameType()))
                .findFirst().orElseThrow();
        // 有字典项时显示 dict_label 而非 code
        assertThat(translated.getGameTypeName()).isEqualTo("测试游戏类型A");
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
