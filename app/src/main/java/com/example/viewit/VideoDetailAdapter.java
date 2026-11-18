package com.example.viewit;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.ItemReviewDetailBinding;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.firebase.ui.firestore.paging.FirestorePagingAdapter;
import com.firebase.ui.firestore.paging.FirestorePagingOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// =========================================================================
// 1. 헤더/상태안내 영역을 ConcatAdapter로 묶어주는 Section ViewHolder & Adapter
// =========================================================================
class DetailSectionViewHolder extends RecyclerView.ViewHolder {
    final FrameLayout container;

    DetailSectionViewHolder(FrameLayout container) {
        super(container);
        this.container = container;
    }

    void bind(View section) {
        if (section.getParent() == container) {
            return;
        }
        if (section.getParent() instanceof ViewGroup) {
            ((ViewGroup) section.getParent()).removeView(section);
        }
        container.removeAllViews();
        container.addView(section, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}

class DetailSectionAdapter extends RecyclerView.Adapter<DetailSectionViewHolder> {
    private final View section;

    DetailSectionAdapter(View section) {
        this.section = section;
    }

    @NonNull
    @Override
    public DetailSectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(parent.getContext());
        container.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new DetailSectionViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull DetailSectionViewHolder holder, int position) {
        holder.bind(section);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @Override
    public void onViewRecycled(@NonNull DetailSectionViewHolder holder) {
        holder.container.removeAllViews();
        super.onViewRecycled(holder);
    }
}

// =========================================================================
// 2. 영상 상세 화면의 공개 평가 카드 1개 뷰홀더 (ViewHolder)
// =========================================================================
class VideoDetailViewHolder extends RecyclerView.ViewHolder {
    private final ItemReviewDetailBinding itemBinding;
    String boundUid;
    String timeText;
    int bindingGeneration;

    public VideoDetailViewHolder(@NonNull ItemReviewDetailBinding itemBinding) {
        super(itemBinding.getRoot());
        this.itemBinding = itemBinding;
    }

    public void bind(Rating rating) {
        bindingGeneration++;
        boundUid = rating.getUserId();
        Context context = itemBinding.getRoot().getContext();
        timeText = rating.getCreatedAt() == null
                ? context.getString(R.string.video_detail_review_unknown_time)
                : TimeAgoFormat.format(context, rating.getCreatedAt().toDate(), new Date());
        itemBinding.reviewRating.setText(context.getString(
                R.string.video_detail_review_score, RatingFormat.stars(rating.getScore()), rating.getScore()));
        String comment = rating.getComment();
        itemBinding.reviewComment.setText(comment == null || comment.trim().isEmpty()
                ? context.getString(R.string.video_detail_review_no_comment) : comment);
        itemBinding.reviewActions.reviewActionLike.setText(context.getString(
                R.string.review_like_count, Math.max(0, rating.getLikeCount())));
        itemBinding.reviewActions.reviewActionComment.setText(context.getString(
                R.string.video_detail_review_comment_count, Math.max(0, rating.getCommentCount())));
        showProfile(null);
    }

    void showProfile(User profile) {
        Context context = itemBinding.getRoot().getContext();
        String name = boundUid;
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null && current.getUid().equals(boundUid)
                && current.getDisplayName() != null) {
            name = current.getDisplayName();
        }
        if (profile != null && profile.getDisplayName() != null
                && !profile.getDisplayName().trim().isEmpty()) {
            name = profile.getDisplayName().trim();
        }
        if (name == null || name.trim().isEmpty()) {
            name = context.getString(R.string.video_detail_review_unknown_author);
        }
        itemBinding.reviewAuthor.authorName.setText(name);
        itemBinding.reviewAuthor.authorAvatar.setText(name.substring(0, name.offsetByCodePoints(0, 1)));
        String username = profile == null ? null : profile.getUsername();
        itemBinding.reviewAuthor.authorMeta.setText(username == null || username.trim().isEmpty()
                ? timeText : context.getString(R.string.author_meta, username, timeText));
    }
}

// =========================================================================
// 3. 영상 상세 화면의 전체 공개 평가 페이징 어댑터 (FirebaseUI Paging Adapter)
// =========================================================================
public class VideoDetailAdapter extends FirestorePagingAdapter<Rating, VideoDetailViewHolder> {
    private final Map<String, Task<DocumentSnapshot>> profiles = new HashMap<>();

    public VideoDetailAdapter(@NonNull FirestorePagingOptions<Rating> options) {
        super(options);
    }

    public void clearProfiles() {
        profiles.clear();
    }

    @Override
    protected void onBindViewHolder(@NonNull VideoDetailViewHolder holder, int position, @NonNull Rating model) {
        holder.bind(model);
        final String uid = model.getUserId();
        if (uid == null || uid.isEmpty() || uid.contains("/")) {
            return;
        }
        final int generation = holder.bindingGeneration;
        Task<DocumentSnapshot> profile = profiles.get(uid);
        if (profile == null) {
            profile = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS)
                    .document(uid).get(Source.SERVER);
            profiles.put(uid, profile);
        }
        profile.addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot snapshot) {
                if (generation != holder.bindingGeneration || !uid.equals(holder.boundUid)) {
                    return;
                }
                try {
                    holder.showProfile(snapshot.exists() ? snapshot.toObject(User.class) : null);
                } catch (RuntimeException error) {
                    Log.w("VideoDetailAdapter", "Author profile conversion failed", error);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception error) {
                Log.w("VideoDetailAdapter", "Author profile load failed", error);
            }
        });
    }

    @NonNull
    @Override
    public VideoDetailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewDetailBinding itemBinding = ItemReviewDetailBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VideoDetailViewHolder(itemBinding);
    }

    @Override
    public void onViewRecycled(@NonNull VideoDetailViewHolder holder) {
        holder.bindingGeneration++;
        holder.boundUid = null;
        super.onViewRecycled(holder);
    }
}
