package xiaozhi.modules.storyengine.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import xiaozhi.modules.storyengine.constant.StoryImageTimeOfDay;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;
import xiaozhi.modules.storyengine.model.StoryPeriodContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StoryPeriodResolverTest {

    @ParameterizedTest
    @CsvSource({
            "00:00,NIGHT,NIGHT", "06:59,NIGHT,NIGHT",
            "07:00,MORNING,DAY", "11:59,MORNING,DAY",
            "12:00,AFTERNOON,DAY", "16:59,AFTERNOON,DAY",
            "17:00,EVENING,SUNSET", "18:59,EVENING,SUNSET",
            "19:00,NIGHT,NIGHT", "23:59,NIGHT,NIGHT"
    })
    void resolvesWeightAndImagePeriods(String time, StoryWeightPeriod weight, StoryImageTimeOfDay image) {
        ZonedDateTime value = ZonedDateTime.of(LocalDate.parse("2026-08-08"),
                LocalTime.parse(time), ZoneId.of("Asia/Shanghai"));

        assertThat(new StoryPeriodResolver().resolve(value))
                .isEqualTo(new StoryPeriodContext(weight, image));
    }
}
