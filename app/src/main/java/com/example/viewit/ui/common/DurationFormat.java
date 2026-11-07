package com.example.viewit.ui.common;

import java.util.Locale;

// 영상 재생 시간을 썸네일 배지 글자로. 예: 750초 → "12:30", 3723초 → "1:02:03"
public final class DurationFormat {

    private DurationFormat() {
    }

    public static String format(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%d:%02d", minutes, secs);
    }
}
