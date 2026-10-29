package xiaozhi.modules.dailynote.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.DailyNoteOpenedService;

/**
 * 用户已打开记录 Service 实现。
 * "当天"由可替换的 Clock（Asia/Shanghai）决定，与当日查询共用一个时钟 Bean。
 * 幂等策略：先查后插，(user_id, note_date) 联合唯一约束兜底；
 * 并发下的唯一键冲突说明已有记录，视为成功且不重查（并发写者刚写入，原记录和首次时间不变）。
 * 仅当当日小纸条存在时写入：内容覆盖/删除/同日重建均不触碰已打开记录（无 note_id、无级联）。
 */
@Service
public class DailyNoteOpenedServiceImpl implements DailyNoteOpenedService {

    private final DailyNoteOpenedDao dailyNoteOpenedDao;
    private final DailyNoteDao dailyNoteDao;

    /** 可替换时钟：生产注入 dailyNoteClock（Asia/Shanghai），测试替换为固定时钟验证日界 */
    private Clock clock;

    public DailyNoteOpenedServiceImpl(DailyNoteOpenedDao dailyNoteOpenedDao, DailyNoteDao dailyNoteDao,
            @Qualifier("dailyNoteClock") Clock clock) {
        this.dailyNoteOpenedDao = dailyNoteOpenedDao;
        this.dailyNoteDao = dailyNoteDao;
        this.clock = clock;
    }

    /** 仅供测试替换时钟（包外通过实现类访问） */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void markOpened(Long userId) {
        LocalDate today = LocalDate.now(clock);
        java.sql.Date todayDate = java.sql.Date.valueOf(today);

        // 已打开记录存在：幂等成功，首次打开时间不变
        DailyNoteOpenedEntity existing = dailyNoteOpenedDao.selectOne(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", userId)
                .eq("note_date", todayDate));
        if (existing != null) {
            return;
        }

        // 当日小纸条不存在时不写记录：提前标记不得隐藏后续补录的当日内容；
        // 有内容即可标记，不要求游戏可用（游戏可用性只在查询侧起作用）
        Long noteCount = dailyNoteDao.selectCount(
                new QueryWrapper<DailyNoteEntity>().eq("note_date", todayDate));
        if (noteCount == 0) {
            return;
        }

        DailyNoteOpenedEntity entity = new DailyNoteOpenedEntity();
        entity.setUserId(userId);
        entity.setNoteDate(todayDate);
        entity.setOpenedAt(new Date());
        try {
            dailyNoteOpenedDao.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发重复上报撞联合唯一键：已有记录即视为成功，原记录与首次时间不变
        }
    }
}
