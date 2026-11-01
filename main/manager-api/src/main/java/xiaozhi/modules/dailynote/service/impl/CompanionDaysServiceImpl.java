package xiaozhi.modules.dailynote.service.impl;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.CompanionDaysService;

/**
 * 连续陪伴天数查询实现。
 * 现算不落库：取用户最近一段打开记录（降序），从锚定日往回逐日比对连续性。
 * 锚定规则：今天已打开锚今天，否则锚昨天；锚定日无记录即 0（断档在当天结束才生效）。
 * "当天"由可替换的 Clock（Asia/Shanghai）决定，与小纸条已打开、每日奖励共用一个时钟 Bean。
 */
@Service
public class CompanionDaysServiceImpl implements CompanionDaysService {

    /** 查询窗口上限（天）：streak 超 400 天会被静默截断，属可接受边界（产品实际 streak 远短于此） */
    private static final int LOOKBACK_DAYS = 400;

    private final DailyNoteOpenedDao dailyNoteOpenedDao;

    /** 可替换时钟：生产注入 dailyNoteClock（Asia/Shanghai），测试替换为固定时钟验证日界 */
    private Clock clock;

    public CompanionDaysServiceImpl(DailyNoteOpenedDao dailyNoteOpenedDao,
            @Qualifier("dailyNoteClock") Clock clock) {
        this.dailyNoteOpenedDao = dailyNoteOpenedDao;
        this.clock = clock;
    }

    /** 仅供测试替换时钟（包外通过实现类访问） */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public int companionDays(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Date windowStart = Date.valueOf(today.minusDays(LOOKBACK_DAYS));

        // 按 uk(user_id, note_date) 索引取窗口内打开日期，降序；单用户数据量小，无需分页
        List<LocalDate> dates = dailyNoteOpenedDao.selectList(new QueryWrapper<DailyNoteOpenedEntity>()
                .select("note_date")
                .eq("user_id", userId)
                .ge("note_date", windowStart)
                .orderByDesc("note_date"))
                .stream()
                .map(e -> {
                    // DATE 列在 OceanBase 驱动下可能映射为 java.util.Date 而非 java.sql.Date，统一转 LocalDate；
                    // 注意 java.sql.Date.toInstant() 会抛异常，必须先判 sql.Date 分支
                    java.util.Date d = e.getNoteDate();
                    return d instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                            : d.toInstant().atZone(clock.getZone()).toLocalDate();
                })
                .toList();
        if (dates.isEmpty()) {
            return 0;
        }

        // 锚定"截至昨天"语义：今天已打开锚今天，未打开锚昨天；锚定日无记录即 0
        LocalDate latest = dates.get(0);
        LocalDate anchor;
        if (latest.isEqual(today)) {
            anchor = today;
        } else if (latest.isEqual(today.minusDays(1))) {
            anchor = today.minusDays(1);
        } else {
            return 0;
        }

        int days = 1;
        LocalDate expected = anchor.minusDays(1);
        for (int i = 1; i < dates.size(); i++) {
            if (!dates.get(i).isEqual(expected)) {
                break;
            }
            days++;
            expected = expected.minusDays(1);
        }
        return days;
    }
}
