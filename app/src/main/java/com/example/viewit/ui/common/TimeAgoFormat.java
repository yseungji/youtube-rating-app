package com.example.viewit.ui.common;

import android.content.Context;

import com.example.viewit.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// 작성 시각을 "방금 전 / 5분 전 / 3시간 전 / 2일 전 / 2026.11.06"처럼 보여준다 (UI4/Author)
public final class TimeAgoFormat {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private TimeAgoFormat() {
    }

    public static String format(Context context, Date time, Date now) {
        long diff = Math.max(0, now.getTime() - time.getTime());
        if (diff < MINUTE) {
            return context.getString(R.string.time_just_now);
        } else if (diff < HOUR) {
            return context.getString(R.string.time_minutes_ago, (int) (diff / MINUTE));
        } else if (diff < DAY) {
            return context.getString(R.string.time_hours_ago, (int) (diff / HOUR));
        } else if (diff < 7 * DAY) {
            return context.getString(R.string.time_days_ago, (int) (diff / DAY));
        }
        return new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(time);
    }
}
