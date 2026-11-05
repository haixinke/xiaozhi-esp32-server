package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.GameStarRewardService;
import xiaozhi.modules.game.service.impl.GamePlayLimitServiceImpl;
import xiaozhi.modules.game.vo.GameStarRewardResult;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.dao.ListenAnswerDao;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.service.impl.ListenAnswerServiceImpl;

/** 经抽题和提交公共接口验证听你说限额，配置与持久化边界使用桩。 */
class ListenPlayLimitTest {
    private final GameDao games = mock(GameDao.class);
    private final ListenAnswerDao answers = mock(ListenAnswerDao.class);

    @BeforeEach
    void messages() { MessageSourceMockSupport.install(); }

    @Test
    void drawUsesGameManagementLimit() {
        var game = new GameEntity();
        game.setDailyLimit(1);
        when(games.selectByGameType("LISTEN_TO_YOU")).thenReturn(game);
        when(answers.countDay(eq(123L), any(), any())).thenReturn(1L);
        var service = new ListenAnswerServiceImpl(mock(ListenGroupDao.class), mock(ListenQuestionDao.class),
                answers, mock(GameStarRewardService.class), mock(ApplicationEventPublisher.class),
                new GamePlayLimitServiceImpl(games, mock(DrawingSessionDao.class), answers,
                        Clock.fixed(Instant.parse("2026-11-05T02:00:00Z"), ZoneId.of("Asia/Shanghai"))));

        assertThatThrownBy(() -> service.draw(123L)).isInstanceOf(RenException.class)
                .extracting("code").isEqualTo(ErrorCode.LISTEN_DAILY_LIMIT_REACHED);
    }

    @Test
    void retryOfAnsweredGroupIsNoOpEvenWhenQuestionWasRemoved() {
        when(answers.lockUser(123L)).thenReturn(123L);
        when(answers.selectCount(any())).thenReturn(1L);
        var reward = mock(GameStarRewardService.class);
        when(reward.claimGameReward(123L, "LISTEN_TO_YOU")).thenReturn(GameStarRewardResult.noReward());
        var service = new ListenAnswerServiceImpl(mock(ListenGroupDao.class), mock(ListenQuestionDao.class),
                answers, reward, mock(ApplicationEventPublisher.class),
                new GamePlayLimitServiceImpl(games, mock(DrawingSessionDao.class), answers, Clock.systemUTC()));
        var item = new ListenAnswerSubmitDTO.AnswerItem();
        item.setQuestionId(25L); item.setOptionKey("A");
        var request = new ListenAnswerSubmitDTO();
        request.setGroupId(9L); request.setAnswers(List.of(item));

        assertThat(service.submitAnswers(123L, request).getRewardStatus()).isEqualTo("NO_REWARD");
    }
}
