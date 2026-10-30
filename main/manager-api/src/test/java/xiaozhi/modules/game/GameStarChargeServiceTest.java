package xiaozhi.modules.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameStarChargeService;
import xiaozhi.modules.game.vo.GameStarChargeResult;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.StarEarnBizType;
import xiaozhi.modules.star.service.StarService;

/**
 * 消费型游戏按局扣费集成测试（真实 dev 库）。
 * 并发用例不加 @Transactional，参考 StarServiceTest。
 * 测试直接插入 ai_game 行（不校验字典），使用 CHARGE_TEST_ 前缀游戏类型，
 * 每个用例独立随机 userId，AfterEach 统一清理游戏行、星星账户与流水。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("游戏按局扣费集成测试")
class GameStarChargeServiceTest {

    @Autowired
    private GameStarChargeService chargeService;
    @Autowired
    private StarService starService;
    @Autowired
    private GameDao gameDao;
    @Autowired
    private StarAccountDao starAccountDao;
    @Autowired
    private StarTransactionDao starTransactionDao;

    /** 本用例产生的游戏行 id，统一清理 */
    private final List<Long> touchedGameIds = new ArrayList<>();
    /** 本用例产生的 userId，统一清理星星数据 */
    private final List<Long> touchedUsers = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : touchedGameIds) {
            gameDao.deleteById(id);
        }
        touchedGameIds.clear();
        for (Long uid : touchedUsers) {
            starTransactionDao.delete(new QueryWrapper<StarTransactionEntity>().eq("user_id", uid));
            starAccountDao.delete(new QueryWrapper<StarAccountEntity>().eq("user_id", uid));
        }
        touchedUsers.clear();
    }

    private long newUser() {
        long uid = 960000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000);
        touchedUsers.add(uid);
        return uid;
    }

    private String session() {
        return "sess-" + UUID.randomUUID();
    }

    /** 直接插入游戏配置行；earn/consume 可为冲突值以模拟存量脏配置 */
    private String newGame(Integer earn, Integer consume, Integer status) {
        GameEntity game = new GameEntity();
        // 与真实游戏类型错开，避免唯一索引 uk_ai_game_game_type 撞上运营数据
        game.setGameType("CHARGE_TEST_" + UUID.randomUUID().toString().substring(0, 8));
        game.setEarnStars(earn);
        game.setConsumeStars(consume);
        game.setDailyLimit(0);
        game.setEntrances("NOTE");
        game.setStatus(status);
        gameDao.insert(game);
        touchedGameIds.add(game.getId());
        return game.getGameType();
    }

    private String consumeGame(int consumeStars) {
        return newGame(0, consumeStars, 1);
    }

    private long consumeTxnCount(long uid) {
        return starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("type", "consume"));
    }

    // ---- 正常扣费 ----

    @Test
    @DisplayName("正常扣费：余额扣减，流水 game_play 类型，结果为本次新扣费")
    void charge_normal_deducts() {
        long uid = newUser();
        String gameType = consumeGame(5);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);

        GameStarChargeResult result = chargeService.chargeForPlay(uid, gameType, session());

        assertThat(result.getStatus()).isEqualTo(GameStarChargeResult.Status.NEW_CHARGE);
        assertThat(result.getChargedStars()).isEqualTo(5);
        assertThat(result.getTransactionId()).isNotNull();
        assertThat(starService.balance(uid)).isEqualTo(15);

        List<StarTransactionEntity> txns = starTransactionDao.selectList(
                new QueryWrapper<StarTransactionEntity>().eq("user_id", uid).eq("type", "consume"));
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).getBizType()).isEqualTo("game_play");
        assertThat(txns.get(0).getAmount()).isEqualTo(-5);
        // ref_id 为 64 位十六进制 SHA-256，适配 ref_id VARCHAR(64)
        assertThat(txns.get(0).getRefId()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("同局重试：返回原流水与原金额，不重复扣款，明确标记重复")
    void charge_sameSession_returnsOriginal() {
        long uid = newUser();
        String gameType = consumeGame(5);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);
        String sess = session();

        GameStarChargeResult first = chargeService.chargeForPlay(uid, gameType, sess);
        GameStarChargeResult retry = chargeService.chargeForPlay(uid, gameType, sess);

        assertThat(first.getStatus()).isEqualTo(GameStarChargeResult.Status.NEW_CHARGE);
        assertThat(retry.getStatus()).isEqualTo(GameStarChargeResult.Status.ALREADY_CHARGED);
        assertThat(retry.getChargedStars()).isEqualTo(5);
        assertThat(retry.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(starService.balance(uid)).isEqualTo(15);
        assertThat(consumeTxnCount(uid)).isEqualTo(1L);
    }

    @Test
    @DisplayName("不同局分别扣费：同一天多局各自扣款，不受每日奖励限制")
    void charge_differentSessions_chargedSeparately() {
        long uid = newUser();
        String gameType = consumeGame(5);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);

        GameStarChargeResult r1 = chargeService.chargeForPlay(uid, gameType, session());
        GameStarChargeResult r2 = chargeService.chargeForPlay(uid, gameType, session());
        GameStarChargeResult r3 = chargeService.chargeForPlay(uid, gameType, session());

        assertThat(r1.getStatus()).isEqualTo(GameStarChargeResult.Status.NEW_CHARGE);
        assertThat(r2.getStatus()).isEqualTo(GameStarChargeResult.Status.NEW_CHARGE);
        assertThat(r3.getStatus()).isEqualTo(GameStarChargeResult.Status.NEW_CHARGE);
        assertThat(starService.balance(uid)).isEqualTo(5);
        assertThat(consumeTxnCount(uid)).isEqualTo(3L);
    }

    // ---- 并发 ----

    @Test
    @DisplayName("并发同局：只扣一次，所有请求拿到同一条流水")
    void charge_concurrentSameSession_chargedOnce() throws Exception {
        long uid = newUser();
        String gameType = consumeGame(5);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);
        String sess = session();

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicLong newCharges = new AtomicLong();
        List<Long> txnIds = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    GameStarChargeResult r = chargeService.chargeForPlay(uid, gameType, sess);
                    ok.incrementAndGet();
                    txnIds.add(r.getTransactionId());
                    if (r.getStatus() == GameStarChargeResult.Status.NEW_CHARGE) {
                        newCharges.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(errors.get()).isZero();
        assertThat(ok.get()).isEqualTo(threads);
        // 恰好一次新扣费，其余为幂等命中；所有线程拿到同一条流水
        assertThat(newCharges.get()).isEqualTo(1);
        assertThat(txnIds).allMatch(id -> id.equals(txnIds.get(0)));
        assertThat(starService.balance(uid)).isEqualTo(15);
        assertThat(consumeTxnCount(uid)).isEqualTo(1L);
    }

    @Test
    @DisplayName("并发不同局(总额>余额)：余额非负且成功扣款总额不超过初始余额")
    void charge_concurrentDifferentSessions_neverNegative() throws Exception {
        long uid = newUser();
        String gameType = consumeGame(4);
        long initial = 10;
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), initial, null);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    GameStarChargeResult r = chargeService.chargeForPlay(uid, gameType, session());
                    if (r.getStatus() == GameStarChargeResult.Status.NEW_CHARGE) {
                        success.incrementAndGet();
                    }
                } catch (RenException e) {
                    // 余额不足属预期失败
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        long finalBalance = starService.balance(uid);
        assertThat(finalBalance).isGreaterThanOrEqualTo(0);
        // 每笔 4，初始 10，最多成功 2 笔
        assertThat(success.get()).isLessThanOrEqualTo(2);
        assertThat(finalBalance).isEqualTo(initial - success.get() * 4L);
        assertThat(consumeTxnCount(uid)).isEqualTo(success.get());
    }

    // ---- 防重复先于配置校验 ----

    @Test
    @DisplayName("已扣费后停用游戏：同局重试仍返回原结果，不重新扣款")
    void charge_retryAfterDisabled_returnsOriginal() {
        long uid = newUser();
        String gameType = consumeGame(5);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);
        String sess = session();
        GameStarChargeResult first = chargeService.chargeForPlay(uid, gameType, sess);

        // 模拟运营停用游戏
        GameEntity game = gameDao.selectOne(
                new QueryWrapper<GameEntity>().eq("game_type", gameType));
        game.setStatus(0);
        gameDao.updateById(game);

        GameStarChargeResult retry = chargeService.chargeForPlay(uid, gameType, sess);
        assertThat(retry.getStatus()).isEqualTo(GameStarChargeResult.Status.ALREADY_CHARGED);
        assertThat(retry.getChargedStars()).isEqualTo(5);
        assertThat(retry.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(starService.balance(uid)).isEqualTo(15);
    }

    @Test
    @DisplayName("已扣费后修改费用：同局重试按原金额返回，不按新配置重算")
    void charge_retryAfterFeeChange_returnsOriginalAmount() {
        long uid = newUser();
        String gameType = consumeGame(5);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);
        String sess = session();
        chargeService.chargeForPlay(uid, gameType, sess);

        // 模拟运营把费用从 5 改为 8
        GameEntity game = gameDao.selectOne(
                new QueryWrapper<GameEntity>().eq("game_type", gameType));
        game.setConsumeStars(8);
        gameDao.updateById(game);

        GameStarChargeResult retry = chargeService.chargeForPlay(uid, gameType, sess);
        assertThat(retry.getStatus()).isEqualTo(GameStarChargeResult.Status.ALREADY_CHARGED);
        assertThat(retry.getChargedStars()).isEqualTo(5);
        assertThat(starService.balance(uid)).isEqualTo(15);
    }

    // ---- 防御性校验 ----

    @Test
    @DisplayName("游戏不存在：拒绝新扣费，零写入")
    void charge_gameNotFound_rejected() {
        long uid = newUser();
        assertThatThrownBy(() -> chargeService.chargeForPlay(uid, "NO_SUCH_GAME_XYZ", session()))
                .isInstanceOf(RenException.class);
        assertThat(consumeTxnCount(uid)).isZero();
    }

    @Test
    @DisplayName("游戏已停用：拒绝新扣费，零写入")
    void charge_gameDisabled_rejected() {
        long uid = newUser();
        String gameType = newGame(0, 5, 0);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);

        assertThatThrownBy(() -> chargeService.chargeForPlay(uid, gameType, session()))
                .isInstanceOf(RenException.class);
        assertThat(starService.balance(uid)).isEqualTo(20);
        assertThat(consumeTxnCount(uid)).isZero();
    }

    @Test
    @DisplayName("存量冲突配置(奖扣同时为正)：明确拒绝，不静默采用消费金额")
    void charge_conflictedConfig_rejected() {
        long uid = newUser();
        // 模拟 ticket01 保存校验上线前的存量脏数据
        String gameType = newGame(3, 5, 1);
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 20, null);

        assertThatThrownBy(() -> chargeService.chargeForPlay(uid, gameType, session()))
                .isInstanceOf(RenException.class);
        assertThat(starService.balance(uid)).isEqualTo(20);
        assertThat(consumeTxnCount(uid)).isZero();
    }

    @Test
    @DisplayName("局次单号缺失：拒绝扣费，零写入")
    void charge_missingSessionId_rejected() {
        long uid = newUser();
        String gameType = consumeGame(5);
        assertThatThrownBy(() -> chargeService.chargeForPlay(uid, gameType, null))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> chargeService.chargeForPlay(uid, gameType, "  "))
                .isInstanceOf(RenException.class);
        assertThat(consumeTxnCount(uid)).isZero();
    }

    // ---- 免费/奖励型跳过 ----

    @Test
    @DisplayName("免费双零游戏：直接跳过，不建账户不写流水")
    void charge_freeGame_skipped() {
        long uid = newUser();
        String gameType = newGame(0, 0, 1);

        GameStarChargeResult result = chargeService.chargeForPlay(uid, gameType, session());

        assertThat(result.getStatus()).isEqualTo(GameStarChargeResult.Status.NO_CHARGE);
        assertThat(result.getChargedStars()).isZero();
        assertThat(result.getTransactionId()).isNull();
        // 免费游戏不创建星星账户、不产生任何流水
        assertThat(starAccountDao.selectCount(
                new QueryWrapper<StarAccountEntity>().eq("user_id", uid))).isZero();
        assertThat(starTransactionDao.selectCount(
                new QueryWrapper<StarTransactionEntity>().eq("user_id", uid))).isZero();
    }

    @Test
    @DisplayName("奖励型游戏调用扣费入口：跳过，不发生星星变动")
    void charge_earnOnlyGame_skipped() {
        long uid = newUser();
        String gameType = newGame(5, 0, 1);

        GameStarChargeResult result = chargeService.chargeForPlay(uid, gameType, session());

        assertThat(result.getStatus()).isEqualTo(GameStarChargeResult.Status.NO_CHARGE);
        assertThat(starAccountDao.selectCount(
                new QueryWrapper<StarAccountEntity>().eq("user_id", uid))).isZero();
        assertThat(consumeTxnCount(uid)).isZero();
    }

    // ---- 余额不足与失败回滚 ----

    @Test
    @DisplayName("余额不足：统一业务错误，账户与流水零写入，重试不视为已扣款")
    void charge_insufficientBalance_rejectedAndRetryable() {
        long uid = newUser();
        String gameType = consumeGame(5);
        String sess = session();
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 3, null);

        assertThatThrownBy(() -> chargeService.chargeForPlay(uid, gameType, sess))
                .isInstanceOf(RenException.class);
        assertThat(starService.balance(uid)).isEqualTo(3);
        assertThat(consumeTxnCount(uid)).isZero();

        // 补足余额后同一局重试：不得被失败残留误判为已扣款，应正常扣费
        starService.earn(uid, StarEarnBizType.SIGN_IN, "seed-" + UUID.randomUUID(), 10, null);
        GameStarChargeResult retry = chargeService.chargeForPlay(uid, gameType, sess);
        assertThat(retry.getStatus()).isEqualTo(GameStarChargeResult.Status.NEW_CHARGE);
        assertThat(retry.getChargedStars()).isEqualTo(5);
        assertThat(starService.balance(uid)).isEqualTo(8);
        assertThat(consumeTxnCount(uid)).isEqualTo(1L);
    }

    @Test
    @DisplayName("无账户且余额为零：余额不足拒绝，不创建账户")
    void charge_noAccount_insufficient() {
        long uid = newUser();
        String gameType = consumeGame(5);

        assertThatThrownBy(() -> chargeService.chargeForPlay(uid, gameType, session()))
                .isInstanceOf(RenException.class);
        assertThat(starAccountDao.selectCount(
                new QueryWrapper<StarAccountEntity>().eq("user_id", uid))).isZero();
        assertThat(consumeTxnCount(uid)).isZero();
    }
}
