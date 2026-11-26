package com.example.viewit.ui.profile;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.viewit.R;
import com.example.viewit.data.model.BlockedUser;
import com.example.viewit.databinding.ItemBlockedUserBinding;

import java.util.ArrayList;
import java.util.List;

// 차단한 사용자 목록 Adapter (6.3). 한 줄은 item_blocked_user.xml. FollowListAdapter와 같은 구조.
public class BlockedUserAdapter extends RecyclerView.Adapter<BlockedUserAdapter.BlockedViewHolder> {

    // [차단 해제]를 눌렀을 때 BlockedUsersActivity에 알려 주는 통로
    public interface OnUnblockClickListener {
        void onUnblockClick(BlockedUser item);
    }

    private List<BlockedUser> items = new ArrayList<>();
    private final OnUnblockClickListener listener;

    public BlockedUserAdapter(OnUnblockClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<BlockedUser> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BlockedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBlockedUserBinding b = ItemBlockedUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new BlockedViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull BlockedViewHolder holder, int position) {
        BlockedUser item = items.get(position);
        bindAvatar(holder, item);
        // 이름, @아이디, [차단 해제]
        holder.binding.blockedRowName.setText(item.getDisplayName());
        holder.binding.blockedRowHandle.setText(holder.itemView.getContext().getString(R.string.profile_handle, item.getUsername()));
        holder.binding.blockedRowUnblock.setOnClickListener(v-> listener.onUnblockClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // 프로필 사진 (없으면 기본 사진, 동그랗게)
    private void bindAvatar(BlockedViewHolder holder, BlockedUser item) {
        Glide.with(holder.itemView)
            .load(item.getPhotoUrl())
            .placeholder(R.drawable.avatar_default)
            .error(R.drawable.avatar_default)
            .fallback(R.drawable.avatar_default)
            .circleCrop()
            .into(holder.binding.blockedRowAvatar);
    }

    static class BlockedViewHolder extends RecyclerView.ViewHolder {
        final ItemBlockedUserBinding binding;

        BlockedViewHolder(ItemBlockedUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
