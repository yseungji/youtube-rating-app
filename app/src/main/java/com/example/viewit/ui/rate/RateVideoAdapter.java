package com.example.viewit.ui.rate;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.databinding.ItemRateComponentBinding;
import com.example.viewit.ui.common.ThumbnailLoader;

import java.util.List;

// 1. 수업 실습 형태: ViewHolder 클래스를 위에 먼저 배치한다.
// 평가하기 탭의 3개 필터(앱에서 본 · 좋아요 · 구독 채널) 모두 같은 행(item_rate_component.xml)을 사용한다.
class RateVideoViewHolder extends RecyclerView.ViewHolder {

    private final ItemRateComponentBinding itemBinding;
    @Nullable
    private final RateVideoAdapter.OnItemClickListener itemClickListener;

    RateVideoViewHolder(@NonNull ItemRateComponentBinding itemBinding,
                        @Nullable RateVideoAdapter.OnItemClickListener itemClickListener) {
        super(itemBinding.getRoot());
        this.itemBinding = itemBinding;
        this.itemClickListener = itemClickListener;
    }

    void bind(@NonNull final RateVideoAdapter.RateVideoItem item) {
        ThumbnailLoader.load(itemBinding.rateVideoThumbnail, item.getThumbnailUrl());
        itemBinding.rateVideoTitle.setText(item.getTitle());
        itemBinding.rateVideoMetadata.setText(item.getMetadata());

        itemBinding.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (itemClickListener != null) {
                    itemClickListener.onVideoClick(item);
                }
            }
        });
    }
}

// 2. 수업 실습 형태: ViewHolder 다음에 Adapter 클래스를 배치한다.
public class RateVideoAdapter extends RecyclerView.Adapter<RateVideoViewHolder> {

    // 평가하기 탭 3개(앱에서 본 · 좋아요 · 구독 채널)가 공통으로 사용하는 목록 행 데이터.
    public static class RateVideoItem {
        private final String videoId;
        private final String categoryId;
        private final String title;
        private final String metadata;
        private final String thumbnailUrl;

        public RateVideoItem(@NonNull String videoId,
                             @Nullable String categoryId,
                             @NonNull String title,
                             @NonNull String metadata,
                             @Nullable String thumbnailUrl) {
            this.videoId = videoId;
            this.categoryId = categoryId != null ? categoryId : "";
            this.title = title;
            this.metadata = metadata;
            this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";
        }

        @NonNull
        public String getVideoId() {
            return videoId;
        }

        @NonNull
        public String getCategoryId() {
            return categoryId;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getMetadata() {
            return metadata;
        }

        @NonNull
        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
    }

    public interface OnItemClickListener {
        void onVideoClick(@NonNull RateVideoItem item);
    }

    private final List<RateVideoItem> items;
    @Nullable
    private final OnItemClickListener itemClickListener;

    public RateVideoAdapter(@NonNull List<RateVideoItem> items,
                            @Nullable OnItemClickListener itemClickListener) {
        this.items = items;
        this.itemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public RateVideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRateComponentBinding itemBinding = ItemRateComponentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RateVideoViewHolder(itemBinding, itemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull RateVideoViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
