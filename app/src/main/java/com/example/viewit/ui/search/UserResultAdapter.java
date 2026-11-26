package com.example.viewit.ui.search;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
import com.example.viewit.data.model.FollowListItem;
import com.example.viewit.databinding.ItemUserResultBinding;
import com.example.viewit.ui.profile.UserProfileActivity;

import java.util.List;

// 사용자 검색 결과 한 줄(item_user_result.xml): 이름 첫 글자 아바타 + 이름 + "@아이디 · 공개 평가 n개" + 팔로우 버튼
class UserResultViewHolder extends RecyclerView.ViewHolder {

    private final ItemUserResultBinding itemBinding;

    UserResultViewHolder(@NonNull ItemUserResultBinding itemBinding) {
        super(itemBinding.getRoot());
        this.itemBinding = itemBinding;
    }

    void bind(FollowListItem item, UserResultAdapter.Listener listener) {
        Context context = itemBinding.getRoot().getContext();
        String name = item.getDisplayName() == null || item.getDisplayName().isEmpty()
                ? item.getUsername() : item.getDisplayName();
        itemBinding.userResultName.setText(name);
        itemBinding.userResultAvatar.setText(name == null || name.isEmpty() ? "" : name.substring(0, 1));
        itemBinding.userResultMeta.setText(context.getString(R.string.search_user_meta,
                item.getUsername(), item.getPublicRatingCount()));
        itemBinding.userResultFollow.setText(item.isFollowedByMe() ? R.string.following : R.string.follow);
        itemBinding.userResultFollow.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onFollowClick(item, position);
            }
        });

        // 카드를 누르면 그 사람 프로필로
        itemBinding.getRoot().setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), UserProfileActivity.class);
            intent.putExtra(UserProfileActivity.EXTRA_UID, item.getUid());
            v.getContext().startActivity(intent);
        });
    }
}

// 사용자 검색 결과 어댑터. 팔로우 버튼은 Listener로 화면(SearchFragment)에 알린다
public class UserResultAdapter extends RecyclerView.Adapter<UserResultViewHolder> {

    public interface Listener {
        void onFollowClick(FollowListItem item, int position);
    }

    private final List<FollowListItem> users;
    private final Listener listener;

    public UserResultAdapter(List<FollowListItem> users, Listener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserResultBinding itemBinding = ItemUserResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        // 카드 사이 간격 (Figma 16dp)
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemBinding.getRoot().getLayoutParams();
        params.bottomMargin = parent.getResources().getDimensionPixelSize(R.dimen.space_l);
        itemBinding.getRoot().setLayoutParams(params);
        return new UserResultViewHolder(itemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull UserResultViewHolder holder, int position) {
        holder.bind(users.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }
}
