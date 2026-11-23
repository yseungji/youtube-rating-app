package com.example.viewit.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.viewit.R;
import com.example.viewit.data.model.FollowListItem;
import com.example.viewit.databinding.ItemFollowRowBinding;

import java.util.ArrayList;
import java.util.List;

// 팔로워·팔로잉 목록 Adapter (6.2). 한 줄은 item_follow_row.xml.
public class FollowListAdapter extends RecyclerView.Adapter<FollowListAdapter.FollowViewHolder> {

    // 줄·버튼을 눌렀을 때 FollowListActivity에 알려 주는 통로
    public interface OnFollowRowListener {
        void onRowClick(FollowListItem item);                    // 줄 → 그 사람 프로필 열기

        void onFollowButtonClick(FollowListItem item, int position);   // 버튼 → 팔로우/언팔로우
    }

    private List<FollowListItem> items = new ArrayList<>();
    private final OnFollowRowListener listener;
    private boolean followerTab;   // 팔로워 탭이면 팔로우 전 버튼 글자가 "맞팔로우", 팔로잉 탭이면 "팔로우"

    public FollowListAdapter(OnFollowRowListener listener) {
        this.listener = listener;
    }

    // 새 목록으로 바꾸기 (FollowListActivity가 조회 결과를 받으면 부른다)
    public void setItems(List<FollowListItem> newItems, boolean followerTab) {
        this.items = newItems;
        this.followerTab = followerTab;
        notifyDataSetChanged();
    }

    // 한 줄 화면을 만든다 (화면에 보일 만큼만 만들고 다시 쓴다)
    @NonNull
    @Override
    public FollowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFollowRowBinding b = ItemFollowRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new FollowViewHolder(b);
    }

    // position번째 사람을 줄에 채운다. ⋮ 메뉴는 아직 없어서 숨긴다
    @Override
    public void onBindViewHolder(@NonNull FollowViewHolder holder, int position) {
        FollowListItem item = items.get(position);
        holder.binding.followRowName.setText(item.getDisplayName()); //이름
        holder.binding.followRowMeta.setText( //메타
            holder.itemView.getContext().getString(R.string.user_rating_count, item.getUsername(), (int)item.getPublicRatingCount() )
        );
        Glide.with(holder.itemView) //프로필 사진
            .load(item.getPhotoUrl())
            .placeholder(R.drawable.avatar_default)
            .error(R.drawable.avatar_default)
            .fallback(R.drawable.avatar_default)
            .circleCrop()
            .into(holder.binding.followRowAvatar);
        FollowButtonStyle.apply(holder.binding.followRowButton, item.isFollowedByMe(), followerTab ? R.string.follow_back : R.string.follow);
        holder.binding.getRoot().setOnClickListener(v -> listener.onRowClick(item));
        holder.binding.followRowButton.setOnClickListener(v -> listener.onFollowButtonClick(item, holder.getBindingAdapterPosition()));
        holder.binding.followRowMore.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FollowViewHolder extends RecyclerView.ViewHolder {
        final ItemFollowRowBinding binding;

        FollowViewHolder(ItemFollowRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
