package com.example.viewit.ui.feed;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.viewit.R;
import com.example.viewit.VideoDetailActivity;
import com.example.viewit.data.FeedRepository;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.User;
import com.example.viewit.data.model.Video;
import com.example.viewit.databinding.FragmentFeedBinding;
import com.example.viewit.databinding.ItemFeedPostBinding;
import com.example.viewit.ui.common.DurationFormat;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.ThumbnailLoader;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// 화면 설정 → ViewHolder → 이름 있는 Adapter 순서.
// 여러 Query의 조회 위치·남은 평가 관리와 병합은 FeedRepository가 담당한다.
public class FeedFragment extends Fragment {
    private FragmentFeedBinding binding;
    private FeedAdapter adapter;
    private boolean latestOrder = true;
    private boolean loading;
    private boolean hasMore;
    private boolean loadFailed;
    private int feedRequestNumber;
    private final FeedRepository feedRepository = new FeedRepository();
    private final VideoRepository videoRepository = new VideoRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.feedList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.feedOrder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFeed(!latestOrder);
            }
        });
        binding.feedRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadFeed(latestOrder);
            }
        });
        binding.feedList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || adapter == null || loadFailed) {
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    loadNextPage();
                }
            }
        });
        binding.feedStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loadFailed) {
                    loadNextPage();
                }
            }
        });
        loadFeed(latestOrder);
    }

    private void loadFeed(boolean latest) {
        latestOrder = latest;
        feedRequestNumber++;
        feedRepository.cancel();
        loading = false;
        loadFailed = false;
        hasMore = true;
        binding.feedOrder.setText(latest ? R.string.feed_order_latest : R.string.feed_order_popular);
        // 새로고침은 조회 위치와 보관 목록을 초기화하고 화면도 교체한다.
        adapter = new FeedAdapter(new ArrayList<Rating>());
        binding.feedList.setAdapter(adapter);
        if (latest && FirebaseAuth.getInstance().getCurrentUser() == null) {
            hasMore = false;
            binding.feedRefresh.setRefreshing(false);
            showStatus(R.string.feed_latest_login_required);
            return;
        }
        loading = true;
        binding.feedRefresh.setRefreshing(true);
        showStatus(R.string.feed_loading);
        feedRepository.refresh(latest, pageCallback());
    }

    private void loadNextPage() {
        if (binding == null || loading || !hasMore) {
            return;
        }
        loadFailed = false;
        loading = true;
        showStatus(R.string.feed_loading);
        feedRepository.loadNext(pageCallback());
    }

    private FeedRepository.Callback pageCallback() {
        final FragmentFeedBinding screenBinding = binding;
        final FeedAdapter currentAdapter = adapter;
        final int request = feedRequestNumber;
        return new FeedRepository.Callback() {
            private boolean isCurrent() {
                return binding == screenBinding && adapter == currentAdapter
                        && request == feedRequestNumber;
            }

            @Override
            public void onPage(List<Rating> ratings, boolean more) {
                if (!isCurrent()) {
                    return;
                }
                loading = false;
                loadFailed = false;
                hasMore = more;
                binding.feedRefresh.setRefreshing(false);
                currentAdapter.addPage(ratings);
                if (currentAdapter.getItemCount() == 0) {
                    showStatus(latestOrder ? R.string.feed_latest_empty : R.string.feed_popular_empty);
                } else {
                    binding.feedStatus.setVisibility(View.GONE);
                }
                binding.feedList.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isCurrent() && !loadFailed && !binding.feedList.canScrollVertically(1)) {
                            loadNextPage();
                        }
                    }
                });
            }

            @Override
            public void onNoFollowing() {
                if (!isCurrent()) {
                    return;
                }
                loading = false;
                loadFailed = false;
                hasMore = false;
                binding.feedRefresh.setRefreshing(false);
                showStatus(R.string.feed_no_following);
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isCurrent()) {
                    return;
                }
                loading = false;
                loadFailed = true;
                binding.feedRefresh.setRefreshing(false);
                Log.e("FeedFragment", "피드 조회 실패", error);
                showStatus(R.string.feed_load_error);
            }
        };
    }

    private void showStatus(int message) {
        binding.feedStatus.setText(message);
        binding.feedStatus.setVisibility(View.VISIBLE);
        binding.feedStatus.setClickable(loadFailed);
    }

    // ViewHolder: 게시글 한 개 전체의 Binding을 보관한다.
    private class FeedViewHolder extends RecyclerView.ViewHolder {
        private final ItemFeedPostBinding itemBinding;
        private final FeedAdapter owner;
        private int bindingGeneration;

        FeedViewHolder(@NonNull ItemFeedPostBinding itemBinding, FeedAdapter owner) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;
            this.owner = owner;
        }

        void bind(Rating rating) {
            // bind할 때마다 번호를 바꿔 이전 행의 늦은 조회 응답을 구분한다.
            final int generation = ++bindingGeneration;
            Context context = itemBinding.getRoot().getContext();
            final String timeText = rating.getCreatedAt() == null
                    ? context.getString(R.string.feed_unknown_time)
                    : TimeAgoFormat.format(context, rating.getCreatedAt().toDate(), new Date());

            // Rating에 있는 값은 추가 조회 없이 바로 표시한다.
            itemBinding.feedQuote.reviewQuoteRating.setText(
                    RatingFormat.stars(rating.getScore()) + " "
                            + context.getString(R.string.rating_score, rating.getScore()));
            String comment = rating.getComment();
            itemBinding.feedQuote.reviewQuoteComment.setText(
                    comment == null || comment.trim().isEmpty()
                            ? context.getString(R.string.feed_no_comment) : comment);
            itemBinding.feedActions.reviewActionLike.setText(context.getString(
                    R.string.review_like_count, rating.getLikeCount()));
            itemBinding.feedActions.reviewActionComment.setText(context.getString(
                    R.string.feed_comment_count, rating.getCommentCount()));

            // 재사용된 행에 이전 작성자·영상 정보가 남지 않도록 먼저 초기화한다.
            showAuthor(null, timeText);
            itemBinding.feedVideoTitle.setText(R.string.feed_video_loading);
            itemBinding.feedVideoMetadata.setText("");
            itemBinding.feedThumbnail.thumbnailDuration.setText("");
            itemBinding.feedThumbnail.thumbnailDuration.setVisibility(View.GONE);
            ThumbnailLoader.load(itemBinding.feedThumbnail.thumbnailImage, null);

            // ID로 별도 문서를 조회하고 응답이 도착하면 이 행을 채운다.
            loadAuthor(rating.getUserId(), timeText, generation);
            loadVideo(rating.getVideoId(), generation);

            // 썸네일과 제목에 new View.OnClickListener() 연결.
            itemBinding.feedThumbnail.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), VideoDetailActivity.class);
                    intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, rating.getVideoId());
                    intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, rating.getCategoryId());
                    startActivity(intent);
                }
            });
            itemBinding.feedVideoTitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(v.getContext(), VideoDetailActivity.class);
                    intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, rating.getVideoId());
                    intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, rating.getCategoryId());
                    startActivity(intent);
                }
            });
        }

        private boolean isCurrentBinding(int generation) {
            return binding != null && binding.feedList.getAdapter() == owner
                    && generation == bindingGeneration;
        }

        private void showAuthor(@Nullable User user, String timeText) {
            Context context = itemBinding.getRoot().getContext();
            String name = user == null ? null : user.getDisplayName();
            if (name == null || name.trim().isEmpty()) {
                name = context.getString(R.string.feed_unknown_author);
            } else {
                name = name.trim();
            }
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
                            } catch (RuntimeException error) {
                                Log.w("FeedFragment", "작성자 정보 변환 실패", error);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception error) {
                            // 실패하면 초기화한 작성자 안내와 Rating의 작성 시각을 유지한다.
                            Log.w("FeedFragment", "작성자 조회 실패", error);
                        }
                    });
        }

        private void loadVideo(String videoId, int generation) {
            if (videoId == null || videoId.isEmpty() || videoId.contains("/")) {
                itemBinding.feedVideoTitle.setText(R.string.feed_video_unavailable);
                return;
            }
            // 기존 저장소를 사용해 videos 조회와 30일 지난 정보 갱신 규칙을 유지한다.
            videoRepository.getVideo(videoId, new VideoRepository.Callback<Video>() {
                @Override
                public void onSuccess(Video video) {
                    if (!isCurrentBinding(generation)) {
                        return;
                    }
                    Context context = itemBinding.getRoot().getContext();
                    String duration = DurationFormat.format(video.getDurationSeconds());
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
                    Log.w("FeedFragment", "영상 조회 실패", error);
                }
            });
        }
    }

    // 일반 Adapter는 FirebaseUI 대신 직접 보관한 List와 getItemCount()를 사용한다.
    private class FeedAdapter extends RecyclerView.Adapter<FeedViewHolder> {
        private final List<Rating> ratings;

        FeedAdapter(List<Rating> ratings) {
            this.ratings = ratings;
        }

        void addPage(List<Rating> page) {
            int start = ratings.size();
            ratings.addAll(page);
            if (!page.isEmpty()) {
                notifyItemRangeInserted(start, page.size());
            }
        }

        @NonNull
        @Override
        public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemFeedPostBinding itemBinding = ItemFeedPostBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new FeedViewHolder(itemBinding, this);
        }

        @Override
        public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
            holder.bind(ratings.get(position));
        }

        @Override
        public int getItemCount() {
            return ratings.size();
        }

        @Override
        public void onViewRecycled(@NonNull FeedViewHolder holder) {
            holder.bindingGeneration++;
            super.onViewRecycled(holder);
        }
    }

    @Override
    public void onDestroyView() {
        feedRequestNumber++;
        feedRepository.cancel();
        if (binding != null) {
            binding.feedRefresh.setRefreshing(false);
            binding.feedList.setAdapter(null);
        }
        loading = false;
        adapter = null;
        binding = null;
        super.onDestroyView();
    }
}
