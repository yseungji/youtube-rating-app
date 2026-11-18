package com.example.viewit.ui.profile;

import android.content.ClipData;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.FragmentRateBinding;
import com.example.viewit.databinding.ItemRatingBinding;
import com.example.viewit.databinding.ItemRatingTileBinding;
import com.example.viewit.ui.common.ThumbnailLoader;

import java.util.ArrayList;
import java.util.List;

public class RatingTileAdapter extends RecyclerView.Adapter<RatingTileAdapter.TileViewHolder>{
    private FragmentRateBinding binding;

    public interface onTileClickListener{
        void onTileClick(Rating rating);
    }

    private List<Rating> items = new ArrayList<>();
    private final onTileClickListener listener;

    public RatingTileAdapter(onTileClickListener listener){ //생성자
        this.listener = listener;
    }

    public void setItems(List<Rating>newItems){
        this.items = newItems;
        notifyDataSetChanged(); //목록이 바뀌었음
    }

    static class TileViewHolder extends RecyclerView.ViewHolder{
        ItemRatingTileBinding b ;
        TileViewHolder(ItemRatingTileBinding b){
            super(b.getRoot());
            this.b =b;
        }
        void bind(Rating rating, onTileClickListener listener){
            b.ratingTileScore.setText(String.valueOf(rating.getScore()));
            // 썸네일: Rating에는 videoId만 있으므로 YouTube 기본 썸네일 주소를 만들어 불러온다 (ProfileFragment 배너와 같은 주소)
            String thumbnailUrl = "https://i.ytimg.com/vi/" + rating.getVideoId() + "/hqdefault.jpg";
            ThumbnailLoader.load(b.ratingTileImage, thumbnailUrl);
            itemView.setOnClickListener(v ->listener.onTileClick(rating));
        }
    }

    @NonNull
    @Override
    public RatingTileAdapter.TileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRatingTileBinding b = ItemRatingTileBinding.inflate(LayoutInflater.from(parent.getContext()), parent,false);
        return new TileViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull TileViewHolder holder, int position) {
        Rating rating = items.get(position);
        holder.bind(rating,listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
