package com.example.viewit.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.example.viewit.R;
import com.example.viewit.data.model.Rating;

import java.util.Locale;

// 평균 별점을 0.1점 단위로 별 다섯 개에 채워 보여 주는 표시 전용 뷰 (누를 수 없음).
// 예: setRating(3.6) → 별 3개는 꽉 차고 네 번째 별은 왼쪽 60%만 채움. 1~5점 입력은 StarRatingInputView를 쓴다.
public class StarRatingView extends LinearLayout {

    private static final int STAR_SIZE_DP = 14;
    // ImageView.setImageLevel의 최댓값 = 별 하나를 꽉 채움
    private static final int FULL_LEVEL = 10000;
    private static final int STAR_GAP_DP = 1;

    private final ImageView[] stars = new ImageView[Rating.MAX_SCORE];

    public StarRatingView(Context context) {
        this(context, null);
    }

    public StarRatingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        int size = dp(STAR_SIZE_DP);
        int gap = dp(STAR_GAP_DP);
        for (int i = 0; i < stars.length; i++) {
            ImageView star = new ImageView(context);
            star.setImageResource(R.drawable.ic_star_partial);
            star.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams params = new LayoutParams(size, size);
            if (i > 0) {
                params.setMarginStart(gap);
            }
            addView(star, params);
            stars[i] = star;
        }
        setRating(0);
    }

    // 평균 별점(0~5)을 0.1 단위로 반올림해 별을 채운다. 예: 3.6 → 1, 1, 1, 0.6, 0
    public void setRating(double average) {
        double rounded = RatingFormat.roundToTenth(average);
        for (int i = 0; i < stars.length; i++) {
            double fill = Math.max(0, Math.min(1, rounded - i));
            stars[i].setImageLevel((int) Math.round(fill * FULL_LEVEL));
        }
        setContentDescription(getContext().getString(R.string.rating_average_description,
                String.format(Locale.US, "%.1f", rounded)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
