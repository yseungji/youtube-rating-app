package com.example.viewit.ui.common;

import com.example.viewit.R;
import com.example.viewit.data.model.Rating;

import java.util.Locale;

// 별점을 화면 글자로 바꾸는 공통 함수. 예: stars(4) → "★★★★☆", averageText(4.0) → "4.0", roundToHalf(3.6) → 3.5
public final class RatingFormat {

    private static final String FILLED = "★";
    private static final String EMPTY = "☆";

    private RatingFormat() {
    }

    // 1~5점을 별 다섯 개 글자로. 범위를 벗어나면 0~5 사이로 맞춘다
    public static String stars(long score) {
        int filled = (int) Math.max(0, Math.min(Rating.MAX_SCORE, score));
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= Rating.MAX_SCORE; i++) {
            builder.append(i <= filled ? FILLED : EMPTY);
        }
        return builder.toString();
    }

    // 평균 별점을 0.5 단위로 반올림해 0~5 사이로. 예: 3.6 → 3.5, 3.75 → 4.0, 4.2 → 4.0
    // 별 모양으로 보여 줄 때는 StarRatingView.setRating(평균)을 쓴다
    public static double roundToHalf(double average) {
        double rounded = Math.round(average * 2) / 2.0;
        return Math.max(0, Math.min(Rating.MAX_SCORE, rounded));
    }

    // 0.5 단위 점수 글자. 예: 3.5 → "3.5", 4.0 → "4"
    public static String halfText(double halfScore) {
        if (halfScore == Math.floor(halfScore)) {
            return String.valueOf((long) halfScore);
        }
        return String.format(Locale.US, "%.1f", halfScore);
    }

    // 평균 별점 숫자. 평가가 없으면 "—"
    public static String averageText(double average, long count) {
        if (count <= 0) {
            return "—";
        }
        return String.format(Locale.US, "%.1f", average);
    }

    // 별점 문구 리소스 ID. 예: 4 → R.string.rating_label_4 ("좋아요"). 범위 밖이면 0
    public static int labelRes(long score) {
        switch ((int) score) {
            case 1:
                return R.string.rating_label_1;
            case 2:
                return R.string.rating_label_2;
            case 3:
                return R.string.rating_label_3;
            case 4:
                return R.string.rating_label_4;
            case 5:
                return R.string.rating_label_5;
            default:
                return 0;
        }
    }
}
