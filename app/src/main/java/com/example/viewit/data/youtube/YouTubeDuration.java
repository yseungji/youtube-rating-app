package com.example.viewit.data.youtube;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// YouTube가 주는 재생 시간(ISO 8601)을 초로 바꾼다. 예: "PT12M30S" → 750, "PT1H2M3S" → 3723, "P1DT2H" → 93600
// 실시간 방송처럼 값이 "P0D"이거나 형식이 다르면 0을 돌려준다.
public final class YouTubeDuration {

    private static final Pattern PATTERN = Pattern.compile(
            "P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?");

    private YouTubeDuration() {
    }

    public static long toSeconds(String isoDuration) {
        if (isoDuration == null) {
            return 0;
        }
        Matcher m = PATTERN.matcher(isoDuration);
        if (!m.matches()) {
            return 0;
        }
        return number(m.group(1)) * 86400
                + number(m.group(2)) * 3600
                + number(m.group(3)) * 60
                + number(m.group(4));
    }

    private static long number(String group) {
        return group == null ? 0 : Long.parseLong(group);
    }
}
