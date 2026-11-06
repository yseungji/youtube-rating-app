package com.example.viewit.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.example.viewit.R;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
import com.example.viewit.databinding.ViewRatingStatisticsBinding;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Map;

// 영상의 공개 평가 통계 (Figma UI4/RatingStatistics): 평균 점수 + 별 + 평가 수 + 5~1점 분포 막대.
// 막대 길이는 가장 많이 받은 점수를 100%로 맞춘다.
public class RatingStatisticsView extends LinearLayout {

    private final ViewRatingStatisticsBinding binding;
    private final LinearProgressIndicator[] bars = new LinearProgressIndicator[Rating.MAX_SCORE];

    public RatingStatisticsView(Context context) {
        this(context, null);
    }

    public RatingStatisticsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        binding = ViewRatingStatisticsBinding.inflate(LayoutInflater.from(context), this);
        buildBars(context);
        setStats(0, 0, null);
    }

    // videos 문서의 집계값으로 채운다
    public void setStats(Video video) {
        setStats(video.getRatingAverage(), video.getPublicRatingCount(), video.getRatingDistribution());
    }

    // distribution의 키는 "s1"~"s5" (Video.distributionKey)
    public void setStats(double average, long count, @Nullable Map<String, Long> distribution) {
        Context context = getContext();
        binding.statsAverage.setText(RatingFormat.averageText(average, count));
        binding.statsStars.setText(RatingFormat.stars(count > 0 ? average : 0));
        String countText = context.getString(R.string.rating_stats_count, (int) count);
        binding.statsCount.setText(countText);
        binding.statsCountHeader.setText(countText);

        long max = 0;
        long[] values = new long[Rating.MAX_SCORE];
        for (int score = Rating.MIN_SCORE; score <= Rating.MAX_SCORE; score++) {
            Long value = distribution == null ? null : distribution.get(Video.distributionKey(score));
            values[score - 1] = value == null ? 0 : value;
            max = Math.max(max, values[score - 1]);
        }
        for (int score = Rating.MIN_SCORE; score <= Rating.MAX_SCORE; score++) {
            int percent = max == 0 ? 0 : (int) Math.round(values[score - 1] * 100.0 / max);
            bars[score - 1].setProgressCompat(percent, false);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // 5점 줄이 맨 위에 오도록 5 → 1 순서로 막대를 만든다
    private void buildBars(Context context) {
        int rowHeight = dp(20);
        for (int score = Rating.MAX_SCORE; score >= Rating.MIN_SCORE; score--) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = new TextView(context);
            TextViewCompat.setTextAppearance(label, R.style.TextAppearance_Viewit_Caption);
            label.setText(String.valueOf(score));
            row.addView(label, new LayoutParams(dp(16), LayoutParams.WRAP_CONTENT));

            // 막대: 높이 4, 둥근 끝, 코랄 / 회색 바탕 (Figma 분포 막대)
            LinearProgressIndicator bar = new LinearProgressIndicator(context);
            bar.setMax(100);
            bar.setIndicatorColor(ContextCompat.getColor(context, R.color.positive));
            bar.setTrackColor(ContextCompat.getColor(context, R.color.action_soft));
            bar.setTrackThickness(dp(4));
            bar.setTrackCornerRadius(dp(2));
            bar.setTrackStopIndicatorSize(0);
            bar.setIndicatorTrackGapSize(0);
            row.addView(bar, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            bars[score - 1] = bar;

            binding.statsBars.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        }
    }
}
