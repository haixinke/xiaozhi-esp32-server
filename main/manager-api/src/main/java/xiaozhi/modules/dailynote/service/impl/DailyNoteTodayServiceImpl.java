package xiaozhi.modules.dailynote.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dao.DailyNoteOpenedDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;
import xiaozhi.modules.dailynote.service.DailyNoteTodayService;
import xiaozhi.modules.dailynote.vo.DailyNoteTodayVO;
import xiaozhi.modules.game.enums.GameEntranceEnum;
import xiaozhi.modules.game.service.GameService;
import xiaozhi.modules.game.vo.GameVO;

/**
 * 当日小纸条查询 Service 实现。
 * "当天"由可替换的 Clock（Asia/Shanghai 时区）决定，测试可注入固定时钟验证日界，
 * 不依赖服务器默认时区或真实午夜。
 * 游戏每次查询实时调用游戏服务的启用状态 + NOTE 入口过滤（listEnabled），
 * 不做缓存，因此停用、删除或移除入口的游戏立即不再参与；随机选择也不持久化。
 */
@Service
public class DailyNoteTodayServiceImpl implements DailyNoteTodayService {

    private final DailyNoteDao dailyNoteDao;
    private final DailyNoteOpenedDao dailyNoteOpenedDao;
    private final GameService gameService;

    /**
     * 可替换时钟：生产环境由配置注入 Asia/Shanghai 系统时钟（dailyNoteClock Bean）；
     * 测试通过 setter 替换为固定时钟以验证日界行为
     */
    private Clock clock;

    public DailyNoteTodayServiceImpl(DailyNoteDao dailyNoteDao, DailyNoteOpenedDao dailyNoteOpenedDao,
            GameService gameService,
            @Qualifier("dailyNoteClock") Clock clock) {
        this.dailyNoteDao = dailyNoteDao;
        this.dailyNoteOpenedDao = dailyNoteOpenedDao;
        this.gameService = gameService;
        this.clock = clock;
    }

    /** 仅供测试替换时钟（包外通过实现类访问） */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public DailyNoteTodayVO today(Long userId) {
        // "当天"由服务器时钟决定；已打开状态按用户+完整日期隔离
        LocalDate today = LocalDate.now(clock);

        DailyNoteEntity note = dailyNoteDao.selectOne(
                new QueryWrapper<DailyNoteEntity>().eq("note_date", java.sql.Date.valueOf(today)));
        // 当日无小纸条：成功空响应，不返回部分内容
        if (note == null) {
            return null;
        }

        // 已打开检查先于游戏选择：用户已打开当日内容时直接返回空，
        // 不再随机选游戏（否则白白消耗一次随机且语义上"返回了部分状态"）
        Long openedCount = dailyNoteOpenedDao.selectCount(new QueryWrapper<DailyNoteOpenedEntity>()
                .eq("user_id", userId)
                .eq("note_date", java.sql.Date.valueOf(today)));
        if (openedCount > 0) {
            return null;
        }

        // 复用游戏服务的启用状态与 NOTE 入口过滤能力，不复制游戏配置
        List<GameVO> games = gameService.listEnabled(GameEntranceEnum.NOTE.name());
        // 无合格游戏：同样成功空响应，不下发缺游戏的不完整内容
        if (games.isEmpty()) {
            return null;
        }

        DailyNoteTodayVO vo = new DailyNoteTodayVO();
        vo.setDate(today.format(DateTimeFormatter.ISO_LOCAL_DATE));
        vo.setTitle(note.getTitle());
        vo.setContent(note.getContent());
        vo.setSolarTerm(note.getSolarTerm());
        // 每次查询重新随机：不保存用户与游戏的对应关系
        vo.setGame(games.get(ThreadLocalRandom.current().nextInt(games.size())));
        return vo;
    }
}
