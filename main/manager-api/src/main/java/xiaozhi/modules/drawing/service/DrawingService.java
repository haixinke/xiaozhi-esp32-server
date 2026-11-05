package xiaozhi.modules.drawing.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.drawing.dto.DrawingStartDTO;
import xiaozhi.modules.drawing.entity.DrawingSessionEntity;
import xiaozhi.modules.drawing.vo.DrawingCompleteVO;
import xiaozhi.modules.drawing.vo.DrawingConfigVO;
import xiaozhi.modules.drawing.vo.DrawingStartVO;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GamePlayLimitService;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;
import xiaozhi.modules.star.enums.DailyRewardSourceType;
import xiaozhi.modules.star.enums.StarConsumeBizType;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.service.StarService;

/** 一起画业务边界：用户级开局限次、宠物资格验证及完成时配置结算。 */
@Service
public class DrawingService {
    private static final String GAME_TYPE = "DRAW_TOGETHER";
    private final DrawingSessionDao sessions;
    private final GameDao games;
    private final PetDao pets;
    private final StarDailyRewardDao rewards;
    private final StarService stars;
    private final StarDailyRewardService dailyRewards;
    private final GamePlayLimitService playLimits;
    private final Clock clock;

    public DrawingService(DrawingSessionDao sessions, GameDao games, PetDao pets,
            StarDailyRewardDao rewards, StarService stars, StarDailyRewardService dailyRewards,
            GamePlayLimitService playLimits,
            @Qualifier("dailyNoteClock") Clock clock) {
        this.sessions = sessions; this.games = games; this.pets = pets;
        this.rewards = rewards; this.stars = stars; this.dailyRewards = dailyRewards;
        this.playLimits = playLimits;
        this.clock = clock.withZone(ZoneId.of("Asia/Shanghai"));
    }

    /** 开局前查询当前配置和剩余次数，星星金额以完成时配置为准。 */
    public DrawingConfigVO config(Long userId, String petId) {
        requirePet(userId, petId);
        GameEntity game = requireGame();
        int limit = value(game.getDailyLimit());
        long remaining = limit == 0 ? -1 : Math.max(0, limit - sessions.countDay(userId, LocalDate.now(clock)));
        return new DrawingConfigVO(value(game.getEarnStars()), value(game.getConsumeStars()), limit,
                remaining, value(game.getEarnStars()) > 0 && !rewardedToday(userId));
    }

    /** 先锁用户再查幂等与额度，创建局次不触碰钱包；重试只按用户和请求标识回放。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public DrawingStartVO start(Long userId, DrawingStartDTO dto) {
        if (dto == null || dto.getRequestId() == null || !dto.getRequestId().matches("[A-Za-z0-9_-]{8,64}")) {
            throw new RenException(ErrorCode.DRAWING_REQUEST_INVALID);
        }
        lockUser(userId);
        DrawingSessionEntity existing = sessions.findRequest(userId, dto.getRequestId());
        if (existing != null) return startView(existing);
        requirePet(userId, dto.getPetId());
        requireGame();
        playLimits.checkDailyLimit(userId, GAME_TYPE);
        LocalDate day = LocalDate.now(clock);
        DrawingSessionEntity session = new DrawingSessionEntity();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId); session.setRequestId(dto.getRequestId());
        session.setReferenceId(DrawingReferences.pick());
        session.setStartedDay(day); session.setStartedAt(Date.from(clock.instant()));
        sessions.insert(session);
        return startView(session);
    }

    /** 完成与奖励同事务提交；先回放已完成局次，避免跨日重试再次占用当天奖励资格。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public DrawingCompleteVO complete(Long userId, String sessionId) {
        lockUser(userId);
        DrawingSessionEntity session = sessions.findOwned(userId, sessionId);
        if (session == null) throw new RenException(ErrorCode.DRAWING_SESSION_NOT_FOUND);
        if (session.getCompletedAt() != null) return completeView(session, replayAward(session));
        GameEntity game = requireGame();
        long awarded = 0;
        String rewardStatus = "NO_REWARD";
        java.time.Instant completedAt = clock.instant();
        // 费用只在完成时扣除；沿用旧业务单号，兼容升级前已扣费的未完成局次。
        if (value(game.getConsumeStars()) > 0) {
            stars.consume(userId, StarConsumeBizType.GAME_PLAY, "drawing-" + session.getId(),
                    value(game.getConsumeStars()), "一起画完成结算");
            completedAt = clock.instant();
        }
        // 完成时才读取运营配置，不再沿用开局金额。
        if (value(game.getEarnStars()) > 0) {
            var result = dailyRewards.claimWithDecision(userId, DailyRewardSourceType.GAME, GAME_TYPE,
                    (uid, type, key, day) -> StarDailyRewardService.ClaimDecision.award(value(game.getEarnStars())));
            completedAt = Objects.requireNonNull(result.getSettledAt(), "每日奖励结算时刻不能为空");
            if (result.isAwarded()) { awarded = result.getAmount(); rewardStatus = "NEW_REWARD"; }
            else rewardStatus = "ALREADY_CLAIMED";
        }
        session.setCompletedAt(Date.from(completedAt));
        session.setRewardStatus(rewardStatus);
        sessions.updateById(session);
        return completeView(session, awarded);
    }

    /** 未领取、孵化中、已删除或他人宠物均不构成游玩资格。 */
    private void requirePet(Long userId, String petId) {
        PetEntity pet = petId == null || petId.isBlank() ? null : pets.selectById(petId);
        if (userId == null || pet == null || pet.isDeleted() || !Objects.equals(userId, pet.getUserId())
                || !"HATCHED".equals(pet.getHatchStatus())) throw new RenException(ErrorCode.DRAWING_PET_REQUIRED);
    }

    private void lockUser(Long userId) {
        if (userId == null || sessions.lockUser(userId) == null) throw new RenException(ErrorCode.DRAWING_REQUEST_INVALID);
    }

    /** 新开局与首次完成均须当前游戏有效；已完成重试不再读取配置。 */
    private GameEntity requireGame() {
        GameEntity game = games.selectByGameType(GAME_TYPE);
        if (game == null) throw new RenException(ErrorCode.GAME_NOT_FOUND);
        if (!Objects.equals(game.getStatus(), 1)) throw new RenException(ErrorCode.GAME_DISABLED);
        int earn = value(game.getEarnStars()), consume = value(game.getConsumeStars());
        if (earn < 0 || consume < 0 || value(game.getDailyLimit()) < 0 || (earn > 0 && consume > 0)) {
            throw new RenException(ErrorCode.GAME_STAR_CONFIG_CONFLICT);
        }
        return game;
    }

    private boolean rewardedToday(Long userId) {
        return rewards.selectCount(new QueryWrapper<StarDailyRewardEntity>().eq("user_id", userId)
                .eq("source_type", DailyRewardSourceType.GAME.name()).eq("source_key", GAME_TYPE)
                .eq("reward_date", java.sql.Date.valueOf(LocalDate.now(clock)))) > 0;
    }

    private DrawingStartVO startView(DrawingSessionEntity session) {
        return new DrawingStartVO(session.getId(), session.getReferenceId());
    }

    /** 金额从完成日奖励事实读取，避免跨日重试误取今天的奖励或当前价格。 */
    private long replayAward(DrawingSessionEntity session) {
        if (!"NEW_REWARD".equals(session.getRewardStatus())) return 0;
        LocalDate day = session.getCompletedAt().toInstant().atZone(clock.getZone()).toLocalDate();
        var reward = rewards.selectOne(new QueryWrapper<StarDailyRewardEntity>()
                .eq("user_id", session.getUserId()).eq("source_type", DailyRewardSourceType.GAME.name())
                .eq("source_key", GAME_TYPE).eq("reward_date", java.sql.Date.valueOf(day)));
        if (reward == null) throw new IllegalStateException("已完成局次缺少奖励记录");
        return reward.getAmount();
    }

    private DrawingCompleteVO completeView(DrawingSessionEntity session, long awarded) {
        return new DrawingCompleteVO(session.getId(), awarded, session.getRewardStatus(),
                stars.balance(session.getUserId()), session.getCompletedAt());
    }

    private int value(Integer number) { return number == null ? 0 : number; }
}
