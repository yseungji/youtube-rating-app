package com.example.viewit.ui.rate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.viewit.R;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.WatchHistoryRepository;
import com.example.viewit.data.model.Video;
import com.example.viewit.databinding.FragmentRateBinding;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.example.viewit.ui.videodetail.VideoDetailActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Figma 04 평가하기 탭 (5.1).
// 앱에서 본 · 좋아요 · 구독 채널 3개 필터 칩이 동일한 목록 UI·어댑터·클릭 리스너 구조를 사용한다.
public class RateFragment extends Fragment {
    private static final String TAG = "RateFragment";
    private static final String STATE_SELECTED_TAB = "rate.selectedTab";

    private static final int TAB_WATCHED = 0;
    private static final int TAB_LIKED = 1;
    private static final int TAB_SUBSCRIBED = 2;
    private static final int MAX_WATCH_HISTORY = 30;

    private FragmentRateBinding binding;
    private RateVideoAdapter adapter;
    private final List<RateVideoAdapter.RateVideoItem> videoItems = new ArrayList<>();
    private final WatchHistoryRepository watchHistoryRepository = new WatchHistoryRepository();
    private final VideoRepository videoRepository = new VideoRepository();

    private int selectedTab = TAB_WATCHED;
    private int requestNumber;
    private boolean skipInitialResumeReload;
    private boolean reloadedFromResult;

    // 상세 화면에서 평가를 저장·삭제하고 돌아오면 현재 탭 목록을 새로고침한다.
    //Launcher 클래스로 정보를 돌려받는다.
    private final ActivityResultLauncher<Intent> videoDetailLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Intent data = result.getData();
                            boolean ratingChanged = data != null && data.getBooleanExtra(
                                    VideoDetailActivity.EXTRA_RATING_CHANGED, false);
                            Log.d(TAG, "상세 결과 수신: resultCode=" + result.getResultCode()
                                    + ", ratingChanged=" + ratingChanged);
                            if (result.getResultCode() == Activity.RESULT_OK
                                    && ratingChanged
                                    && binding != null) {
                                Log.d(TAG, "평가 변경 결과로 현재 탭 목록 갱신: tab=" + selectedTab);
                                reloadedFromResult = true;
                                loadCurrentTab();
                            }
                        }
                    });

    // 3개 탭(앱에서 본 · 좋아요 · 구독 채널) 모두 같은 행 클릭 리스너로 영상 상세 화면을 연다.
    private final RateVideoAdapter.OnItemClickListener videoClickListener =
            new RateVideoAdapter.OnItemClickListener() {
                @Override
                public void onVideoClick(@NonNull RateVideoAdapter.RateVideoItem item) {
                    Intent intent = new Intent(requireContext(), VideoDetailActivity.class);
                    intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, item.getVideoId());
                    if (!item.getCategoryId().isEmpty()) {
                        intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, item.getCategoryId());
                    }
                    Log.d(TAG, "상세 실행(launcher.launch): tab=" + selectedTab
                            + ", videoId=" + item.getVideoId());
                    //launch를 실행한다.
                    videoDetailLauncher.launch(intent);
                }
            };

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, TAB_WATCHED);
        }

        // 1) 공통 RecyclerView + 이름 있는 Adapter 연결
        adapter = new RateVideoAdapter(videoItems, videoClickListener);
        binding.rateVideoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rateVideoList.setAdapter(adapter);

        // 2) 회전 복원된 탭에 맞춰 선택 칩과 섹션 제목을 맞춘다.
        syncCheckedChipWithSelectedTab();
        updateSectionHeader(selectedTab);

        // 3) 필터 칩 3개 전환 리스너 연결
        binding.rateTabChipGroup.setOnCheckedStateChangeListener(new ChipGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull ChipGroup group, @NonNull List<Integer> checkedIds) {
                if (checkedIds.isEmpty()) {
                    return;
                }
                int nextTab = tabFromChipId(checkedIds.get(0));
                if (nextTab == selectedTab) {
                    return;
                }
                selectedTab = nextTab;
                Log.d(TAG, "평가하기 필터 칩 전환: tab=" + selectedTab);
                updateSectionHeader(selectedTab);
                loadCurrentTab();
            }
        });

        // 4) 당겨서 새로고침 리스너 연결 (3개 탭 공통)
        binding.rateRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.d(TAG, "평가하기 당겨서 새로고침: tab=" + selectedTab);
                loadCurrentTab();
            }
        });

        skipInitialResumeReload = true;
        loadCurrentTab();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (skipInitialResumeReload) {
            skipInitialResumeReload = false;
            return;
        }
        if (reloadedFromResult) {
            reloadedFromResult = false;
            return;
        }
        // ViewPager2 스와이프로 다른 탭에서 영상을 보고 돌아왔을 때 최근 시청 목록을 반영한다.
        if (selectedTab == TAB_WATCHED && binding != null) {
            Log.d(TAG, "onResume: 앱에서 본 목록 새로고침");
            loadCurrentTab();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState: selectedTab=" + selectedTab);
    }

    private void syncCheckedChipWithSelectedTab() {
        int chipId = R.id.chip_rate_watched;
        if (selectedTab == TAB_LIKED) {
            chipId = R.id.chip_rate_liked;
        } else if (selectedTab == TAB_SUBSCRIBED) {
            chipId = R.id.chip_rate_subscribed;
        }
        binding.rateTabChipGroup.check(chipId);
    }

    private int tabFromChipId(int chipId) {
        if (chipId == R.id.chip_rate_liked) {
            return TAB_LIKED;
        }
        if (chipId == R.id.chip_rate_subscribed) {
            return TAB_SUBSCRIBED;
        }
        return TAB_WATCHED;
    }

    // 3개 탭이 같은 섹션 헤더(제목 + 정렬 기준)를 쓰되, 선택한 탭에 맞춰 문구만 바꾼다.
    private void updateSectionHeader(int tab) {
        if (binding == null) {
            return;
        }
        if (tab == TAB_LIKED) {
            binding.rateSectionTitle.setText(R.string.rate_liked_videos);
            binding.rateSectionSort.setText(R.string.rate_sort_liked);
        } else if (tab == TAB_SUBSCRIBED) {
            binding.rateSectionTitle.setText(R.string.rate_subscribed_videos);
            binding.rateSectionSort.setText(R.string.rate_sort_subscribed);
        } else {
            binding.rateSectionTitle.setText(R.string.rate_recent_videos);
            binding.rateSectionSort.setText(R.string.rate_sort_recent);
        }
    }

    // 현재 선택된 탭의 목록을 조회한다.
    private void loadCurrentTab() {
        if (binding == null) {
            return;
        }
        final int request = ++requestNumber;
        videoItems.clear();
        adapter.notifyDataSetChanged();
        binding.rateStatus.setVisibility(View.GONE);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            hideLoading();
            showStatus(R.string.rate_login_required);
            return;
        }

        showLoading();
        if (selectedTab == TAB_WATCHED) {
            loadWatchedVideos(request);
        } else if (selectedTab == TAB_LIKED) {
            loadOAuthTabVideos(request, R.string.rate_liked_connect_required);
        } else {
            loadOAuthTabVideos(request, R.string.rate_subscribed_connect_required);
        }
    }

    // 1) "앱에서 본" 탭: users/{uid}/watchHistory 를 최신 시청순으로 읽고 VideoRepository로 영상 정보를 채운다.
    private void loadWatchedVideos(final int request) {
        watchHistoryRepository.loadRecentWatches(MAX_WATCH_HISTORY)
                .addOnSuccessListener(new OnSuccessListener<List<WatchHistoryRepository.WatchHistoryEntry>>() {
                    @Override
                    public void onSuccess(final List<WatchHistoryRepository.WatchHistoryEntry> entries) {
                        if (binding == null || request != requestNumber) {
                            return;
                        }
                        if (entries.isEmpty()) {
                            hideLoading();
                            showStatus(R.string.rate_watched_empty);
                            Log.d(TAG, "앱에서 본 영상 없음");
                            return;
                        }

                        final List<String> videoIds = new ArrayList<>();
                        final Map<String, Timestamp> watchedAtById = new HashMap<>();
                        for (WatchHistoryRepository.WatchHistoryEntry entry : entries) {
                            videoIds.add(entry.getVideoId());
                            watchedAtById.put(entry.getVideoId(), entry.getLastWatchedAt());
                        }

                        videoRepository.getVideos(videoIds, new VideoRepository.Callback<List<Video>>() {
                            @Override
                            public void onSuccess(List<Video> videos) {
                                if (binding == null || request != requestNumber) {
                                    return;
                                }
                                hideLoading();
                                Context context = getContext();
                                if (context == null) {
                                    return;
                                }
                                Date now = new Date();
                                List<RateVideoAdapter.RateVideoItem> items = new ArrayList<>();
                                for (Video video : videos) {
                                    String videoId = video.getVideoId();
                                    if (videoId == null || videoId.isEmpty()) {
                                        continue;
                                    }
                                    String title = video.getTitle() != null
                                            ? video.getTitle()
                                            : context.getString(R.string.feed_video_unavailable);
                                    String metadata = buildWatchedMetadata(
                                            context,
                                            video.getChannelTitle(),
                                            watchedAtById.get(videoId),
                                            now);
                                    items.add(new RateVideoAdapter.RateVideoItem(
                                            videoId,
                                            video.getCategoryId(),
                                            title,
                                            metadata,
                                            video.getThumbnailUrl()));
                                }
                                showVideoItems(items, R.string.rate_watched_empty);
                                Log.d(TAG, "앱에서 본 영상 표시 완료: count=" + items.size());
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                if (binding == null || request != requestNumber) {
                                    return;
                                }
                                hideLoading();
                                Log.e(TAG, "앱에서 본 영상 정보 조회 실패: " + error.getClass().getSimpleName());
                                showStatus(R.string.rate_history_load_error);
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        if (binding == null || request != requestNumber) {
                            return;
                        }
                        hideLoading();
                        Log.e(TAG, "시청 기록 조회 실패", error);
                        showStatus(R.string.rate_history_load_error);
                    }
                });
    }

    // 2·3) "좋아요" / "구독 채널" 탭:
    // 6.3 YouTube 계정 연동(OAuth) 완료 시 같은 RateVideoItem 목록으로 showVideoItems()에 연결한다.
    private void loadOAuthTabVideos(final int request, final int connectMessageRes) {
        watchHistoryRepository.isYouTubeConnected()
                .addOnSuccessListener(new OnSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean connected) {
                        if (binding == null || request != requestNumber) {
                            return;
                        }
                        hideLoading();
                        if (Boolean.TRUE.equals(connected)) {
                            Log.d(TAG, "YouTube 연동됨(OAuth 목록 연결 대기): tab=" + selectedTab);
                            showStatus(R.string.rate_oauth_connected_preparing);
                        } else {
                            Log.d(TAG, "YouTube 미연동 안내 표시: tab=" + selectedTab);
                            showStatus(connectMessageRes);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        if (binding == null || request != requestNumber) {
                            return;
                        }
                        hideLoading();
                        Log.w(TAG, "YouTube 연동 여부 확인 실패", error);
                        showStatus(connectMessageRes);
                    }
                });
    }

    private void showVideoItems(@NonNull List<RateVideoAdapter.RateVideoItem> items, int emptyMessageRes) {
        videoItems.clear();
        videoItems.addAll(items);
        adapter.notifyDataSetChanged();
        if (videoItems.isEmpty()) {
            showStatus(emptyMessageRes);
        } else {
            binding.rateStatus.setVisibility(View.GONE);
        }
    }

    private String buildWatchedMetadata(@NonNull Context context,
                                        @Nullable String channelTitle,
                                        @Nullable Timestamp lastWatchedAt,
                                        @NonNull Date now) {
        String channel = channelTitle == null ? "" : channelTitle.trim();
        String timeText = lastWatchedAt == null
                ? ""
                : TimeAgoFormat.format(context, lastWatchedAt.toDate(), now);
        if (!channel.isEmpty() && !timeText.isEmpty()) {
            return context.getString(R.string.rate_watched_meta, channel, timeText);
        }
        if (!timeText.isEmpty()) {
            return context.getString(R.string.rate_watched_time_only, timeText);
        }
        return channel;
    }

    private void showLoading() {
        if (binding == null) {
            return;
        }
        if (!binding.rateRefresh.isRefreshing()) {
            binding.rateLoading.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        if (binding == null) {
            return;
        }
        binding.rateLoading.setVisibility(View.GONE);
        binding.rateRefresh.setRefreshing(false);
    }

    private void showStatus(int messageRes) {
        if (binding == null) {
            return;
        }
        binding.rateStatus.setText(messageRes);
        binding.rateStatus.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        requestNumber++;
        if (binding != null) {
            binding.rateVideoList.setAdapter(null);
        }
        adapter = null;
        binding = null;
        super.onDestroyView();
    }
}
