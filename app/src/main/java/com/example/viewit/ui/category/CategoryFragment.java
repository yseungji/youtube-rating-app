package com.example.viewit.ui.category;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
import com.example.viewit.data.VideoCachePolicy;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.Video;
import com.example.viewit.data.youtube.YouTubeApi;
import com.example.viewit.data.youtube.YouTubeException;
import com.example.viewit.databinding.FragmentCategoryBinding;
import com.example.viewit.ui.common.YouTubeErrorMessage;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 카테고리 탭 화면.
// 위쪽 카테고리 칩을 고르면 그 카테고리 영상을 목록으로 보여 주고, 영상을 누르면 영상 상세로 간다.
// - 인기순: 한국 YouTube 인기 차트. 30개씩 받고, 끝까지 내리면 다음 30개를 더 받는다
// - 평점순: 우리 앱 공개 평가의 평균 별점이 높은 순 (평가 수 가중치는 나중에)
public class CategoryFragment extends Fragment {

    private FragmentCategoryBinding binding;
    private CategoryVideoAdapter adapter;
    private final List<Video> videos = new ArrayList<>();

    // 지금 고른 카테고리의 YouTube 카테고리 ID. "전체"면 null
    private String currentCategoryId;

    private final VideoRepository videoRepository = new VideoRepository();
    // 인기순 다음 페이지 토큰. null이면 더 받을 게 없음(차트 끝이거나 평점순)
    private String nextPageToken;
    private boolean loadingMore;
    // 칩을 빨리 바꿔 응답이 늦게 도착해도 예전 요청 결과로 목록을 덮지 않도록 요청마다 번호를 붙인다
    private int requestId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CategoryVideoAdapter(videos);

        binding.categoryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.categoryList.setAdapter(adapter);

        binding.categoryChipGroup.setOnCheckedStateChangeListener((chipGroup, checkIds) -> {
            if(checkIds.isEmpty()) return;
            Chip chip = chipGroup.findViewById(checkIds.get(0));
            currentCategoryId = (String) chip.getTag();
            if(binding.categorySortGroup.getCheckedChipId() == R.id.chip_sort_popular)
                loadPopular();
            else if(binding.categorySortGroup.getCheckedChipId() == R.id.chip_sort_rating)
                loadRating();
        });

        binding.categorySortGroup.setOnCheckedStateChangeListener((chipGroup, checkIds) -> {
            if(checkIds.isEmpty()) return;
            int checkedId = checkIds.get(0);
            if(checkedId == R.id.chip_sort_popular) {
                loadPopular();
            }
            else if(checkedId == R.id.chip_sort_rating) {
                loadRating();
            }
        });

        binding.categoryRefresh.setOnRefreshListener(() -> {
            if(binding.categorySortGroup.getCheckedChipId() == R.id.chip_sort_popular)
                loadPopular();
            else if( binding.categorySortGroup.getCheckedChipId() == R.id.chip_sort_rating)
                loadRating();
        });

        setupLoadMore();
        loadPopular();
    }

    // 지금 고른 카테고리의 인기 차트 첫 페이지를 받아 목록을 새로 채운다
    private void loadPopular() {
        if (!binding.categoryRefresh.isRefreshing()) {
            binding.categoryLoading.setVisibility(View.VISIBLE);
        }
        binding.categoryMessage.setVisibility(View.GONE);
        nextPageToken = null;
        loadingMore = false;
        int request = ++requestId;

        YouTubeApi.getInstance().fetchPopularVideos(currentCategoryId, null, new YouTubeApi.Callback<YouTubeApi.PopularPage>() {
            @Override
            public void onSuccess(YouTubeApi.PopularPage page) {
                if (binding == null || request != requestId) {
                    return; // 화면이 사라졌거나 그 사이 다른 칩을 골랐으면 무시
                }
                hideLoading();
                videos.clear();
                videos.addAll(page.videos);
                nextPageToken = page.nextPageToken;
                adapter.notifyDataSetChanged();
                binding.categoryList.scrollToPosition(0);
                if (videos.isEmpty()) {
                    showMessage(getString(R.string.category_empty));
                }
                attachRatings(page.videos, request);
            }

            @Override
            public void onError(YouTubeException error) {
                if (binding == null || request != requestId) {
                    return;
                }
                hideLoading();
                videos.clear();
                adapter.notifyDataSetChanged();
                showMessage(YouTubeErrorMessage.of(requireContext(), error));
            }
        });
    }

    // 인기순 다음 페이지를 받아 목록 끝에 붙인다. 실패하면 조용히 멈추고, 다시 내리면 다시 시도한다
    private void loadMorePopular() {
        if (loadingMore || nextPageToken == null) {
            return;
        }
        loadingMore = true;
        int request = requestId;
        YouTubeApi.getInstance().fetchPopularVideos(currentCategoryId, nextPageToken, new YouTubeApi.Callback<YouTubeApi.PopularPage>() {
            @Override
            public void onSuccess(YouTubeApi.PopularPage page) {
                if (binding == null || request != requestId) {
                    return;
                }
                loadingMore = false;
                int start = videos.size();
                videos.addAll(page.videos);
                nextPageToken = page.nextPageToken;
                adapter.notifyItemRangeInserted(start, page.videos.size());
                attachRatings(page.videos, request);
            }

            @Override
            public void onError(YouTubeException error) {
                if (request == requestId) {
                    loadingMore = false;
                }
            }
        });
    }

    // 목록 끝에서 3줄 안쪽까지 내려오면 인기순 다음 페이지를 받는다
    private void setupLoadMore() {
        binding.categoryList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null
                        && layoutManager.findLastVisibleItemPosition() >= videos.size() - 3) {
                    loadMorePopular();
                }
            }
        });
    }

    // 인기순 영상에 우리 앱 평균 별점·평가 수를 붙인다 (videos에 저장된 값만 읽음, YouTube 사용량 0)
    private void attachRatings(List<Video> pageVideos, int request) {
        List<String> ids = new ArrayList<>();
        for (Video video : pageVideos) {
            ids.add(video.getVideoId());
        }
        videoRepository.getSavedVideos(ids, new VideoRepository.Callback<Map<String, Video>>() {
            @Override
            public void onSuccess(Map<String, Video> saved) {
                if (binding == null || request != requestId) {
                    return;
                }
                for (Video video : pageVideos) {
                    VideoCachePolicy.copyStats(saved.get(video.getVideoId()), video);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(@NonNull Exception error) {
                // 별점을 못 붙여도 목록은 그대로 둔다 ("아직 평가 없음"으로 보임)
            }
        });
    }

    // 지금 고른 카테고리의 평점순 목록을 받아 목록을 새로 채운다
    private void loadRating() {
        if (!binding.categoryRefresh.isRefreshing()) {
            binding.categoryLoading.setVisibility(View.VISIBLE);
        }
        binding.categoryMessage.setVisibility(View.GONE);
        nextPageToken = null; // 평점순은 평가된 영상을 한 번에 모두 받는다 (더 불러오기 없음)
        int request = ++requestId;

        videoRepository.getTopRatedVideos(currentCategoryId, new VideoRepository.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> result) {
                if (binding == null || request != requestId) {
                    return;
                }
                hideLoading();
                videos.clear();
                videos.addAll(result);
                adapter.notifyDataSetChanged();
                binding.categoryList.scrollToPosition(0);
                if (videos.isEmpty()) {
                    showMessage(getString(R.string.category_rating_empty));
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding == null || request != requestId) {
                    return;
                }
                hideLoading();
                videos.clear();
                adapter.notifyDataSetChanged();
                showMessage(YouTubeErrorMessage.of(requireContext(), error));
            }
        });
    }

    // 목록 대신 안내 문구(빈 목록·오류·준비 중)를 보여 준다
    private void showMessage(String message) {
        binding.categoryMessage.setText(message);
        binding.categoryMessage.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        binding.categoryLoading.setVisibility(View.GONE);
        binding.categoryRefresh.setRefreshing(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
