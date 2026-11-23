package com.example.viewit.ui.feed;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.widget.Toast;

import com.example.viewit.R;
import com.example.viewit.ui.comment.CommentBottomSheetFragment;
import com.example.viewit.ui.share.ShareRatingBottomSheetFragment;
import com.example.viewit.ui.videodetail.VideoDetailActivity;
import com.example.viewit.ui.watchlater.WatchLaterBottomSheetFragment;
import com.example.viewit.data.FeedRepository;
import com.example.viewit.data.FeedPagingSamples;
import com.example.viewit.data.FollowingFeedSamples;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.FirestorePaths;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 화면 설정 → ViewHolder → 이름 있는 Adapter 순서.
// 여러 Query의 조회 위치·남은 평가 관리와 병합은 FeedRepository가 담당한다.
public class FeedFragment extends Fragment {
    private static final String TAG = "FeedFragment";
    private static final String STATE_LATEST_ORDER = "feed.latestOrder";
    private FragmentFeedBinding binding;
    // Fragment도 Activity와 같은 결과 API를 사용한다. 클릭할 때가 아니라 생성 시 등록한다.
    private final ActivityResultLauncher<Intent> videoDetailLauncher =
            //registerForActivityResult함수를 사용한다.
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            //데이터를 보내고, 나중에 받아온다
                            //영상 상세 화면에서 돌아왔을 때, 평가가 변경됐다면, 피드 목록을 새로 불러오는 코드.
                            Intent data = result.getData();
                            boolean ratingChanged = data != null
                                    && data.getBooleanExtra(VideoDetailActivity.EXTRA_RATING_CHANGED, false);
                            logDebug("상세 결과 수신: resultCode=" + result.getResultCode() + ", ratingChanged=" + ratingChanged);
                            if (result.getResultCode() == android.app.Activity.RESULT_OK
                                    && ratingChanged
                                    && binding != null) {
                                // 평가 저장·삭제가 성공한 경우에만 현재 정렬로 다시 조회한다.
                                logDebug("평가 변경 결과로 피드 갱신");
                                loadFeed(latestOrder);
                            }
                            // View가 없으면 onViewCreated()의 첫 조회에서 최신 데이터를 읽는다.
                        }
                    });
    private FeedAdapter adapter;
    private final List<Rating> feedRatings = new ArrayList<>();
    private boolean latestOrder = true;
    private boolean loading;
    private boolean hasMore;
    private boolean loadFailed;
    private int feedRequestNumber;
    private final FeedRepository feedRepository = new FeedRepository();
    private final VideoRepository videoRepository = new VideoRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 카드 아이템 클릭 시 상세 화면으로 이동하는 공통 리스너
    private final FeedAdapter.OnItemClickListener itemClickListener = new FeedAdapter.OnItemClickListener() {
        @Override
        public void onItemClick(Rating rating) {
            Intent intent = new Intent(requireContext(), VideoDetailActivity.class);
            intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, rating.getVideoId());
            intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, rating.getCategoryId());
            logDebug("상세 실행: launcher.launch");
            videoDetailLauncher.launch(intent);
        }
        @Override
        public void onLikeClick(Rating rating) {
            if (rating == null || rating.getId() == null) return;

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(getContext(), R.string.reactions_login_required, LENGTH_SHORT).show();
                return;
            }

            final String uid = user.getUid();
            DocumentReference likeRef = db.collection(FirestorePaths.RATINGS)
                    .document(rating.getId())
                    .collection("likes")
                    .document(uid);

            DocumentReference ratingRef = db.collection(FirestorePaths.RATINGS)
                    .document(rating.getId());

            likeRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // 이미 좋아요 누른 상태 -> 좋아요 취소 (-1 및 빨간 하트 해제)
                        likeRef.delete();
                        ratingRef.update("likeCount", FieldValue.increment(-1));
                        rating.setLikeCount(Math.max(0, rating.getLikeCount() - 1));
                        rating.setLikedByCurrentUser(false);
                    } else {
                        // 좋아요 안 누른 상태 -> 좋아요 등록 (+1 및 빨간 하트 표시)
                        Map<String, Object> data = new HashMap<>();
                        data.put("createdAt", FieldValue.serverTimestamp());
                        likeRef.set(data);

                        ratingRef.update("likeCount", FieldValue.increment(1));
                        rating.setLikeCount(rating.getLikeCount() + 1);
                        rating.setLikedByCurrentUser(true);
                    }

                    // 전체 목록을 새로고침하지 않고, 좋아요를 누른 카드 1개에 페이로드("LIKE_CHANGED")만 전달하여 부분 업데이트!
                    int index = feedRatings.indexOf(rating);
                    if (index != -1 && adapter != null) {
                        adapter.notifyItemChanged(index, "LIKE_CHANGED");
                    } else if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        }

        @Override
        public void onCommentClick(Rating rating) {
            if (rating == null || rating.getId() == null) return;
            CommentBottomSheetFragment sheet = CommentBottomSheetFragment.newInstance(rating.getId());
            sheet.show(getChildFragmentManager(), "CommentBottomSheet");
        }
        //공유 버튼 눌렀을 때 호출 메서드
        @Override
        public void onShareClick(Rating rating, String authorName, String videoTitle) {
            if (rating == null) {
                return;
            }
            logDebug("공유 바텀시트 열기: videoId=" + rating.getVideoId());
            ShareRatingBottomSheetFragment sheet = ShareRatingBottomSheetFragment.newInstance(
                    authorName,
                    (int) rating.getScore(),
                    rating.getComment(),
                    videoTitle,
                    rating.getVideoId());
            sheet.show(getChildFragmentManager(), "ShareRatingBottomSheet");
        }

        // 나중에 보기 버튼 눌렀을 때 바텀시트 열기
        @Override
        public void onWatchLaterClick(Rating rating, String videoTitle, String videoMeta,
                                      String thumbnailUrl, boolean alreadySaved) {
            if (rating == null || rating.getVideoId() == null) {
                return;
            }
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                makeToast(R.string.reactions_login_required);
                return;
            }
            logDebug("나중에 보기 바텀시트 열기: videoId=" + rating.getVideoId()
                    + ", alreadySaved=" + alreadySaved);
            WatchLaterBottomSheetFragment sheet = WatchLaterBottomSheetFragment.newInstance(
                    rating.getVideoId(),
                    videoTitle,
                    videoMeta,
                    thumbnailUrl,
                    alreadySaved);
            sheet.show(getChildFragmentManager(), "WatchLaterBottomSheet");
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFeedBinding.inflate(inflater,container,false);
        return binding.getRoot();
    }


    //리스너 연결하는 부분
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //화면이 다시 만들어졌을 때, 이전에 선택했던 피드 정렬 방식을 복원하는 코드
        if (savedInstanceState != null) {
            latestOrder = savedInstanceState.getBoolean(STATE_LATEST_ORDER, true);
        }
        //feedList는 리사이클러뷰 xml이다
        //한번만 만들면 되기 때문에 onViewCreated에 뒀다.
        binding.feedList.setLayoutManager(new LinearLayoutManager(getContext()));
        RecyclerView.ItemAnimator animator = binding.feedList.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        adapter = new FeedAdapter(feedRatings, videoRepository, itemClickListener);
        binding.feedList.setAdapter(adapter);

        // 공유 바텀시트(ShareRatingBottomSheetFragment)에서 setFragmentResult()로 전달한 결과를 수신한다.
        getChildFragmentManager().setFragmentResultListener(ShareRatingBottomSheetFragment.REQUEST_KEY,
            getViewLifecycleOwner(),
            new FragmentResultListener() {
                @Override
                public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
                    String action = result.getString(ShareRatingBottomSheetFragment.RESULT_ACTION, "");
                    logDebug("공유 결과 수신(setFragmentResult): action=" + action);
                    if (ShareRatingBottomSheetFragment.ACTION_COPY_LINK.equals(action)) {
                        makeToast(R.string.share_copied_toast);
                    } else if (ShareRatingBottomSheetFragment.ACTION_KAKAOTALK.equals(action)) {
                        makeToast(R.string.share_kakaotalk_opened_toast);
                    } else if (ShareRatingBottomSheetFragment.ACTION_SMS.equals(action)) {
                        makeToast(R.string.share_sms_opened_toast);
                    } else if (ShareRatingBottomSheetFragment.ACTION_MORE.equals(action)) {
                        makeToast(R.string.share_more_opened_toast);
                    }
                }
            }

        );

        //getChildFragmentManager 사용-> 자식뷰니까
        // 나중에 보기 바텀시트(WatchLaterBottomSheetFragment)에서 저장·취소 결과를 수신해 북마크 아이콘을 갱신한다.
        getChildFragmentManager().setFragmentResultListener(//setFragmentResultListener로 매니저와 연결을 시작한다.
                WatchLaterBottomSheetFragment.REQUEST_KEY,
                getViewLifecycleOwner(),
                new FragmentResultListener() {
                    @Override
                    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) { //result 매개변수를 이용해 데이터를 전달받는다.
                        String videoId = result.getString(WatchLaterBottomSheetFragment.RESULT_VIDEO_ID, "");
                        boolean saved = result.getBoolean(WatchLaterBottomSheetFragment.RESULT_SAVED, false);
                        logDebug("나중에 보기 결과 수신(setFragmentResult): videoId=" + videoId + ", saved=" + saved);
                        if (adapter != null) {
                            adapter.setVideoWatchLater(videoId, saved);
                        }
                    }
                });

        //인기순 , 최신순을 변경하는 리스너
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

    private void makeToast(int messageRes) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Toast.makeText(context, messageRes, LENGTH_SHORT).show();
    }

    private void loadFeed(boolean latest) {
        logDebug("피드 조회 시작: order=" + (latest ? "latest" : "popular"));
        latestOrder = latest;
        feedRequestNumber++;
        feedRepository.cancel();
        loading = false;
        loadFailed = false;
        hasMore = true;
        binding.feedOrder.setText(latest ? R.string.feed_order_latest : R.string.feed_order_popular);

        // 새로고침/정렬 변경 시 기존 목록을 비운다.
        if (adapter != null) {
            adapter.clear();
        }

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
                logDebug("피드 조회 성공: added=" + ratings.size()
                        + ", total=" + currentAdapter.getItemCount() + ", hasMore=" + more);
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_LATEST_ORDER, latestOrder);
        super.onSaveInstanceState(outState);
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

    // 로그 출력을 간결하게 관리하기 위한 메서드
    private void logDebug(String message) {
        Log.d(TAG, message);
    }
}
