package com.example.viewit.ui.common;

import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.viewit.R;

// 썸네일 칸(view_thumbnail, item_video_row 등)에 영상 썸네일 이미지를 불러온다.
// 불러오는 동안과 실패했을 때는 회색 칸(action_soft)을 보여 준다. 한 번 받은 이미지는 Glide가 캐시한다.
// 예: ThumbnailLoader.load(binding.videoRowThumbnail, video.getThumbnailUrl());
public final class ThumbnailLoader {

    private ThumbnailLoader() {
    }

    public static void load(ImageView view, @Nullable String url) {
        Glide.with(view)
                .load(url)
                .placeholder(R.color.action_soft)
                .error(R.color.action_soft)
                .centerCrop()
                .into(view);
    }
}
