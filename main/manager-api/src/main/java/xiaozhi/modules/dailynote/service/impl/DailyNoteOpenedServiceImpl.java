package xiaozhi.modules.dailynote.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.DailyNoteOpenedService;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.vo.DailyRewardResult;

/**
 * 用户已打开记录 Service 实现。
 * "当天"由可替换的 Clock（Asia/Shanghai）决定，与当日查询、每日奖励共用一个时钟 Bean。
 * 幂等策略：先查后插，(user_id, note_date) 联合唯一约束兜底；
 * 并发下的唯一键冲突说明已有记录，视为成功且不重查（并发写者刚写入，原记录和首次时间不变）。
 * 仅当当日小纸条存在时写入：内容覆盖/删除/同日重建均不触碰已打开记录（无 note_id、无级联）。
 *
 * 奖励编排：当天首次打开成功时同事务发放每日星星奖励（首次11，连续+1，封顶17，中断回11）；
 * 打开记录、奖励记录与钱包流水在同一事务提交，任一步失败整体回滚，重试可正常领取。
 * 历史打开记录不参与连续计算也不补发：旧记录命中提前返回分支，不会走到发奖逻辑。
 */
@Slf4j
@Service
public class DailyNoteOpenedServiceImpl implements DailyNoteOpenedService {

    /** 小纸条奖励来源标识（source_type/source_key 固定值，见 ai_star_daily_reward 表注释） */
    private static final String DAILY_NOTE_SOURCE = "DAILY_NOTE";

    private final DailyNoteOpenedDao dailyNoteOpenedDao;
    private final DailyNoteDao dailyNoteDao;
    private final StarDailyRewardService starDailyRewardService;

    /** 可替换时钟：生产注入 dailyNoteClock（Asia/Shanghai），测试替换为固定时钟验证日界 */
    private Clock clock;

    public DailyNoteOpenedServiceImpl(DailyNoteOpenedDao dailyNoteOpenedDao, DailyNoteDao dailyNoteDao,
            StarDailyRewardService starDailyRewardService, @Qualifier("dailyNoteClock") Clock clock) {
        this.dailyNoteOpenedDao = dailyNoteOpenedDao;
        this.dailyNoteDao = dailyNoteDao;
        this.starDailyRewardService = starDailyRewardService;
        this.clock = clock;
    }

    /** 仅供测试替换时钟（包外通过实现类访问） */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    // 与每日奖励共用 READ_COMMITTED，账户锁等待后读取最新领取记录，且不引入缺失行间隙锁。
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public DailyRewardResult markOpened(Long userId) {
        LocalDate today = LocalDate.now(clock);
        java.sql.Date todayDate = java.sql.Date.valueOf(today);

        // 已打开记录存在：幂等成功，首次打开时间不变。
        // 该分支同时承载历史兼容：上线前已打开的用户当天不补发奖励，从次日首次有效打开开始领取。
        // 返回 amount=0 而非历史金额快照：多查一次当日奖励记录只为展示，业务上无消费者
        DailyNoteOpenedEntity existing = dailyNoteOpenedDao.selectOne(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", userId)
                .eq("note_date", todayDate));
        if (existing != null) {
            return notAwarded(today);
        }

        // 当日小纸条不存在时不写记录不发奖：提前标记不得隐藏后续补录的当日内容；
        // 有内容即可标记并发奖，不要求游戏可用（游戏可用性只在查询侧起作用）
        Long noteCount = dailyNoteDao.selectCount(
                new QueryWrapper<DailyNoteEntity>().eq("note_date", todayDate));
        if (noteCount == 0) {
            return notAwarded(today);
        }

        DailyNoteOpenedEntity entity = new DailyNoteOpenedEntity();
        entity.setUserId(userId);
        entity.setNoteDate(todayDate);
        entity.setOpenedAt(new Date());
        try {
            dailyNoteOpenedDao.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发重复上报撞联合唯一键：已有记录即视为成功，原记录与首次时间不变；
            // 发奖由并发胜出方完成，本事务不发奖（胜出方未提交时每日奖励唯一约束兜底）
            return notAwarded(today);
        }

        // 同事务发奖：奖励记录与钱包流水随本事务原子提交，失败整体回滚不留半成功状态
        return starDailyRewardService.claimDailyReward(userId, DAILY_NOTE_SOURCE, DAILY_NOTE_SOURCE);
    }

    /** 非发奖分支的统一结果：未入账、未跳过、金额为 0，仅奖励日期标识当天 */
    private static DailyRewardResult notAwarded(LocalDate today) {
        DailyRewardResult result = new DailyRewardResult();
        result.setAwarded(false);
        result.setAmount(0);
        result.setRewardDate(today);
        return result;
    }
}
