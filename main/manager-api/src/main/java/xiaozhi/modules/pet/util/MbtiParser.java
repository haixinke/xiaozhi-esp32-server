package xiaozhi.modules.pet.util;

import java.util.Set;

public final class MbtiParser {

    private static final Set<String> VALID_MBTI = Set.of(
            "ISTJ", "ISFJ", "INFJ", "INTJ",
            "ISTP", "ISFP", "INFP", "INTP",
            "ESTP", "ESFP", "ENFP", "ENTP",
            "ESTJ", "ESFJ", "ENFJ", "ENTJ"
    );

    private static final String DEFAULT_MBTI = "INFP";

    private MbtiParser() {
    }

    public static String parse(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return DEFAULT_MBTI;
        }
        String upper = llmResponse.toUpperCase().trim();
        for (String mbti : VALID_MBTI) {
            if (upper.contains(mbti)) {
                return mbti;
            }
        }
        return DEFAULT_MBTI;
    }
}
