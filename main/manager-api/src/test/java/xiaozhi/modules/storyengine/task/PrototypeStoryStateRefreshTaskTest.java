package xiaozhi.modules.storyengine.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import xiaozhi.modules.storyengine.config.StoryRuntimeConfig;
import xiaozhi.modules.storyengine.model.StoryEvaluationResult;
import xiaozhi.modules.storyengine.service.PrototypeStoryStateService;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PrototypeStoryStateRefreshTaskTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant EVALUATED_INSTANT = Instant.parse("2026-08-08T02:00:00Z");
    private static final ZonedDateTime EVALUATED_AT =
            ZonedDateTime.parse("2026-08-08T10:00:00+08:00[Asia/Shanghai]");

    private PrototypeStoryStateService service;

    @BeforeEach
    void setUp() {
        service = mock(PrototypeStoryStateService.class);
    }

    @Test
    void refreshesExactlyTwoSupportedPrototypesWithOneClockInstant() {
        Clock clock = runtimeClock();
        when(service.evaluate(eq("锦鲤"), any(ZonedDateTime.class)))
                .thenReturn(StoryEvaluationResult.KEPT_NOT_DUE);
        when(service.evaluate(eq("玉兔"), any(ZonedDateTime.class)))
                .thenReturn(StoryEvaluationResult.KEPT_REMAINDER);

        new PrototypeStoryStateRefreshTask(service, clock).refreshStates();

        ArgumentCaptor<ZonedDateTime> timestamps = ArgumentCaptor.forClass(ZonedDateTime.class);
        InOrder calls = inOrder(service);
        calls.verify(service).evaluate(eq("锦鲤"), timestamps.capture());
        calls.verify(service).evaluate(eq("玉兔"), timestamps.capture());
        List<ZonedDateTime> evaluatedAt = timestamps.getAllValues();
        assertThat(evaluatedAt).hasSize(2);
        assertThat(evaluatedAt.get(0)).isEqualTo(EVALUATED_AT);
        assertThat(evaluatedAt.get(1)).isSameAs(evaluatedAt.get(0));
        verify(clock).instant();
        verify(clock).getZone();
        verifyNoMoreInteractions(clock, service);
    }

    @Test
    void koiFailureDoesNotBlockRabbit() {
        Clock clock = runtimeClock();
        doThrow(new IllegalStateException("boom"))
                .when(service).evaluate(eq("锦鲤"), any(ZonedDateTime.class));
        when(service.evaluate(eq("玉兔"), any(ZonedDateTime.class)))
                .thenReturn(StoryEvaluationResult.KEPT_NOT_DUE);

        new PrototypeStoryStateRefreshTask(service, clock).refreshStates();

        verify(service).evaluate("锦鲤", EVALUATED_AT);
        verify(service).evaluate("玉兔", EVALUATED_AT);
        verifyNoMoreInteractions(service);
    }

    @Test
    void invalidConfigurationDoesNotBlockRemainingPrototypes() {
        Clock clock = runtimeClock();
        when(service.evaluate(eq("锦鲤"), any(ZonedDateTime.class)))
                .thenReturn(StoryEvaluationResult.KEPT_INVALID_CONFIGURATION);
        when(service.evaluate(eq("玉兔"), any(ZonedDateTime.class)))
                .thenReturn(StoryEvaluationResult.KEPT_NOT_DUE);

        new PrototypeStoryStateRefreshTask(service, clock).refreshStates();

        verify(service).evaluate("锦鲤", EVALUATED_AT);
        verify(service).evaluate("玉兔", EVALUATED_AT);
        verifyNoMoreInteractions(service);
    }

    @Test
    void exposesNamedShanghaiRuntimeClock() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(StoryRuntimeConfig.class)) {
            assertThat(context.getBean("storyRuntimeClock", Clock.class).getZone()).isEqualTo(SHANGHAI);
        }
    }

    @Test
    void refreshStatesUsesTheHourlyShanghaiSchedule() throws NoSuchMethodException {
        Method refreshStates = PrototypeStoryStateRefreshTask.class.getDeclaredMethod("refreshStates");

        Scheduled scheduled = refreshStates.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 * * * ?");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }

    private Clock runtimeClock() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(EVALUATED_INSTANT);
        when(clock.getZone()).thenReturn(SHANGHAI);
        return clock;
    }
}
