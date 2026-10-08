package xiaozhi.modules.star;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
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
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarTransactionDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.StarConsumeBizType;
import xiaozhi.modules.star.enums.StarEarnBizType;
import xiaozhi.modules.star.service.StarService;

/**
 * 星星罐 service 接缝集成测试（真实 dev 库，OceanBase MySQL 模式）。
 * 并发用例不加 @Transactional：工作线程独立事务需看到测试数据，参考 InviteConsumeConcurrencyTest。
 * 每个用例用独立随机 userId + 唯一 refId 隔离，AfterEach 清理本用例产生的行。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("星星罐服务集成测试")
class StarServiceTest {

    @Autowired
    private StarService starService;
    @Autowired
    private StarAccountDao starAccountDao;
    @Autowired
    private StarTransactionDao starTransactionDao;

    /** 本用例产生的 userId 集合，统一清理 */
    private final List<Long> touchedUsers = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long uid : touchedUsers) {
            starTransactionDao.delete(new QueryWrapper<StarTransactionEntity>().eq("user_id", uid));
            starAccountDao.delete(new QueryWrapper<StarAccountEntity>().eq("user_id", uid));
        }
        touchedUsers.clear();
    }

    private long newUser() {
        long uid = 950000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000);
        touchedUsers.add(uid);
        return uid;
    }

    private String ref() {
        return "t-" + UUID.randomUUID();
    }

    // ---- 票1：earn 入账链路 ----

    @Test
    @DisplayName("earn 正常入账：余额增加，流水 type=earn/amount 正/balance_after 正确")
    void earn_normal_balanceAndTxn() {
        long uid = newUser();
        StarTransactionEntity txn = starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 5, "签到");

        assertThat(starService.balance(uid)).isEqualTo(5);
        assertThat(txn.getType()).isEqualTo("earn");
        assertThat(txn.getAmount()).isEqualTo(5);
        assertThat(txn.getBalanceAfter()).isEqualTo(5);
        assertThat(txn.getFulfillStatus()).isEqualTo("fulfilled");
    }

    @Test
    @DisplayName("earn 同 refId 重复调用：幂等，流水一条，余额不重复")
    void earn_sameRefId_idempotent() {
        long uid = newUser();
        String r = ref();
        starService.earn(uid, StarEarnBizType.SIGN_IN, r, 5, null);
        StarTransactionEntity second = starService.earn(uid, StarEarnBizType.SIGN_IN, r, 5, null);

        assertThat(starService.balance(uid)).isEqualTo(5);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("ref_id", r))).isEqualTo(1L);
        assertThat(second.getId()).isNotNull();
    }

    @Test
    @DisplayName("earn 缺 refId 或 amount≤0：拒绝，零写入")
    void earn_invalidInput_rejected() {
        long uid = newUser();
        assertThatThrownBy(() -> starService.earn(uid, StarEarnBizType.SIGN_IN, null, 5, null))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 0, null))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), -3, null))
                .isInstanceOf(RenException.class);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid))).isZero();
    }

    // ---- 票2：consume 扣减与余额保护 ----

    @Test
    @DisplayName("consume 正常扣减：流水 amount 负、balance_after 正确、exchange 即 fulfilled")
    void consume_normal_deducts() {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 10, null);
        StarTransactionEntity txn = starService.consume(uid, StarConsumeBizType.EXCHANGE, ref(), 4, null, "兑换");

        assertThat(starService.balance(uid)).isEqualTo(6);
        assertThat(txn.getType()).isEqualTo("consume");
        assertThat(txn.getAmount()).isEqualTo(-4);
        assertThat(txn.getBalanceAfter()).isEqualTo(6);
        assertThat(txn.getFulfillStatus()).isEqualTo("fulfilled");
    }

    @Test
    @DisplayName("consume 余额不足：业务异常，账户与流水零写入")
    void consume_insufficient_noWrite() {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 3, null);

        assertThatThrownBy(() -> starService.consume(uid, StarConsumeBizType.EXCHANGE, ref(), 5, null, null))
                .isInstanceOf(RenException.class);

        assertThat(starService.balance(uid)).isEqualTo(3);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("type", "consume"))).isZero();
    }

    @Test
    @DisplayName("consume 同 refId 重复：幂等，不重复扣")
    void consume_sameRefId_idempotent() {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 10, null);
        String r = ref();
        starService.consume(uid, StarConsumeBizType.LOTTERY, r, 4, null, null);
        starService.consume(uid, StarConsumeBizType.LOTTERY, r, 4, null, null);

        assertThat(starService.balance(uid)).isEqualTo(6);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("ref_id", r))).isEqualTo(1L);
    }

    @Test
    @DisplayName("并发扣同一账户(总额>余额)：余额≥0 且成功扣减总额≤初始余额")
    void consume_concurrent_neverNegative() throws Exception {
        long uid = newUser();
        long initial = 10;
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), initial, null);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    starService.consume(uid, StarConsumeBizType.EXCHANGE, ref(), 4, null, null);
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 余额不足/幂等冲突均属预期
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
        // 每笔 4，初始 10，最多成功 2 笔（8），第 3 笔必失败
        assertThat(success.get()).isLessThanOrEqualTo(2);
        assertThat(finalBalance).isEqualTo(initial - success.get() * 4L);
    }

    @Test
    @DisplayName("并发 consume 同 refId：全部幂等返回(非异常)，只扣一次")
    void consume_concurrent_sameRefId_idempotent() throws Exception {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 10, null);
        String r = ref();

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    StarTransactionEntity txn =
                            starService.consume(uid, StarConsumeBizType.EXCHANGE, r, 4, null, null);
                    if (txn != null && txn.getId() != null) {
                        ok.incrementAndGet();
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

        // 全部线程应拿到幂等结果而非 IllegalStateException；余额只扣一次；流水只一条
        assertThat(errors.get()).isZero();
        assertThat(ok.get()).isEqualTo(threads);
        assertThat(starService.balance(uid)).isEqualTo(6);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("ref_id", r))).isEqualTo(1L);
    }

    // ---- 票3：旅行预订与履约 ----

    @Test
    @DisplayName("consume(travel) 缺 prototype 拒绝；有 prototype 写入 pending")
    void consumeTravel_prototypeRequired_pending() {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 10, null);

        assertThatThrownBy(() -> starService.consume(uid, StarConsumeBizType.TRAVEL, ref(), 5, null, null))
                .isInstanceOf(RenException.class);

        StarTransactionEntity txn = starService.consume(uid, StarConsumeBizType.TRAVEL, ref(), 5, "KOI", null);
        assertThat(txn.getFulfillStatus()).isEqualTo("pending");
        assertThat(txn.getPetPrototype()).isEqualTo("KOI");
        assertThat(starService.balance(uid)).isEqualTo(5);
    }

    @Test
    @DisplayName("履约消耗最早 pending，回填日记ID，其余 pending 不动")
    void fulfill_consumesEarliest_onlyOne() {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 20, null);
        starService.consume(uid, StarConsumeBizType.TRAVEL, ref(), 5, "KOI", null);
        starService.consume(uid, StarConsumeBizType.TRAVEL, ref(), 5, "KOI", null);

        Optional<StarTransactionEntity> fulfilled =
                starService.consumePendingTravelPreorder(uid, "KOI", "diary-1");
        assertThat(fulfilled).isPresent();
        assertThat(fulfilled.get().getFulfillRefId()).isEqualTo("diary-1");
        assertThat(fulfilled.get().getFulfillStatus()).isEqualTo("fulfilled");

        // 还剩一笔 pending
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("biz_type", "travel").eq("fulfill_status", "pending")))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("无 pending 预订：履约返回空，零写入")
    void fulfill_noPending_empty() {
        long uid = newUser();
        Optional<StarTransactionEntity> result =
                starService.consumePendingTravelPreorder(uid, "RABBIT", "diary-x");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("履约幂等：同笔流水不会被并发履约两次")
    void fulfill_concurrent_onlyOnce() throws Exception {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 10, null);
        starService.consume(uid, StarConsumeBizType.TRAVEL, ref(), 5, "KOI", null);

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger fulfilledCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    if (starService.consumePendingTravelPreorder(uid, "KOI", "diary-" + idx).isPresent()) {
                        fulfilledCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(fulfilledCount.get()).isEqualTo(1);
        assertThat(starTransactionDao.selectCount(new QueryWrapper<StarTransactionEntity>()
                .eq("user_id", uid).eq("biz_type", "travel").eq("fulfill_status", "fulfilled")))
                .isEqualTo(1L);
    }

    // ---- 票4：流水分页查询 ----

    @Test
    @DisplayName("transactions 分页倒序、earn/consume 混排")
    void transactions_pagedDesc_mixed() {
        long uid = newUser();
        starService.earn(uid, StarEarnBizType.SIGN_IN, ref(), 10, null);
        starService.consume(uid, StarConsumeBizType.EXCHANGE, ref(), 3, null, null);
        starService.earn(uid, StarEarnBizType.AD_REWARD, ref(), 2, null);

        var page = starService.transactions(uid, 1, 10);
        assertThat(page.getTotal()).isEqualTo(3);
        // 倒序：最新(第3笔 earn)在首
        assertThat(page.getRecords().get(0).getBizType()).isEqualTo("ad_reward");
        assertThat(page.getRecords().get(2).getBizType()).isEqualTo("sign_in");
        // 混排含 earn 与 consume
        assertThat(page.getRecords().stream().map(StarTransactionEntity::getType).distinct())
                .containsExactlyInAnyOrder("earn", "consume");
    }
}
