package com.example.viewit.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.example.viewit.R;
import com.example.viewit.data.model.Rating;

// 평균 별점을 0.5점 단위 별 다섯 개로 보여 주는 표시 전용 뷰 (누를 수 없음).
// 예: setRating(3.6) → ★★★½☆ (3.5점). 1~5점 입력은 StarRatingInputView를 쓴다.
public class StarRatingView extends LinearLayout {

    private static final int STAR_SIZE_DP = 14;
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

    // 평균 별점(0~5)을 0.5 단위로 반올림해 별을 채운다
    public void setRating(double average) {
        double rounded = RatingFormat.roundToHalf(average);
        for (int i = 0; i < stars.length; i++) {
            int starNumber = i + 1;
            int icon;
            if (rounded >= starNumber) {
                icon = R.drawable.ic_star_filled;
            } else if (rounded >= starNumber - 0.5) {
                icon = R.drawable.ic_star_half;
            } else {
                icon = R.drawable.ic_star_empty;
            }
            stars[i].setImageResource(icon);
        }
        setContentDescription(getContext().getString(R.string.rating_average_description,
                RatingFormat.halfText(rounded)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
