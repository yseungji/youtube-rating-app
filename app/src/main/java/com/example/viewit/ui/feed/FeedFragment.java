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
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.viewit.R;
import com.example.viewit.ui.videodetail.VideoDetailActivity;
import com.example.viewit.data.FeedRepository;
import com.example.viewit.data.FeedPagingSamples;
import com.example.viewit.data.FollowingFeedSamples;
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
        adapter = new FeedAdapter(feedRatings, videoRepository, itemClickListener);
        binding.feedList.setAdapter(adapter);

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
