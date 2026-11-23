package com.example.viewit.ui.profile;

import android.content.res.ColorStateList;
import android.widget.Button;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.viewit.R;

// 팔로우 버튼 모양 바꾸기 (6.2, Figma UI4/FollowRow).
// 팔로우 중이면 회색 "팔로잉", 아니면 검정 버튼에 notFollowingText("팔로우" 또는 "맞팔로우").
// 예: FollowButtonStyle.apply(binding.followRowButton, item.isFollowedByMe(), R.string.follow_back);
public final class FollowButtonStyle {

    private FollowButtonStyle() {
    }

    public static void apply(Button button, boolean following, @StringRes int notFollowingText) {
        if (following) {
            button.setText(R.string.following);
            button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(button.getContext(), R.color.action_soft)));
            button.setTextColor(ContextCompat.getColor(button.getContext(), R.color.text_secondary));
        } else {
            button.setText(notFollowingText);
            button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(button.getContext(), R.color.action_primary)));
            button.setTextColor(ContextCompat.getColor(button.getContext(), R.color.bg_surface));
        }
    }
}
