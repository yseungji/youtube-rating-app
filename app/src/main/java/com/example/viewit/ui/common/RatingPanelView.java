package com.example.viewit.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.example.viewit.R;
import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.ViewRatingPanelBinding;

// 영상 상세의 평가 입력 (Figma UI4/RatingPanel): 별점 + 문구 + 한줄평 + 공개 범위 + [보관함에 담기] [평가 저장].
// 저장된 평가를 보여줄 때는 setSaved(true)로 안내 문구와 버튼이 "평가 수정"으로 바뀐다.
public class RatingPanelView extends LinearLayout {

    private final ViewRatingPanelBinding binding;
    @Nullable
    private StarRatingInputView.OnScoreChangeListener scoreListener;

    public RatingPanelView(Context context) {
        this(context, null);
    }

    public RatingPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        binding = ViewRatingPanelBinding.inflate(LayoutInflater.from(context), this);

        binding.ratingPanelStars.setOnScoreChangeListener(score -> {
            updateScoreLabel();
            if (scoreListener != null) {
                scoreListener.onScoreChanged(score);
            }
        });
        updateScoreLabel();
    }

    public int getScore() {
        return binding.ratingPanelStars.getScore();
    }

    public void setScore(int score) {
        binding.ratingPanelStars.setScore(score);
        updateScoreLabel();
    }

    public String getComment() {
        return binding.ratingPanelComment.getText().toString().trim();
    }

    public void setComment(@Nullable String comment) {
        binding.ratingPanelComment.setText(comment == null ? "" : comment);
    }

    // 평가 공개 범위: Rating.VISIBILITY_PUBLIC 또는 Rating.VISIBILITY_PRIVATE
    // (View.getVisibility와 겹치지 않도록 이름에 Rating을 붙인다)
    public String getRatingVisibility() {
        int checked = binding.ratingPanelVisibility.visibilityGroup.getCheckedChipId();
        return checked == R.id.visibility_private ? Rating.VISIBILITY_PRIVATE : Rating.VISIBILITY_PUBLIC;
    }

    public void setRatingVisibility(String visibility) {
        int chipId = Rating.VISIBILITY_PRIVATE.equals(visibility) ? R.id.visibility_private : R.id.visibility_public;
        binding.ratingPanelVisibility.visibilityGroup.check(chipId);
    }

    // true: 이미 저장된 평가("내 평가가 저장됐어요." / "평가 수정"), false: 새로 쓰는 중
    public void setSaved(boolean saved) {
        binding.ratingPanelHint.setText(saved ? R.string.rating_panel_hint_saved : R.string.rating_panel_hint_editing);
        binding.ratingPanelSave.setText(saved ? R.string.rating_update : R.string.rating_save);
    }

    public void setOnScoreChangeListener(@Nullable StarRatingInputView.OnScoreChangeListener listener) {
        scoreListener = listener;
    }

    public void setOnSaveClickListener(@Nullable View.OnClickListener listener) {
        binding.ratingPanelSave.setOnClickListener(listener);
    }

    public void setOnWatchLaterClickListener(@Nullable View.OnClickListener listener) {
        binding.ratingPanelWatchLater.setOnClickListener(listener);
    }

    // 별을 고르기 전에는 문구를 숨기고 저장 버튼을 누를 수 없게 한다 (0점은 저장하지 않음)
    private void updateScoreLabel() {
        int score = getScore();
        boolean valid = Rating.isValidScore(score);
        binding.ratingPanelScoreLabel.setVisibility(valid ? VISIBLE : INVISIBLE);
        if (valid) {
            String label = getContext().getString(RatingFormat.labelRes(score));
            binding.ratingPanelScoreLabel.setText(getContext().getString(R.string.rating_score_with_label, score, label));
        }
        binding.ratingPanelSave.setEnabled(valid);
    }
}
