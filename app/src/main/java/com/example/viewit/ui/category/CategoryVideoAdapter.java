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
        itemBinding.categoryVideoRating.setText(R.string.category_no_rating);

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
