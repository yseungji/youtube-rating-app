package com.example.viewit.ui.category;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
import com.example.viewit.data.model.Video;
import com.example.viewit.databinding.ItemCategoryVideoBinding;
import com.example.viewit.ui.common.DurationFormat;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.ThumbnailLoader;
import com.example.viewit.ui.videodetail.VideoDetailActivity;

import java.util.List;

// 카테고리 목록의 영상 한 줄(item_category_video.xml)을 맡는 ViewHolder
class CategoryVideoViewHolder extends RecyclerView.ViewHolder {

    private final ItemCategoryVideoBinding itemBinding;

    CategoryVideoViewHolder(@NonNull ItemCategoryVideoBinding itemBinding) {
        super(itemBinding.getRoot());
        this.itemBinding = itemBinding;
    }

    // 영상 하나의 내용을 한 줄에 채운다
    void bind(Video video) {
        ThumbnailLoader.load(itemBinding.categoryVideoThumbnail, video.getThumbnailUrl());
        itemBinding.categoryVideoDuration.setText(DurationFormat.format(video.getDurationSeconds()));
        itemBinding.categoryVideoTitle.setText(video.getTitle());
        itemBinding.categoryVideoChannel.setText(video.getChannelTitle());
        //평가의 유무에 따라 출력 메세지를 다르게 했다.
        long count = video.getPublicRatingCount();
        if (count > 0) {
            String average = RatingFormat.averageText(video.getRatingAverage(), count);
            itemBinding.categoryVideoRating.setText(
                itemBinding.getRoot().getContext().getString(R.string.category_rating, average, count));
        } else {
            itemBinding.categoryVideoRating.setText(R.string.category_no_rating);
        }
        //카테고리 fragement에서 영상 상세로 넘어갈 수 있다
        itemBinding.getRoot().setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), VideoDetailActivity.class);
            intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, video.getVideoId());
            v.getContext().startActivity(intent);
        });
    }
}

// 카테고리 영상 목록 어댑터
public class CategoryVideoAdapter extends RecyclerView.Adapter<CategoryVideoViewHolder> {

    private final List<Video> videos;

    public CategoryVideoAdapter(List<Video> videos) {
        this.videos = videos;
    }

    @NonNull
    @Override
    public CategoryVideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryVideoBinding itemCategoryVideoBinding = ItemCategoryVideoBinding.inflate(LayoutInflater.from(parent.getContext()),parent, false);
        return new CategoryVideoViewHolder(itemCategoryVideoBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryVideoViewHolder holder, int position) {
        holder.bind(videos.get(position));
    }

    @Override
    public int getItemCount() {
        return videos.size();

    }
}
