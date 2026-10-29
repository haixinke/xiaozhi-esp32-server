package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.DailyNoteTodayService;
import xiaozhi.modules.dailynote.service.impl.DailyNoteTodayServiceImpl;
import xiaozhi.modules.dailynote.vo.DailyNoteTodayVO;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.enums.GameEntranceEnum;

/**
 * 当日小纸条与随机游戏 Service 集成测试（真实 dev 库）。
 * 通过可替换的 Clock 固定"当天"为 2160 年的专用日期段，避免依赖真实午夜，
 * 也避免与运营在真实日期上的既有数据相互干扰。
 * 每个用例自造数据，AfterEach 物理清理。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("当日小纸条查询集成测试")
class DailyNoteTodayServiceTest {

    @Autowired
    private DailyNoteTodayService dailyNoteTodayService;
    @Autowired
    private DailyNoteDao dailyNoteDao;
    @Autowired
    private DailyNoteOpenedDao dailyNoteOpenedDao;
    @Autowired
    private GameDao gameDao;

    /** 本用例使用的专用用户 ID（远离真实用户区间），统一清理 */
    private static final long USER_A = 9_999_000_001L;
    private static final long USER_B = 9_999_000_002L;

    /** 本用例创建的小纸条 id 与游戏 id，统一清理 */
    private final List<Long> noteIds = new ArrayList<>();
    private final List<Long> gameIds = new ArrayList<>();

    /**
     * 被本用例临时停用的既有游戏（id → 原状态）。
     * 合格游戏查询实时读库，dev 库中运营既有游戏（如 DRAW_TOGETHER、LISTEN_TO_YOU）
     * 同样启用且带 NOTE 入口，会污染断言；用例需要"无合格游戏"或精确枚举合格集合时，
     * 先把既有游戏临时停用，cleanup 中恢复原状态，不破坏运营数据。
     */
    private final Map<Long, Integer> sidelinedGames = new ConcurrentHashMap<>();

    /**
     * 把被测 Service 的可替换时钟拨到指定日期的正午（Asia/Shanghai），
     * 使"当天"完全由测试决定；正午取值远离日界，防止相邻用例串日期。
     */
    private void atShanghaiNoon(String date) {
        LocalDate day = LocalDate.parse(date);
        Instant noon = day.atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        ((DailyNoteTodayServiceImpl) dailyNoteTodayService).setClock(Clock.fixed(noon, ZoneId.of("Asia/Shanghai")));
    }

    @BeforeEach
    void resetClock() {
        // 每个用例先拨到一个绝无数据的日期，防止用例间时钟状态泄漏
        atShanghaiNoon("2160-12-31");
    }

    @AfterEach
    void cleanup() {
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
        gameIds.forEach(gameDao::deleteById);
        gameIds.clear();
        // 恢复被临时停用的既有游戏到原状态
        sidelinedGames.forEach((id, originalStatus) -> {
            GameEntity entity = gameDao.selectById(id);
            if (entity != null) {
                entity.setStatus(originalStatus);
                gameDao.updateById(entity);
            }
        });
        sidelinedGames.clear();
        // 已打开记录按本用例专用用户清理
        dailyNoteOpenedDao.delete(new QueryWrapper<DailyNoteOpenedEntity>()
                .in("user_id", USER_A, USER_B)
                .ge("note_date", java.sql.Date.valueOf("2160-01-01")));
    }

    /** 直接落库一条已打开记录（聚焦查询行为，不经过标记接口） */
    private void insertOpened(long userId, String date) {
        DailyNoteOpenedEntity entity = new DailyNoteOpenedEntity();
        entity.setUserId(userId);
        entity.setNoteDate(java.sql.Date.valueOf(date));
        entity.setOpenedAt(new java.util.Date());
        dailyNoteOpenedDao.insert(entity);
    }

    /** 临时停用所有本用例之外、启用且带 NOTE 入口的既有游戏，使合格集合只含本用例数据 */
    private void sidelinePreExistingNoteGames() {
        List<GameEntity> enabledNoteGames = gameDao.selectList(new QueryWrapper<GameEntity>()
                .eq("status", 1)
                .apply("FIND_IN_SET({0}, entrances)", GameEntranceEnum.NOTE.name()));
        for (GameEntity entity : enabledNoteGames) {
            if (gameIds.contains(entity.getId())) {
                continue;
            }
            sidelinedGames.putIfAbsent(entity.getId(), entity.getStatus());
            entity.setStatus(0);
            gameDao.updateById(entity);
        }
    }

    /** 直接落库一份小纸条（绕过管理 Service 的日期冲突校验，聚焦查询行为） */
    private void insertNote(String date, String title) {
        DailyNoteEntity entity = new DailyNoteEntity();
        entity.setNoteDate(LocalDate.parse(date));
        entity.setTitle(title);
        entity.setContent("正文-" + title);
        entity.setSolarTerm("霜降");
        dailyNoteDao.insert(entity);
        noteIds.add(entity.getId());
    }

    /**
     * 直接落库一条游戏配置。
     * game_type 有全局唯一约束（uk_ai_game_game_type），dev 库已占用 DRAW_TOGETHER / LISTEN_TO_YOU，
     * 因此本测试类统一使用 GAME_TYPE 字典中未被占用的 MEET_YOU / TRAVEL / WORK。
     */
    private void insertGame(String gameType, String entrances, int status) {
        GameEntity entity = new GameEntity();
        entity.setGameType(gameType);
        entity.setEarnStars(0);
        entity.setConsumeStars(0);
        entity.setDailyLimit(0);
        entity.setDescription("测试游戏");
        entity.setEntrances(entrances);
        entity.setStatus(status);
        gameDao.insert(entity);
        gameIds.add(entity.getId());
    }

    @Test
    @DisplayName("当天有小纸条且有合格游戏时返回完整日期、标题、正文、节气与游戏，不暴露小纸条 ID")
    void todayReturnsNoteAndGame() {
        atShanghaiNoon("2160-01-10");
        insertNote("2160-01-10", "今日小纸条");
        insertGame("MEET_YOU", "NOTE", 1);
        sidelinePreExistingNoteGames();

        DailyNoteTodayVO vo = dailyNoteTodayService.today(null);

        assertThat(vo).isNotNull();
        assertThat(vo.getDate()).isEqualTo("2160-01-10");
        assertThat(vo.getTitle()).isEqualTo("今日小纸条");
        assertThat(vo.getContent()).isEqualTo("正文-今日小纸条");
        assertThat(vo.getSolarTerm()).isEqualTo("霜降");
        assertThat(vo.getGame()).isNotNull();
        assertThat(vo.getGame().getId()).isIn(gameIds);
    }

    @Test
    @DisplayName("完整日期精确匹配：其他年份的同月同日不命中，相邻日不命中")
    void fullDateMatchNoYearRollover() {
        atShanghaiNoon("2160-02-15");
        // 同月同日但不同年份、以及相邻日，都不应被当作"当天"
        insertNote("2159-02-15", "去年同日");
        insertNote("2160-02-14", "昨天");
        insertNote("2160-02-16", "明天");
        insertGame("MEET_YOU", "NOTE", 1);

        assertThat(dailyNoteTodayService.today(null)).isNull();

        // 补上真正当天的小纸条后才返回
        insertNote("2160-02-15", "今天");
        DailyNoteTodayVO vo = dailyNoteTodayService.today(null);
        assertThat(vo).isNotNull();
        assertThat(vo.getDate()).isEqualTo("2160-02-15");
        assertThat(vo.getTitle()).isEqualTo("今天");
    }

    @Test
    @DisplayName("当日无小纸条时返回空，即使有合格游戏")
    void noNoteReturnsNull() {
        atShanghaiNoon("2160-03-01");
        insertGame("MEET_YOU", "NOTE", 1);

        assertThat(dailyNoteTodayService.today(null)).isNull();
    }

    @Test
    @DisplayName("无合格游戏时返回空：停用游戏、无 NOTE 入口游戏均不参与")
    void noEligibleGameReturnsNull() {
        atShanghaiNoon("2160-04-01");
        insertNote("2160-04-01", "标题");
        // 停用游戏不参与
        insertGame("MEET_YOU", "NOTE", 0);
        // 无 NOTE 入口的游戏不参与
        insertGame("TRAVEL", "ACTIVITY", 1);
        // 排除既有运营游戏的干扰，确保"无合格游戏"成立
        sidelinePreExistingNoteGames();

        assertThat(dailyNoteTodayService.today(null)).isNull();
    }

    @Test
    @DisplayName("游戏实时变更生效：停用或移除 NOTE 入口后立即不再参与")
    void gameChangesTakeEffectImmediately() {
        atShanghaiNoon("2160-05-01");
        insertNote("2160-05-01", "标题");
        insertGame("MEET_YOU", "NOTE", 1);
        Long gameId = gameIds.get(0);
        sidelinePreExistingNoteGames();

        assertThat(dailyNoteTodayService.today(null)).isNotNull();

        // 停用后立即查不到
        GameEntity entity = gameDao.selectById(gameId);
        entity.setStatus(0);
        gameDao.updateById(entity);
        assertThat(dailyNoteTodayService.today(null)).isNull();

        // 恢复启用但移除 NOTE 入口，同样查不到
        entity.setStatus(1);
        entity.setEntrances("ACTIVITY");
        gameDao.updateById(entity);
        assertThat(dailyNoteTodayService.today(null)).isNull();

        // 加回 NOTE 入口后恢复可选
        entity.setEntrances("NOTE");
        gameDao.updateById(entity);
        assertThat(dailyNoteTodayService.today(null)).isNotNull();
    }

    @Test
    @DisplayName("两个合格游戏多次查询均会被随机选中（不要求固定序列）")
    void randomSelectionCoversBothGames() {
        atShanghaiNoon("2160-06-01");
        insertNote("2160-06-01", "标题");
        insertGame("MEET_YOU", "NOTE", 1);
        insertGame("TRAVEL", "NOTE,ACTIVITY", 1);
        // 合格集合必须恰好是这两款游戏，排除既有运营游戏干扰
        sidelinePreExistingNoteGames();
        Set<Long> eligible = new HashSet<>(gameIds);

        Set<Long> observed = new HashSet<>();
        // 两款游戏各被抽中的概率随次数指数收敛；50 次未同时出现的概率约 2^-49，可忽略
        for (int i = 0; i < 50; i++) {
            DailyNoteTodayVO vo = dailyNoteTodayService.today(null);
            assertThat(vo).isNotNull();
            // 只会从合格游戏中选择
            assertThat(vo.getGame().getId()).isIn(eligible);
            observed.add(vo.getGame().getId());
        }
        assertThat(observed).isEqualTo(eligible);
    }

    @Test
    @DisplayName("Asia/Shanghai 日界：跨午夜瞬间前后查询的'当天'分别是前一天和后一天")
    void shanghaiMidnightBoundary() {
        // 冻结在 2160-07-02 00:00:00 Asia/Shanghai 前一瞬间 → 当天是 07-01
        Instant beforeMidnight = LocalDate.parse("2160-07-02").atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .minusNanos(1).toInstant();
        ((DailyNoteTodayServiceImpl) dailyNoteTodayService)
                .setClock(Clock.fixed(beforeMidnight, ZoneId.of("Asia/Shanghai")));
        insertNote("2160-07-01", "前一天");
        insertNote("2160-07-02", "后一天");
        insertGame("MEET_YOU", "NOTE", 1);

        DailyNoteTodayVO vo = dailyNoteTodayService.today(null);
        assertThat(vo).isNotNull();
        assertThat(vo.getDate()).isEqualTo("2160-07-01");

        // 拨过午夜 → 当天变为 07-02
        Instant afterMidnight = LocalDate.parse("2160-07-02").atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        ((DailyNoteTodayServiceImpl) dailyNoteTodayService)
                .setClock(Clock.fixed(afterMidnight, ZoneId.of("Asia/Shanghai")));

        DailyNoteTodayVO next = dailyNoteTodayService.today(null);
        assertThat(next).isNotNull();
        assertThat(next.getDate()).isEqualTo("2160-07-02");
        assertThat(next.getTitle()).isEqualTo("后一天");
    }

    @Test
    @DisplayName("已打开用户查询返回空：当日内容与游戏都在也不返回；未打开用户不受影响（跨设备由用户维度保证）")
    void openedUserSeesNullOthersUnaffected() {
        atShanghaiNoon("2160-08-01");
        insertNote("2160-08-01", "标题");
        insertGame("MEET_YOU", "NOTE", 1);
        sidelinePreExistingNoteGames();
        insertOpened(USER_A, "2160-08-01");

        // 已打开用户：即使内容与游戏齐备也返回空
        assertThat(dailyNoteTodayService.today(USER_A)).isNull();
        // 其他用户：不受 A 的已打开状态影响
        assertThat(dailyNoteTodayService.today(USER_B)).isNotNull();
    }

    @Test
    @DisplayName("已打开状态次日不继承：昨天打开过的用户今天仍可查询")
    void openedStateNotInheritedNextDay() {
        atShanghaiNoon("2160-08-02");
        insertNote("2160-08-02", "昨天");
        insertGame("MEET_YOU", "NOTE", 1);
        sidelinePreExistingNoteGames();
        // 用户打开的是昨天的小纸条
        insertOpened(USER_A, "2160-08-01");

        assertThat(dailyNoteTodayService.today(USER_A)).isNotNull();
    }

    @Test
    @DisplayName("内容删除期间查询为空，同日重建后原已打开用户仍为空、新用户可查看")
    void deleteAndRecreateKeepsOpenedState() {
        atShanghaiNoon("2160-08-03");
        insertNote("2160-08-03", "旧内容");
        insertGame("MEET_YOU", "NOTE", 1);
        sidelinePreExistingNoteGames();
        insertOpened(USER_A, "2160-08-03");

        // 物理删除当日内容：所有用户查询都为空
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
        assertThat(dailyNoteTodayService.today(USER_A)).isNull();
        assertThat(dailyNoteTodayService.today(USER_B)).isNull();

        // 同日重建（新 id）：原已打开用户仍为空，新用户可见
        insertNote("2160-08-03", "重建内容");
        assertThat(dailyNoteTodayService.today(USER_A)).isNull();
        assertThat(dailyNoteTodayService.today(USER_B)).isNotNull();
    }

    @Test
    @DisplayName("内容同日覆盖不重置已打开：更新内容字段后已打开用户仍返回空")
    void overwriteDoesNotResetOpenedState() {
        atShanghaiNoon("2160-08-04");
        insertNote("2160-08-04", "旧标题");
        insertGame("MEET_YOU", "NOTE", 1);
        sidelinePreExistingNoteGames();
        insertOpened(USER_A, "2160-08-04");

        // 导入整项覆盖同日期内容（保留记录身份）
        dailyNoteDao.update(null, new UpdateWrapper<DailyNoteEntity>()
                .eq("id", noteIds.get(0))
                .set("title", "新标题")
                .set("content", "新正文"));

        assertThat(dailyNoteTodayService.today(USER_A)).isNull();
        assertThat(dailyNoteTodayService.today(USER_B)).isNotNull();
        assertThat(dailyNoteTodayService.today(USER_B).getTitle()).isEqualTo("新标题");
    }
}
