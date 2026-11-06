package com.example.viewit.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.viewit.R;
import com.example.viewit.data.model.Rating;

// 별 다섯 개를 눌러 1~5점을 고르는 입력 (Figma UI4/RatingPanel의 별 줄). 고른 별까지 코랄, 나머지는 회색.
public class StarRatingInputView extends LinearLayout {

    // 별점이 바뀔 때 알림
    public interface OnScoreChangeListener {
        void onScoreChanged(int score);
    }

    private static final int STAR_TOUCH_DP = 40;   // 손가락으로 누르는 영역
    private static final int STAR_PADDING_DP = 6;  // 별 크기 = 40 - 6×2 = 28dp

    private final ImageView[] stars = new ImageView[Rating.MAX_SCORE];
    private int score;                             // 0이면 아직 고르지 않음
    @Nullable
    private OnScoreChangeListener listener;

    public StarRatingInputView(Context context) {
        this(context, null);
    }

    public StarRatingInputView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int size = dp(STAR_TOUCH_DP);
        int padding = dp(STAR_PADDING_DP);
        for (int i = 0; i < stars.length; i++) {
            int starScore = i + 1;
            ImageView star = new ImageView(context);
            star.setImageResource(R.drawable.ic_star);
            star.setPadding(padding, padding, padding, padding);
            star.setContentDescription(context.getString(R.string.rating_star_description, starScore));
            star.setOnClickListener(v -> setScoreFromUser(starScore));
            addView(star, new LayoutParams(size, size));
            stars[i] = star;
        }
        updateStars();
    }

    public int getScore() {
        return score;
    }

    // 코드에서 별점을 정할 때 (저장된 평가 불러오기 등). 리스너는 부르지 않는다
    public void setScore(int score) {
        this.score = Math.max(0, Math.min(Rating.MAX_SCORE, score));
        updateStars();
    }

    public void setOnScoreChangeListener(@Nullable OnScoreChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (ImageView star : stars) {
            star.setEnabled(enabled);
        }
    }

    private void setScoreFromUser(int newScore) {
        setScore(newScore);
        if (listener != null) {
            listener.onScoreChanged(score);
        }
    }

    private void updateStars() {
        ColorStateList on = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.positive));
        ColorStateList off = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.border_default));
        for (int i = 0; i < stars.length; i++) {
            ImageViewCompat.setImageTintList(stars[i], i < score ? on : off);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
