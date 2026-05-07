package xiaozhi.modules.pet.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlf.calendar.EightChar;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PetBirthCalculator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<Character, String> GANG_WUXING = Map.ofEntries(
            Map.entry('甲', "wood"), Map.entry('乙', "wood"),
            Map.entry('丙', "fire"), Map.entry('丁', "fire"),
            Map.entry('戊', "earth"), Map.entry('己', "earth"),
            Map.entry('庚', "metal"), Map.entry('辛', "metal"),
            Map.entry('壬', "water"), Map.entry('癸', "water")
    );

    private static final Map<Character, String> ZHI_WUXING = Map.ofEntries(
            Map.entry('子', "water"), Map.entry('丑', "earth"),
            Map.entry('寅', "wood"), Map.entry('卯', "wood"),
            Map.entry('辰', "earth"), Map.entry('巳', "fire"),
            Map.entry('午', "fire"), Map.entry('未', "earth"),
            Map.entry('申', "metal"), Map.entry('酉', "metal"),
            Map.entry('戌', "earth"), Map.entry('亥', "water")
    );

    private static final Map<String, String> ZODIAC_CODES = Map.ofEntries(
            Map.entry("白羊", "aries"), Map.entry("白羊座", "aries"),
            Map.entry("金牛", "taurus"), Map.entry("金牛座", "taurus"),
            Map.entry("双子", "gemini"), Map.entry("双子座", "gemini"),
            Map.entry("巨蟹", "cancer"), Map.entry("巨蟹座", "cancer"),
            Map.entry("狮子", "leo"), Map.entry("狮子座", "leo"),
            Map.entry("处女", "virgo"), Map.entry("处女座", "virgo"),
            Map.entry("天秤", "libra"), Map.entry("天秤座", "libra"),
            Map.entry("天蝎", "scorpio"), Map.entry("天蝎座", "scorpio"),
            Map.entry("射手", "sagittarius"), Map.entry("射手座", "sagittarius"),
            Map.entry("摩羯", "capricorn"), Map.entry("摩羯座", "capricorn"),
            Map.entry("水瓶", "aquarius"), Map.entry("水瓶座", "aquarius"),
            Map.entry("双鱼", "pisces"), Map.entry("双鱼座", "pisces")
    );

    private PetBirthCalculator() {
    }

    public static BirthResult calculate(LocalDateTime birthTime) {
        Solar solar = Solar.fromYmdHms(
                birthTime.getYear(), birthTime.getMonthValue(), birthTime.getDayOfMonth(),
                birthTime.getHour(), birthTime.getMinute(), birthTime.getSecond()
        );
        Lunar lunar = solar.getLunar();
        EightChar eightChar = lunar.getEightChar();

        Map<String, String> bazi = new LinkedHashMap<>();
        bazi.put("year", eightChar.getYear());
        bazi.put("month", eightChar.getMonth());
        bazi.put("day", eightChar.getDay());
        bazi.put("hour", eightChar.getTime());

        Map<String, Integer> wuxingCount = new LinkedHashMap<>();
        wuxingCount.put("metal", 0);
        wuxingCount.put("wood", 0);
        wuxingCount.put("water", 0);
        wuxingCount.put("fire", 0);
        wuxingCount.put("earth", 0);

        String[] pillars = {eightChar.getYear(), eightChar.getMonth(), eightChar.getDay(), eightChar.getTime()};
        for (String pillar : pillars) {
            countWuxing(pillar.charAt(0), wuxingCount);
            countWuxing(pillar.charAt(1), wuxingCount);
        }

        String zodiacName = solar.getXingZuo();
        String zodiacCode = ZODIAC_CODES.getOrDefault(zodiacName, zodiacName);

        try {
            return new BirthResult(
                    MAPPER.writeValueAsString(bazi),
                    MAPPER.writeValueAsString(wuxingCount),
                    zodiacCode
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化八字/五行数据失败", e);
        }
    }

    private static void countWuxing(char c, Map<String, Integer> wuxingCount) {
        String element = GANG_WUXING.getOrDefault(c, ZHI_WUXING.get(c));
        if (element != null) {
            wuxingCount.merge(element, 1, Integer::sum);
        }
    }

    public record BirthResult(String bazi, String wuxing, String zodiac) {
    }
}
