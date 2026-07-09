package xiaozhi.modules.invite;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.exception.RenException;
import xiaozhi.modules.invite.dao.InviteCodeDao;
import xiaozhi.modules.invite.dao.InviteUsageDao;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.entity.InviteUsageEntity;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.invite.vo.InviteCodeVO;

/**
 * 邀请码消耗并发集成测试。
 *
 * <p>授权偏差说明：brief 中本类标注了 {@code @Transactional}，但那样会让 {@code createEnterpriseCode}
 * 的插入在测试线程事务中保持未提交状态；而工作线程通过 {@link ExecutorService} 在各自独立的事务中
 * 执行 {@code inviteService.consume}（Spring 事务同步管理器是线程本地的），任何隔离级别下都无法
 * 看到测试线程未提交的数据，导致 {@code selectByCodeForUpdate(code)} 返回 null，全部以"邀请码无效"失败。
 *
 * <p>修复方案（已授权）：移除类上的 {@code @Transactional}，使 {@code createEnterpriseCode}
 * （自身 {@code @Transactional}）在独立事务中提交，工作线程可见；通过 {@link AfterEach} 清理
 * 测试产生的企业码及其使用记录，避免污染 dev 库。
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("邀请码消耗并发集成测试")
class InviteConsumeConcurrencyTest {

    @Autowired
    private InviteService inviteService;

    @Autowired
    private InviteCodeDao inviteCodeDao;

    @Autowired
    private InviteUsageDao inviteUsageDao;

    /** 记录测试创建的企业码 ID，供 {@link #cleanup()} 清理。 */
    private final List<Long> createdCodeIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdCodeIds) {
            inviteUsageDao.delete(new QueryWrapper<InviteUsageEntity>().eq("code_id", id));
            inviteCodeDao.deleteById(id);
        }
        createdCodeIds.clear();
    }

    @Test
    @DisplayName("同被邀请人并发消耗同码：usage 仅 1 条，used_count 仅 +1")
    void sameInvitee_concurrent_idempotent() throws Exception {
        InviteCodeVO code = createEnterpriseCode(10);
        Long invitee = 900001L;

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyUsed = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    var vo = inviteService.consume(code.getCode(), invitee);
                    if ("success".equals(vo.getMessage())) {
                        success.incrementAndGet();
                    } else if (vo.getMessage().contains("已使用")) {
                        alreadyUsed.incrementAndGet();
                    }
                } catch (RenException e) {
                    errors.incrementAndGet();
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

        // 契约保证：同一被邀请人对同一码仅一次成功消耗（幂等），其余均得"已使用"。
        assertThat(success.get()).isEqualTo(1);
        assertThat(alreadyUsed.get() + errors.get()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("多被邀请人抢 remaining=2 的码：恰好 2 人成功")
    void multipleInvitees_raceForTwoSeats() throws Exception {
        InviteCodeVO code = createEnterpriseCode(2);
        int threads = 3;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        List<Long> invitees = new ArrayList<>();
        for (long i = 1; i <= threads; i++) {
            invitees.add(910000L + i);
        }

        for (Long invitee : invitees) {
            pool.submit(() -> {
                try {
                    start.await();
                    var vo = inviteService.consume(code.getCode(), invitee);
                    if ("success".equals(vo.getMessage())) {
                        success.incrementAndGet();
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

        // 契约保证：quota=2 时恰好 2 人成功，第三人得"邀请码已无剩余"异常（被 catch 忽略）。
        assertThat(success.get()).isEqualTo(2);
    }

    private InviteCodeVO createEnterpriseCode(int quota) {
        InviteCodeCreateDTO dto = new InviteCodeCreateDTO();
        dto.setQuota(quota);
        dto.setStatus(1);
        dto.setRemark("concurrency-test-" + UUID.randomUUID());
        InviteCodeVO vo = inviteService.createEnterprise(dto);
        createdCodeIds.add(vo.getId());
        return vo;
    }
}
