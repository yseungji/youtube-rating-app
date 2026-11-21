package com.example.viewit.ui.feed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
import com.example.viewit.data.FeedPagingSamples;
import com.example.viewit.data.FollowingFeedSamples;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.User;
import com.example.viewit.data.model.Video;
import com.example.viewit.databinding.ItemFeedPostBinding;
import com.example.viewit.ui.common.DurationFormat;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.ThumbnailLoader;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.List;

// 1. 수업 실습 형태: ViewHolder 클래스를 위에 먼저 배치한다.
class FeedViewHolder extends RecyclerView.ViewHolder {
    private final ItemFeedPostBinding itemBinding;
    private final VideoRepository videoRepository;
    private final FeedAdapter.OnItemClickListener itemClickListener;
    int bindingGeneration;
    private String boundAuthorName = "";
    private String boundVideoTitle = "";

    //생성자 호출
    public FeedViewHolder(@NonNull ItemFeedPostBinding itemBinding,
                          @NonNull VideoRepository videoRepository,
                          @Nullable FeedAdapter.OnItemClickListener itemClickListener) {
        super(itemBinding.getRoot());
        this.itemBinding = itemBinding;
        this.videoRepository = videoRepository;
        this.itemClickListener = itemClickListener;
    }

    // 카드 1개 뷰에 데이터를 연결(바인딩)하는 메서드
    public void bind(Rating rating) {
        final int generation = ++bindingGeneration;
        Context context = itemBinding.getRoot().getContext();
        final String timeText = rating.getCreatedAt() == null
                ? context.getString(R.string.feed_unknown_time)
                : TimeAgoFormat.format(context, rating.getCreatedAt().toDate(), new Date());

        // 1) 기본 평가 데이터 채우기 (별점, 한줄평, 좋아요 수, 댓글 수)
        itemBinding.feedQuote.reviewQuoteRating.setText(
                RatingFormat.stars(rating.getScore()) + " "
                        + context.getString(R.string.rating_score, rating.getScore()));
        String comment = rating.getComment();
        itemBinding.feedQuote.reviewQuoteComment.setText(
                comment == null || comment.trim().isEmpty()
                        ? context.getString(R.string.feed_no_comment) : comment);
        updateLike(rating);
        itemBinding.feedActions.reviewActionComment.setText(context.getString(
                R.string.feed_comment_count, rating.getCommentCount()));
        loadLikeStatus(rating, generation);

        // 2) 비동기 조회 전 기본 뷰 초기화 (재사용 찌꺼기 방지)
        boundAuthorName = "";
        boundVideoTitle = "";
        showAuthor(null, timeText);
        itemBinding.feedVideoTitle.setText(R.string.feed_video_loading);
        itemBinding.feedVideoMetadata.setText("");
        itemBinding.feedThumbnail.thumbnailDuration.setText("");
        itemBinding.feedThumbnail.thumbnailDuration.setVisibility(View.GONE);
        ThumbnailLoader.load(itemBinding.feedThumbnail.thumbnailImage, null);

        // 3) 작성자 정보(닉네임, 프로필) 비동기 조회
        loadAuthor(rating.getUserId(), timeText, generation);

        // 4) 영상 정보(썸네일, 제목 등) 비동기 조회
        final boolean pagingSample = FeedPagingSamples.isSample(rating)
                || FollowingFeedSamples.isSample(rating);
        if (pagingSample) {
            String id = rating.getVideoId();
            itemBinding.feedVideoTitle.setText(context.getString(
                    R.string.feed_paging_video_title, id.substring(id.lastIndexOf('-') + 1)));
            itemBinding.feedVideoMetadata.setText(R.string.feed_paging_video_metadata);
        } else {
            loadVideo(rating.getVideoId(), generation);
        }

        // 5) 카드 클릭 이벤트 연결 (클릭 시 상세 화면으로 이동하도록 콜백 호출)
        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pagingSample) {
                    return;
                }
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(rating);
                }
            }
        };
        itemBinding.feedThumbnail.getRoot().setOnClickListener(clickListener);
        itemBinding.feedVideoTitle.setOnClickListener(clickListener);
        // 1) 좋아요 버튼 클릭
        itemBinding.feedActions.reviewActionLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pagingSample) return;
                if (itemClickListener != null) {
                    //어뎁터에 전달하면, new로 어뎁터를 생성해서, 외부에서 정보를 받는다.
                    itemClickListener.onLikeClick(rating);
                }
            }
        });
        // 2) 댓글 버튼 클릭
        itemBinding.feedActions.reviewActionComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pagingSample) return;
                if (itemClickListener != null) {
                    itemClickListener.onCommentClick(rating);
                }
            }
        });
        // 3) 공유 버튼 클릭
        itemBinding.feedActions.reviewActionShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(pagingSample) return;
                if(itemClickListener != null) {
                    itemClickListener.onShareClick(rating,boundAuthorName,boundVideoTitle);
                }
            }
        });
    }

    // 💡 부분 업데이트(Payload): 전체 재로딩 없이 좋아요 텍스트 및 빨간 하트 상태 변경
    void updateLike(Rating rating) {
        Context context = itemBinding.getRoot().getContext();
        boolean liked = rating.isLikedByCurrentUser();

        itemBinding.feedActions.reviewActionLike.setText(context.getString(
                R.string.review_like_count, Math.max(0, rating.getLikeCount())));

        int iconRes = liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline;
        int colorRes = liked ? R.color.positive : R.color.text_secondary;

        itemBinding.feedActions.reviewActionLike.setCompoundDrawablesWithIntrinsicBounds(
                iconRes, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(
                itemBinding.feedActions.reviewActionLike,
                ColorStateList.valueOf(ContextCompat.getColor(context, colorRes)));
    }

    private void loadLikeStatus(Rating rating, int generation) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null || rating == null || rating.getId() == null || rating.getId().isEmpty()) {
            return;
        }
        FirebaseFirestore.getInstance().collection(FirestorePaths.RATINGS)
                .document(rating.getId())
                .collection("likes")
                .document(current.getUid())
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (!isCurrentBinding(generation)) {
                            return;
                        }
                        if (snapshot.exists()) {
                            rating.setLikedByCurrentUser(true);
                            updateLike(rating);
                        }
                    }
                });
    }

    private boolean isCurrentBinding(int generation) {
        return generation == bindingGeneration;
    }

    private void showAuthor(@Nullable User user, String timeText) {
        Context context = itemBinding.getRoot().getContext();
        String name = user == null ? null : user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            name = context.getString(R.string.feed_unknown_author);
        } else {
            name = name.trim();
        }
        boundAuthorName = name;
        itemBinding.feedAuthor.authorName.setText(name);
        itemBinding.feedAuthor.authorAvatar.setText(
                name.substring(0, name.offsetByCodePoints(0, 1)));
        String username = user == null ? null : user.getUsername();
        itemBinding.feedAuthor.authorMeta.setText(
                username == null || username.trim().isEmpty()
                        ? timeText : context.getString(R.string.author_meta, username, timeText));
        itemBinding.feedQuote.reviewQuoteTitle.setText(
                context.getString(R.string.review_quote_title, name));
    }

    private void loadAuthor(String userId, String timeText, int generation) {
        if (userId == null || userId.isEmpty() || userId.contains("/")) {
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (!isCurrentBinding(generation)) {
                            return;
                        }
                        try {
                            User user = snapshot.exists() ? snapshot.toObject(User.class) : null;
                            showAuthor(user, timeText);
                        } catch (RuntimeException ignored) {
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                    }
                });
    }

    private void loadVideo(String videoId, int generation) {
        if (videoId == null || videoId.isEmpty() || videoId.contains("/")) {
            itemBinding.feedVideoTitle.setText(R.string.feed_video_unavailable);
            return;
        }
        videoRepository.getVideo(videoId, new VideoRepository.Callback<Video>() {
            @Override
            public void onSuccess(Video video) {
                if (!isCurrentBinding(generation)) {
                    return;
                }
                Context context = itemBinding.getRoot().getContext();
                String duration = DurationFormat.format(video.getDurationSeconds());
                boundVideoTitle = video.getTitle() != null ? video.getTitle() : "";
                itemBinding.feedVideoTitle.setText(video.getTitle());
                String channel = video.getChannelTitle();
                itemBinding.feedVideoMetadata.setText(
                        channel == null || channel.isEmpty() ? duration
                                : context.getString(R.string.video_meta, channel, duration));
                itemBinding.feedThumbnail.thumbnailDuration.setText(duration);
                itemBinding.feedThumbnail.thumbnailDuration.setVisibility(View.VISIBLE);
                ThumbnailLoader.load(
                        itemBinding.feedThumbnail.thumbnailImage, video.getThumbnailUrl());
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isCurrentBinding(generation)) {
                    return;
                }
                itemBinding.feedVideoTitle.setText(R.string.feed_video_unavailable);
            }
        });
    }
}

// 2. 수업 실습 형태: ViewHolder 다음에 Adapter 클래스를 배치한다.
public class FeedAdapter extends RecyclerView.Adapter<FeedViewHolder> {

    private final List<Rating> ratings;
    private final VideoRepository videoRepository;
    private final OnItemClickListener itemClickListener;

    //생성자
    public FeedAdapter(List<Rating> ratings, VideoRepository videoRepository, OnItemClickListener itemClickListener) {
        this.ratings = ratings;
        this.videoRepository = videoRepository;
        this.itemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFeedPostBinding itemBinding = ItemFeedPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new FeedViewHolder(itemBinding, videoRepository, itemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        // 수업 실습처럼 holder.bind()를 호출하여 행 데이터를 채운다.
        holder.bind(ratings.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            // 페이로드(부분 갱신)인 경우 프로필/썸네일 재로딩 없이 좋아요 숫자만 갱신
            holder.updateLike(ratings.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public int getItemCount() {
        return ratings.size();
    }

    public interface OnItemClickListener {
        void onItemClick(Rating rating);
        void onLikeClick(Rating rating);         // 좋아요 클릭 (좋아요 수 토글/증가)
        void onCommentClick(Rating rating);      // 댓글 버튼 클릭 (댓글 바텀시트/화면 이동)
        void onShareClick(Rating rating, String authorName, String videoTitle); // 공유 버튼 클릭 (공유 바텀시트 열기)
    }

    public void clear() {
        int size = ratings.size();
        ratings.clear();
        if (size > 0) {
            notifyItemRangeRemoved(0, size);
        }
    }

    public void addPage(List<Rating> page) {
        int start = ratings.size();
        ratings.addAll(page);
        if (!page.isEmpty()) {
            notifyItemRangeInserted(start, page.size());
        }
    }

    @Override
    public void onViewRecycled(@NonNull FeedViewHolder holder) {
        holder.bindingGeneration++;
        super.onViewRecycled(holder);
    }
}
