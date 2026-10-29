package xiaozhi.modules.dailynote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.DailyNoteOpenedService;
import xiaozhi.modules.dailynote.service.impl.DailyNoteOpenedServiceImpl;

/**
 * 用户已打开记录 Service 集成测试（真实 dev 库）。
 * 通过可替换的 Clock 固定"当天"为 2160 年的专用日期段，用户 ID 使用不可能存在的超大值，
 * 避免与真实用户数据冲突；每个用例自造数据，AfterEach 物理清理已打开记录与小纸条。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("用户已打开记录集成测试")
class DailyNoteOpenedServiceTest {

    @Autowired
    private DailyNoteOpenedService dailyNoteOpenedService;
    @Autowired
    private DailyNoteOpenedDao dailyNoteOpenedDao;
    @Autowired
    private DailyNoteDao dailyNoteDao;

    /** 本用例使用的专用用户 ID（远离真实用户区间），统一清理 */
    private static final long USER_A = 9_999_000_001L;
    private static final long USER_B = 9_999_000_002L;

    private final List<Long> openedIds = new ArrayList<>();
    private final List<Long> noteIds = new ArrayList<>();

    /** 把被测 Service 的可替换时钟拨到指定日期的正午（Asia/Shanghai），远离日界 */
    private void atShanghaiNoon(String date) {
        LocalDate day = LocalDate.parse(date);
        Instant noon = day.atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        ((DailyNoteOpenedServiceImpl) dailyNoteOpenedService)
                .setClock(Clock.fixed(noon, ZoneId.of("Asia/Shanghai")));
    }

    @BeforeEach
    void resetClock() {
        // 每个用例先拨到一个绝无数据的日期，防止用例间时钟状态泄漏
        atShanghaiNoon("2160-12-30");
    }

    @AfterEach
    void cleanup() {
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
        // 已打开记录按本用例专用用户清理（id 由雪花生成，无法预知，按用户+2160 年日期段删除）
        dailyNoteOpenedDao.delete(new QueryWrapper<DailyNoteOpenedEntity>()
                .in("user_id", USER_A, USER_B)
                .ge("note_date", java.sql.Date.valueOf("2160-01-01")));
        openedIds.clear();
    }

    /** 直接落库一份小纸条（聚焦已打开行为，不经过管理 Service 校验） */
    private void insertNote(String date, String title) {
        DailyNoteEntity entity = new DailyNoteEntity();
        entity.setNoteDate(java.sql.Date.valueOf(date));
        entity.setTitle(title);
        entity.setContent("正文-" + title);
        dailyNoteDao.insert(entity);
        noteIds.add(entity.getId());
    }

    /** 查询某用户某日的已打开记录（断言用） */
    private DailyNoteOpenedEntity findOpened(long userId, String date) {
        return dailyNoteOpenedDao.selectOne(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", userId)
                .eq("note_date", java.sql.Date.valueOf(date)));
    }

    @Test
    @DisplayName("首次标记：有当日小纸条时写入记录，包含用户、完整日期与首次打开时间")
    void firstMarkWritesRecord() {
        atShanghaiNoon("2160-01-10");
        insertNote("2160-01-10", "今日小纸条");

        dailyNoteOpenedService.markOpened(USER_A);

        DailyNoteOpenedEntity record = findOpened(USER_A, "2160-01-10");
        assertThat(record).isNotNull();
        assertThat(record.getUserId()).isEqualTo(USER_A);
        assertThat(record.getOpenedAt()).isNotNull();
        openedIds.add(record.getId());
    }

    @Test
    @DisplayName("重复标记幂等：仍成功，不产生重复记录，首次打开时间不变")
    void repeatMarkIsIdempotent() {
        atShanghaiNoon("2160-01-11");
        insertNote("2160-01-11", "标题");

        dailyNoteOpenedService.markOpened(USER_A);
        DailyNoteOpenedEntity first = findOpened(USER_A, "2160-01-11");
        openedIds.add(first.getId());

        dailyNoteOpenedService.markOpened(USER_A);
        dailyNoteOpenedService.markOpened(USER_A);

        List<DailyNoteOpenedEntity> all = dailyNoteOpenedDao.selectList(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", USER_A).eq("note_date", java.sql.Date.valueOf("2160-01-11")));
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getOpenedAt()).isEqualTo(first.getOpenedAt());
    }

    @Test
    @DisplayName("并发标记：唯一键冲突视为成功，最终只有一条记录且首次时间不变")
    void concurrentMarkTreatedAsSuccess() throws Exception {
        atShanghaiNoon("2160-01-12");
        insertNote("2160-01-12", "标题");

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    dailyNoteOpenedService.markOpened(USER_A);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 并发唯一键冲突视为成功：不允许任何线程失败
        assertThat(failures.get()).isZero();
        List<DailyNoteOpenedEntity> all = dailyNoteOpenedDao.selectList(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", USER_A).eq("note_date", java.sql.Date.valueOf("2160-01-12")));
        assertThat(all).hasSize(1);
        openedIds.add(all.get(0).getId());
    }

    @Test
    @DisplayName("当日无小纸条且用户无记录：标记成功但不写记录（提前标记不隐藏后续补录内容）")
    void noNoteNoRecordIsSuccessWithoutWrite() {
        atShanghaiNoon("2160-01-13");

        assertThatCode(() -> dailyNoteOpenedService.markOpened(USER_A)).doesNotThrowAnyException();

        assertThat(findOpened(USER_A, "2160-01-13")).isNull();
    }

    @Test
    @DisplayName("当日无小纸条但已有记录（内容被删期间）：仍幂等成功且不产生重复记录")
    void noNoteWithExistingRecordStaysIdempotent() {
        atShanghaiNoon("2160-01-14");
        insertNote("2160-01-14", "标题");
        dailyNoteOpenedService.markOpened(USER_A);
        DailyNoteOpenedEntity first = findOpened(USER_A, "2160-01-14");
        openedIds.add(first.getId());

        // 运营物理删除当日内容后，用户重复上报仍成功且不改变记录
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
        assertThatCode(() -> dailyNoteOpenedService.markOpened(USER_A)).doesNotThrowAnyException();

        List<DailyNoteOpenedEntity> all = dailyNoteOpenedDao.selectList(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", USER_A).eq("note_date", java.sql.Date.valueOf("2160-01-14")));
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getOpenedAt()).isEqualTo(first.getOpenedAt());
    }

    @Test
    @DisplayName("有当日小纸条但没有合格游戏时仍可标记已打开（打开行为不依赖游戏实时状态）")
    void markableWithoutEligibleGame() {
        atShanghaiNoon("2160-01-15");
        insertNote("2160-01-15", "标题");
        // 不创建任何游戏：标记仍应成功写入（游戏可用性只在查询侧起作用）

        dailyNoteOpenedService.markOpened(USER_A);

        DailyNoteOpenedEntity record = findOpened(USER_A, "2160-01-15");
        assertThat(record).isNotNull();
        openedIds.add(record.getId());
    }

    @Test
    @DisplayName("跨用户隔离：A 的已打开不影响 B；同日各自独立记录")
    void crossUserIsolation() {
        atShanghaiNoon("2160-01-16");
        insertNote("2160-01-16", "标题");

        dailyNoteOpenedService.markOpened(USER_A);

        assertThat(findOpened(USER_A, "2160-01-16")).isNotNull();
        assertThat(findOpened(USER_B, "2160-01-16")).isNull();

        dailyNoteOpenedService.markOpened(USER_B);
        assertThat(findOpened(USER_B, "2160-01-16")).isNotNull();
    }

    @Test
    @DisplayName("跨日隔离：昨天的已打开记录不影响今天，次日需重新打开")
    void crossDayIsolation() {
        atShanghaiNoon("2160-01-17");
        insertNote("2160-01-17", "昨天");
        dailyNoteOpenedService.markOpened(USER_A);

        // 拨到次日：昨天的记录对今天不生效
        atShanghaiNoon("2160-01-18");
        insertNote("2160-01-18", "今天");
        assertThat(findOpened(USER_A, "2160-01-18")).isNull();

        dailyNoteOpenedService.markOpened(USER_A);
        assertThat(findOpened(USER_A, "2160-01-18")).isNotNull();
        // 两天的记录相互独立
        assertThat(findOpened(USER_A, "2160-01-17")).isNotNull();
    }

    @Test
    @DisplayName("内容覆盖不重置已打开：更新小纸条内容后记录与首次时间不变")
    void contentOverwriteDoesNotResetOpened() {
        atShanghaiNoon("2160-01-19");
        insertNote("2160-01-19", "旧标题");
        dailyNoteOpenedService.markOpened(USER_A);
        DailyNoteOpenedEntity first = findOpened(USER_A, "2160-01-19");
        openedIds.add(first.getId());

        // 模拟导入整项覆盖（与导入 Service 相同方式更新内容字段，保留记录身份）
        DailyNoteEntity note = dailyNoteDao.selectById(noteIds.get(0));
        dailyNoteDao.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DailyNoteEntity>()
                .eq("id", note.getId())
                .set("title", "新标题")
                .set("content", "新正文")
                .set("solar_term", null));

        DailyNoteOpenedEntity after = findOpened(USER_A, "2160-01-19");
        assertThat(after).isNotNull();
        assertThat(after.getId()).isEqualTo(first.getId());
        assertThat(after.getOpenedAt()).isEqualTo(first.getOpenedAt());
    }

    @Test
    @DisplayName("同日删除后重建不重置已打开：原用户记录保持，新记录身份也不影响")
    void sameDayRecreateDoesNotResetOpened() {
        atShanghaiNoon("2160-01-20");
        insertNote("2160-01-20", "旧内容");
        dailyNoteOpenedService.markOpened(USER_A);
        DailyNoteOpenedEntity first = findOpened(USER_A, "2160-01-20");
        openedIds.add(first.getId());

        // 物理删除后同日重建（新 id），已打开记录不得被清除或重置
        noteIds.forEach(dailyNoteDao::deleteById);
        noteIds.clear();
        insertNote("2160-01-20", "重建内容");

        DailyNoteOpenedEntity after = findOpened(USER_A, "2160-01-20");
        assertThat(after).isNotNull();
        assertThat(after.getId()).isEqualTo(first.getId());
        assertThat(after.getOpenedAt()).isEqualTo(first.getOpenedAt());
    }
}
