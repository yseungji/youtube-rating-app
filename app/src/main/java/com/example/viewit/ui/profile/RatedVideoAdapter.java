package com.example.viewit.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.data.model.RatedVideoItem;
import com.example.viewit.databinding.ItemVideoRowBinding;
import com.example.viewit.ui.common.ThumbnailLoader;

import java.util.ArrayList;
import java.util.List;

// 평가한 영상 목록 Adapter (6.1). 한 줄은 item_video_row.xml (썸네일 + 제목 + 메타).
public class RatedVideoAdapter extends RecyclerView.Adapter<RatedVideoAdapter.RatedViewHolder> {

    public interface OnVideoClickListener {
        void onVideoClick(RatedVideoItem item);
    }

    private List<RatedVideoItem> items = new ArrayList<>();
    private final OnVideoClickListener listener;

    public RatedVideoAdapter(OnVideoClickListener listener) {
        this.listener = listener;
    }

    // 새 목록으로 바꾸기 (RatedVideosActivity가 불러오기·정렬 후 부른다)
    public void setItems(List<RatedVideoItem> newItems) {
        items = newItems;
        notifyDataSetChanged();
    }

    // 한 줄 화면(item_video_row) 만들기
    @NonNull
    @Override
    public RatedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVideoRowBinding b = ItemVideoRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new RatedViewHolder(b);
    }

    // position번째 평가를 줄에 채운다 ("평가하기 →"는 이 목록에서 숨김)
    @Override
    public void onBindViewHolder(@NonNull RatedViewHolder holder, int position) {
        RatedVideoItem item = items.get(position);
        holder.binding.videoRowTitle.setText(item.getTitle());
        holder.binding.videoRowMeta.setText(item.getMeta());
        holder.binding.getRoot().setOnClickListener(v -> listener.onVideoClick(item));
        ThumbnailLoader.load(holder.binding.videoRowThumbnail, item.getThumbnailUrl());
        holder.binding.videoRowAction.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RatedViewHolder extends RecyclerView.ViewHolder {
        final ItemVideoRowBinding binding;

        RatedViewHolder(ItemVideoRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
