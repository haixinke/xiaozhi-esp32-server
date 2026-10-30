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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameStarRewardService;
import xiaozhi.modules.game.vo.GameStarRewardResult;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.service.StarService;

/**
 * 游戏每日首次完成奖励集成测试（真实 dev 库）。
 * 复用统一每日奖励能力；测试直接插入 ai_game 行（不校验字典），
 * 使用 REWARD_TEST_ 前缀游戏类型，每个用例独立随机 userId，
 * AfterEach 统一清理游戏行、奖励记录、星星账户与流水。
 * 并发用例不加 @Transactional，参考 GameStarChargeServiceTest。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("游戏每日首次完成奖励集成测试")
class GameStarRewardServiceTest {

    @Autowired
    private GameStarRewardService rewardService;
    @Autowired
    private StarService starService;
    @Autowired
    private GameDao gameDao;
    @Autowired
    private StarAccountDao starAccountDao;
    @Autowired
    private StarTransactionDao starTransactionDao;
    @Autowired
    private StarDailyRewardDao starDailyRewardDao;

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
            starDailyRewardDao.delete(new QueryWrapper<StarDailyRewardEntity>().eq("user_id", uid));
            starTransactionDao.delete(new QueryWrapper<StarTransactionEntity>().eq("user_id", uid));
            starAccountDao.delete(new QueryWrapper<StarAccountEntity>().eq("user_id", uid));
        }
        touchedUsers.clear();
    }

    private long newUser() {
        long uid = 970000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000);
        touchedUsers.add(uid);
        return uid;
    }

    /** 直接插入游戏配置行；earn/consume 可为冲突值以模拟存量脏配置 */
    private String newGame(Integer earn, Integer consume, Integer status) {
        GameEntity game = new GameEntity();
        // 与真实游戏类型错开，避免唯一索引 uk_ai_game_game_type 撞上运营数据
        game.setGameType("REWARD_TEST_" + UUID.randomUUID().toString().substring(0, 8));
        game.setEarnStars(earn);
        game.setConsumeStars(consume);
        game.setDailyLimit(0);
        game.setEntrances("NOTE");
        game.setStatus(status);
        gameDao.insert(game);
        touchedGameIds.add(game.getId());
        return game.getGameType();
    }

    private String rewardGame(int earnStars) {
        return newGame(earnStars, 0, 1);
    }

    private long rewardRecordCount(long uid) {
        return starDailyRewardDao.selectCount(new QueryWrapper<StarDailyRewardEntity>().eq("user_id", uid));
    }

    private long earnTxnCount(long uid) {
        return starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("type", "earn"));
    }

    private StarDailyRewardEntity rewardRecord(long uid, String gameType) {
        return starDailyRewardDao.selectOne(new QueryWrapper<StarDailyRewardEntity>()
                .eq("user_id", uid).eq("source_type", "GAME").eq("source_key", gameType));
    }

    // ---- 正常发奖 ----

    @Test
    @DisplayName("首次成功完成：按配置固定金额入账，奖励记录与流水一对一")
    void reward_firstCompletion_awardsConfiguredAmount() {
        long uid = newUser();
        String gameType = rewardGame(7);

        GameStarRewardResult result = rewardService.claimGameReward(uid, gameType);

        assertThat(result.getStatus()).isEqualTo(GameStarRewardResult.Status.NEW_REWARD);
        assertThat(result.getRewardedStars()).isEqualTo(7);
        assertThat(result.getTransactionId()).isNotNull();
        assertThat(starService.balance(uid)).isEqualTo(7);

        StarDailyRewardEntity record = rewardRecord(uid, gameType);
        assertThat(record).isNotNull();
        assertThat(record.getAmount()).isEqualTo(7);
        assertThat(record.getStarTransactionId()).isEqualTo(result.getTransactionId());

        StarTransactionEntity txn = starTransactionDao.selectById(result.getTransactionId());
        assertThat(txn.getType()).isEqualTo("earn");
        assertThat(txn.getBizType()).isEqualTo("game_reward");
        assertThat(txn.getAmount()).isEqualTo(7);
    }

    @Test
    @DisplayName("当日重复完成：返回已领取结果（原金额+原流水），不新增记录或流水")
    void reward_sameDayRepeat_returnsAlreadyClaimed() {
        long uid = newUser();
        String gameType = rewardGame(7);

        GameStarRewardResult first = rewardService.claimGameReward(uid, gameType);
        GameStarRewardResult second = rewardService.claimGameReward(uid, gameType);
        GameStarRewardResult third = rewardService.claimGameReward(uid, gameType);

        assertThat(second.getStatus()).isEqualTo(GameStarRewardResult.Status.ALREADY_REWARDED);
        assertThat(third.getStatus()).isEqualTo(GameStarRewardResult.Status.ALREADY_REWARDED);
        assertThat(second.getRewardedStars()).isEqualTo(7);
        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());

        assertThat(rewardRecordCount(uid)).isEqualTo(1);
        assertThat(earnTxnCount(uid)).isEqualTo(1);
        assertThat(starService.balance(uid)).isEqualTo(7);
    }

    @Test
    @DisplayName("不同游戏当天各获一份：同一用户两种游戏互不占资格")
    void reward_differentGames_eachAwarded() {
        long uid = newUser();
        String gameA = rewardGame(5);
        String gameB = rewardGame(9);

        GameStarRewardResult rA = rewardService.claimGameReward(uid, gameA);
        GameStarRewardResult rB = rewardService.claimGameReward(uid, gameB);

        assertThat(rA.getStatus()).isEqualTo(GameStarRewardResult.Status.NEW_REWARD);
        assertThat(rB.getStatus()).isEqualTo(GameStarRewardResult.Status.NEW_REWARD);
        assertThat(starService.balance(uid)).isEqualTo(14);
        assertThat(rewardRecordCount(uid)).isEqualTo(2);
    }

    @Test
    @DisplayName("用户隔离：A 已领取不影响 B 当天首次领取")
    void reward_crossUser_independent() {
        long userA = newUser();
        long userB = newUser();
        String gameType = rewardGame(6);

        rewardService.claimGameReward(userA, gameType);
        GameStarRewardResult rB = rewardService.claimGameReward(userB, gameType);

        assertThat(rB.getStatus()).isEqualTo(GameStarRewardResult.Status.NEW_REWARD);
        assertThat(rB.getRewardedStars()).isEqualTo(6);
        assertThat(starService.balance(userB)).isEqualTo(6);
    }

    @Test
    @DisplayName("次日重新获得资格：同一游戏隔天首次完成再发一份固定金额，不连续累加")
    void reward_nextDay_eligibleAgain() {
        long uid = newUser();
        String gameType = rewardGame(7);

        GameStarRewardResult day1 = rewardService.claimGameReward(uid, gameType);
        assertThat(day1.getRewardedStars()).isEqualTo(7);

        // 直接用 SQL 把首条记录日期拨到昨天，模拟隔日（时钟固定为真实今天）
        StarDailyRewardEntity record = rewardRecord(uid, gameType);
        java.sql.Date yesterday = new java.sql.Date(System.currentTimeMillis() - 24L * 3600 * 1000);
        StarDailyRewardEntity update = new StarDailyRewardEntity();
        update.setId(record.getId());
        update.setRewardDate(yesterday);
        starDailyRewardDao.updateById(update);

        GameStarRewardResult day2 = rewardService.claimGameReward(uid, gameType);
        assertThat(day2.getStatus()).isEqualTo(GameStarRewardResult.Status.NEW_REWARD);
        // 固定金额：第二天仍是 7，不 +1
        assertThat(day2.getRewardedStars()).isEqualTo(7);
        assertThat(starService.balance(uid)).isEqualTo(14);
        assertThat(rewardRecordCount(uid)).isEqualTo(2);
    }

    // ---- 防重复先于配置校验 ----

    @Test
    @DisplayName("已领取后修改奖励金额：当日重试返回原金额快照，不按新配置重算")
    void reward_retryAfterConfigChange_returnsOriginal() {
        long uid = newUser();
        String gameType = rewardGame(7);
        GameStarRewardResult first = rewardService.claimGameReward(uid, gameType);

        GameEntity game = gameDao.selectByGameType(gameType);
        game.setEarnStars(20);
        gameDao.updateById(game);

        GameStarRewardResult retry = rewardService.claimGameReward(uid, gameType);
        assertThat(retry.getStatus()).isEqualTo(GameStarRewardResult.Status.ALREADY_REWARDED);
        assertThat(retry.getRewardedStars()).isEqualTo(7);
        assertThat(retry.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(starService.balance(uid)).isEqualTo(7);
    }

    @Test
    @DisplayName("已领取后停用游戏：当日重试仍返回原结果，不报错")
    void reward_retryAfterDisabled_returnsOriginal() {
        long uid = newUser();
        String gameType = rewardGame(7);
        GameStarRewardResult first = rewardService.claimGameReward(uid, gameType);

        GameEntity game = gameDao.selectByGameType(gameType);
        game.setStatus(0);
        gameDao.updateById(game);

        GameStarRewardResult retry = rewardService.claimGameReward(uid, gameType);
        assertThat(retry.getStatus()).isEqualTo(GameStarRewardResult.Status.ALREADY_REWARDED);
        assertThat(retry.getRewardedStars()).isEqualTo(7);
        assertThat(retry.getTransactionId()).isEqualTo(first.getTransactionId());
    }

    @Test
    @DisplayName("已领取后删除游戏：当日重试仍返回原结果")
    void reward_retryAfterDeleted_returnsOriginal() {
        long uid = newUser();
        String gameType = rewardGame(7);
        GameStarRewardResult first = rewardService.claimGameReward(uid, gameType);

        GameEntity game = gameDao.selectByGameType(gameType);
        gameDao.deleteById(game.getId());
        touchedGameIds.remove(game.getId());

        GameStarRewardResult retry = rewardService.claimGameReward(uid, gameType);
        assertThat(retry.getStatus()).isEqualTo(GameStarRewardResult.Status.ALREADY_REWARDED);
        assertThat(retry.getRewardedStars()).isEqualTo(7);
        assertThat(retry.getTransactionId()).isEqualTo(first.getTransactionId());
    }

    // ---- 新发奖的防御性校验 ----

    @Test
    @DisplayName("游戏不存在：拒绝新发奖，零写入")
    void reward_gameNotFound_rejected() {
        long uid = newUser();
        assertThatThrownBy(() -> rewardService.claimGameReward(uid, "NO_SUCH_GAME_XYZ"))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.GAME_NOT_FOUND);
        assertThat(rewardRecordCount(uid)).isZero();
        assertThat(earnTxnCount(uid)).isZero();
    }

    @Test
    @DisplayName("游戏已停用：拒绝新发奖，零写入")
    void reward_gameDisabled_rejected() {
        long uid = newUser();
        String gameType = newGame(7, 0, 0);

        assertThatThrownBy(() -> rewardService.claimGameReward(uid, gameType))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.GAME_DISABLED);
        assertThat(rewardRecordCount(uid)).isZero();
    }

    @Test
    @DisplayName("存量冲突配置(奖扣同时为正)：明确拒绝 10375，不静默选取奖励金额")
    void reward_conflictedConfig_rejected() {
        long uid = newUser();
        // 模拟 ticket01 保存校验上线前的存量脏数据
        String gameType = newGame(7, 5, 1);

        assertThatThrownBy(() -> rewardService.claimGameReward(uid, gameType))
                .isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.GAME_STAR_CONFIG_CONFLICT);
        assertThat(rewardRecordCount(uid)).isZero();
        assertThat(earnTxnCount(uid)).isZero();
    }

    // ---- 跳过场景 ----

    @Test
    @DisplayName("免费双零游戏：直接跳过，不建账户、不写奖励记录与流水，不占资格")
    void reward_freeGame_skipped() {
        long uid = newUser();
        String gameType = newGame(0, 0, 1);

        GameStarRewardResult result = rewardService.claimGameReward(uid, gameType);

        assertThat(result.getStatus()).isEqualTo(GameStarRewardResult.Status.NO_REWARD);
        assertThat(result.getRewardedStars()).isZero();
        assertThat(result.getTransactionId()).isNull();
        // 免费游戏不创建星星账户、不产生任何星星业务数据
        assertThat(starAccountDao.selectCount(
                new QueryWrapper<StarAccountEntity>().eq("user_id", uid))).isZero();
        assertThat(rewardRecordCount(uid)).isZero();
        assertThat(earnTxnCount(uid)).isZero();
    }

    @Test
    @DisplayName("消费型游戏调用奖励入口：跳过，不发生星星变动，不占当天奖励资格")
    void reward_consumeGame_skipped() {
        long uid = newUser();
        String gameType = newGame(0, 5, 1);

        GameStarRewardResult result = rewardService.claimGameReward(uid, gameType);

        assertThat(result.getStatus()).isEqualTo(GameStarRewardResult.Status.NO_REWARD);
        assertThat(starAccountDao.selectCount(
                new QueryWrapper<StarAccountEntity>().eq("user_id", uid))).isZero();
        assertThat(rewardRecordCount(uid)).isZero();
    }

    @Test
    @DisplayName("免费游戏改为奖励型后：后续成功完成可正常领取（跳过不占资格）")
    void reward_freeTurnedEarnable_laterClaimWorks() {
        long uid = newUser();
        String gameType = newGame(0, 0, 1);

        GameStarRewardResult skipped = rewardService.claimGameReward(uid, gameType);
        assertThat(skipped.getStatus()).isEqualTo(GameStarRewardResult.Status.NO_REWARD);

        // 运营把免费游戏改为奖励型
        GameEntity game = gameDao.selectByGameType(gameType);
        game.setEarnStars(6);
        gameDao.updateById(game);

        GameStarRewardResult result = rewardService.claimGameReward(uid, gameType);
        assertThat(result.getStatus()).isEqualTo(GameStarRewardResult.Status.NEW_REWARD);
        assertThat(result.getRewardedStars()).isEqualTo(6);
        assertThat(starService.balance(uid)).isEqualTo(6);
    }

    // ---- 并发 ----

    @Test
    @DisplayName("并发完成：多线程同时领奖只入账一次，余额与记录一致")
    void reward_concurrent_awardOnce() throws Exception {
        long uid = newUser();
        String gameType = rewardGame(7);

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger newRewards = new AtomicInteger();
        List<Long> txnIds = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    GameStarRewardResult r = rewardService.claimGameReward(uid, gameType);
                    ok.incrementAndGet();
                    if (r.getStatus() == GameStarRewardResult.Status.NEW_REWARD) {
                        newRewards.incrementAndGet();
                    }
                    if (r.getTransactionId() != null) {
                        txnIds.add(r.getTransactionId());
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
        // 恰好一次新发奖，其余命中已领取；所有线程拿到同一条流水
        assertThat(newRewards.get()).isEqualTo(1);
        assertThat(txnIds).hasSize(threads);
        assertThat(txnIds).allMatch(id -> id.equals(txnIds.get(0)));
        assertThat(starService.balance(uid)).isEqualTo(7);
        assertThat(rewardRecordCount(uid)).isEqualTo(1);
        assertThat(earnTxnCount(uid)).isEqualTo(1);
    }
}
