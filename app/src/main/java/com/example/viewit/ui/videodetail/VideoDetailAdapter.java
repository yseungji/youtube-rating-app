package com.example.viewit.ui.videodetail;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
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
import java.util.List;
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
// 2. 영상 상세 화면의 전체 공개 평가 페이징 어댑터 (FirebaseUI Paging Adapter)
// =========================================================================
public class VideoDetailAdapter extends FirestorePagingAdapter<Rating, VideoDetailAdapter.VideoDetailViewHolder> {

    private static final String TAG = "VideoDetailAdapter";
    public static final String PAYLOAD_LIKE_CHANGED = "LIKE_CHANGED";
    public static final String PAYLOAD_COMMENT_CHANGED = "COMMENT_CHANGED";

    public interface OnReviewActionListener {
        void onLikeClick(@NonNull String ratingId, @NonNull Rating rating, long currentLikeCount);

        void onCommentClick(@NonNull String ratingId,
                            @NonNull Rating rating,
                            @NonNull String authorName,
                            @NonNull String authorMeta);
    }

    // =========================================================================
    // 영상 상세 화면의 공개 평가 카드 1개 뷰홀더 (ViewHolder)
    // =========================================================================
    public static class VideoDetailViewHolder extends RecyclerView.ViewHolder {
        private final ItemReviewDetailBinding itemBinding;
        String boundRatingId;
        Rating boundRating;
        long boundLikeCount;
        String boundUid;
        String boundAuthorName = "";
        String boundAuthorMeta = "";
        String timeText;
        int bindingGeneration;

        public VideoDetailViewHolder(@NonNull ItemReviewDetailBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;
        }

        public void bind(@NonNull String ratingId,
                         @NonNull Rating rating,
                         long likeCount,
                         boolean liked,
                         long commentCount,
                         @Nullable OnReviewActionListener listener) {
            bindingGeneration++;
            boundRatingId = ratingId;
            boundRating = rating;
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

            updateLikeUi(likeCount, liked);
            updateCommentUi(commentCount);
            showProfile(null);

            itemBinding.reviewActions.reviewActionLike.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null && boundRatingId != null && boundRating != null) {
                        listener.onLikeClick(boundRatingId, boundRating, boundLikeCount);
                    }
                }
            });

            itemBinding.reviewActions.reviewActionComment.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null && boundRatingId != null && boundRating != null) {
                        listener.onCommentClick(boundRatingId, boundRating, boundAuthorName, boundAuthorMeta);
                    }
                }
            });
        }

        void updateLikeUi(long likeCount, boolean liked) {
            boundLikeCount = Math.max(0L, likeCount);
            Context context = itemBinding.getRoot().getContext();
            itemBinding.reviewActions.reviewActionLike.setText(context.getString(
                    R.string.review_like_count, boundLikeCount));

            int iconRes = liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline;
            int colorRes = liked ? R.color.positive : R.color.text_secondary;
            itemBinding.reviewActions.reviewActionLike.setCompoundDrawablesWithIntrinsicBounds(
                    iconRes, 0, 0, 0);
            TextViewCompat.setCompoundDrawableTintList(
                    itemBinding.reviewActions.reviewActionLike,
                    ColorStateList.valueOf(ContextCompat.getColor(context, colorRes)));
        }

        void updateCommentUi(long commentCount) {
            Context context = itemBinding.getRoot().getContext();
            itemBinding.reviewActions.reviewActionComment.setText(context.getString(
                    R.string.video_detail_review_comment_count, Math.max(0, commentCount)));
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
            boundAuthorName = name;
            itemBinding.reviewAuthor.authorName.setText(name);
            itemBinding.reviewAuthor.authorAvatar.setText(name.substring(0, name.offsetByCodePoints(0, 1)));
            String username = profile == null ? null : profile.getUsername();
            boundAuthorMeta = username == null || username.trim().isEmpty()
                    ? timeText : context.getString(R.string.author_meta, username, timeText);
            itemBinding.reviewAuthor.authorMeta.setText(boundAuthorMeta);
        }
    }

    private final Map<String, Task<DocumentSnapshot>> profiles = new HashMap<>();
    private final Map<String, Task<DocumentSnapshot>> likeStatusRequests = new HashMap<>();
    private final Map<String, Boolean> likedByMeCache = new HashMap<>();
    private final Map<String, Long> likeCountOverrides = new HashMap<>();
    private final Map<String, Long> commentCountOverrides = new HashMap<>();
    @Nullable
    private final OnReviewActionListener actionListener;

    public VideoDetailAdapter(@NonNull FirestorePagingOptions<Rating> options,
                              @Nullable OnReviewActionListener actionListener) {
        super(options);
        this.actionListener = actionListener;
    }

    public void clearProfiles() {
        profiles.clear();
        likeStatusRequests.clear();
        likedByMeCache.clear();
        likeCountOverrides.clear();
        commentCountOverrides.clear();
    }

    // 좋아요 토글 성공 후 해당 평가 행만 부분 갱신한다.
    public void updateLikeState(@NonNull String ratingId, boolean liked, long newLikeCount) {
        likedByMeCache.put(ratingId, liked);
        likeCountOverrides.put(ratingId, Math.max(0L, newLikeCount));
        notifyRatingRow(ratingId, PAYLOAD_LIKE_CHANGED);
    }

    // 댓글 바텀시트에서 댓글/답글을 등록했을 때 해당 평가 행의 댓글 수만 +1 갱신한다.
    public void incrementCommentCount(@NonNull String ratingId) {
        int position = findPositionByRatingId(ratingId);
        long baseCount = 0L;
        Long cached = commentCountOverrides.get(ratingId);
        if (cached != null) {
            baseCount = cached;
        } else if (position != -1) {
            DocumentSnapshot snapshot = getItem(position);
            Long stored = snapshot != null ? snapshot.getLong("commentCount") : null;
            baseCount = stored != null ? stored : 0L;
        }
        commentCountOverrides.put(ratingId, Math.max(0L, baseCount) + 1L);
        if (position != -1) {
            notifyItemChanged(position, PAYLOAD_COMMENT_CHANGED);
        }
    }

    private void notifyRatingRow(@NonNull String ratingId, @NonNull String payload) {
        int position = findPositionByRatingId(ratingId);
        if (position != -1) {
            notifyItemChanged(position, payload);
        }
    }

    private int findPositionByRatingId(@NonNull String ratingId) {
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            DocumentSnapshot snapshot = getItem(i);
            if (snapshot != null && ratingId.equals(snapshot.getId())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onBindViewHolder(@NonNull VideoDetailViewHolder holder,
                                 int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder.boundRatingId != null) {
            String ratingId = holder.boundRatingId;
            Rating model = holder.boundRating;
            if (payloads.contains(PAYLOAD_LIKE_CHANGED)) {
                long likeCount = resolveLikeCount(ratingId, model != null ? model.getLikeCount() : 0L);
                boolean liked = Boolean.TRUE.equals(likedByMeCache.get(ratingId));
                holder.updateLikeUi(likeCount, liked);
            }
            if (payloads.contains(PAYLOAD_COMMENT_CHANGED)) {
                long commentCount = resolveCommentCount(ratingId, model != null ? model.getCommentCount() : 0L);
                holder.updateCommentUi(commentCount);
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    protected void onBindViewHolder(@NonNull VideoDetailViewHolder holder, int position, @NonNull Rating model) {
        String ratingId = resolveRatingId(position, model);
        long likeCount = resolveLikeCount(ratingId, model.getLikeCount());
        boolean liked = Boolean.TRUE.equals(likedByMeCache.get(ratingId));
        long commentCount = resolveCommentCount(ratingId, model.getCommentCount());

        holder.bind(ratingId, model, likeCount, liked, commentCount, actionListener);

        final int generation = holder.bindingGeneration;
        bindAuthorProfile(holder, model.getUserId(), generation);
        bindLikeStatus(holder, ratingId, model, generation);
    }

    private String resolveRatingId(int position, @NonNull Rating model) {
        if (model.getId() != null && !model.getId().isEmpty()) {
            return model.getId();
        }
        DocumentSnapshot snapshot = getItem(position);
        if (snapshot != null) {
            return snapshot.getId();
        }
        return Rating.documentId(model.getUserId(), model.getVideoId());
    }

    private long resolveLikeCount(@NonNull String ratingId, long modelLikeCount) {
        Long override = likeCountOverrides.get(ratingId);
        return override != null ? override : modelLikeCount;
    }

    private long resolveCommentCount(@NonNull String ratingId, long modelCommentCount) {
        Long override = commentCountOverrides.get(ratingId);
        return override != null ? override : modelCommentCount;
    }

    private void bindAuthorProfile(@NonNull VideoDetailViewHolder holder,
                                   @Nullable String uid,
                                   int generation) {
        if (uid == null || uid.isEmpty() || uid.contains("/")) {
            return;
        }
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
                    Log.w(TAG, "Author profile conversion failed", error);
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception error) {
                Log.w(TAG, "Author profile load failed", error);
            }
        });
    }

    private void bindLikeStatus(@NonNull VideoDetailViewHolder holder,
                                @NonNull String ratingId,
                                @NonNull Rating model,
                                int generation) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null || ratingId.isEmpty() || ratingId.contains("/")) {
            return;
        }
        if (likedByMeCache.containsKey(ratingId)) {
            return;
        }
        Task<DocumentSnapshot> request = likeStatusRequests.get(ratingId);
        if (request == null) {
            request = FirebaseFirestore.getInstance()
                    .collection(FirestorePaths.RATINGS)
                    .document(ratingId)
                    .collection(FirestorePaths.LIKES)
                    .document(current.getUid())
                    .get();
            likeStatusRequests.put(ratingId, request);
        }
        request.addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot snapshot) {
                if (!likedByMeCache.containsKey(ratingId)) {
                    likedByMeCache.put(ratingId, snapshot.exists());
                }
                if (generation != holder.bindingGeneration || !ratingId.equals(holder.boundRatingId)) {
                    return;
                }
                boolean liked = Boolean.TRUE.equals(likedByMeCache.get(ratingId));
                long likeCount = resolveLikeCount(ratingId, model.getLikeCount());
                holder.updateLikeUi(likeCount, liked);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception error) {
                Log.w(TAG, "Like status load failed: ratingId=" + ratingId, error);
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
        holder.boundRatingId = null;
        holder.boundRating = null;
        holder.boundLikeCount = 0L;
        holder.boundUid = null;
        super.onViewRecycled(holder);
    }
}
