package com.example.viewit.ui.category;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.data.model.Video;
import com.example.viewit.data.youtube.YouTubeApi;
import com.example.viewit.data.youtube.YouTubeException;
import com.example.viewit.databinding.FragmentCategoryBinding;
import com.example.viewit.ui.common.YouTubeErrorMessage;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

// 카테고리 탭 화면.
// 위쪽 카테고리 칩을 고르면 그 카테고리의 한국 YouTube 인기 차트를 목록으로 보여 주고(인기순),
// 영상을 누르면 영상 상세로 간다. 평점순은 다음 작업에서 붙인다.
public class CategoryFragment extends Fragment {

    private FragmentCategoryBinding binding;
    private CategoryVideoAdapter adapter;
    private final List<Video> videos = new ArrayList<>();

    // 지금 고른 카테고리의 YouTube 카테고리 ID. "전체"면 null
    private String currentCategoryId;

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
                return;// 평점순이면 카테고리만 기억해 둔다. 나중에 loadRating()으로 대체
        });

        binding.categorySortGroup.setOnCheckedStateChangeListener((chipGroup, checkIds) -> {
            if(checkIds.isEmpty()) return;
            int checkedId = checkIds.get(0);
            if(checkedId == R.id.chip_sort_popular) {
                loadPopular();
            }
            else if(checkedId == R.id.chip_sort_rating) {
                videos.clear();
                adapter.notifyDataSetChanged();
                showMessage(getString(R.string.category_rating_coming_soon));
            }
        });

        binding.categoryRefresh.setOnRefreshListener(() -> {
            if(binding.categorySortGroup.getCheckedChipId() == R.id.chip_sort_popular)
                loadPopular();
            else if( binding.categorySortGroup.getCheckedChipId() == R.id.chip_sort_rating)
                binding.categoryRefresh.setRefreshing(false);
            //평점에 관한 내용이 협의되면 그에 맞춰 loadRating()으로 평점순 불러오기 함수를 만들어 대체하고 위 내용을 지울 예정
        });
        loadPopular();
    }

    // 지금 고른 카테고리의 인기 차트를 받아 목록에 보여 준다
    private void loadPopular() {
        if (!binding.categoryRefresh.isRefreshing()) {
            binding.categoryLoading.setVisibility(View.VISIBLE);
        }
        binding.categoryMessage.setVisibility(View.GONE);

        YouTubeApi.getInstance().fetchPopularVideos(currentCategoryId, new YouTubeApi.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> result) {
                if (binding == null) {
                    return; // 응답이 오기 전에 화면이 사라졌으면 무시
                }
                hideLoading();
                videos.clear();
                videos.addAll(result);
                adapter.notifyDataSetChanged();
                if (videos.isEmpty()) {
                    showMessage(getString(R.string.category_empty));
                }
            }

            @Override
            public void onError(YouTubeException error) {
                if (binding == null) {
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
