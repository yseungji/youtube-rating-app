package com.example.viewit.ui.rate;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.paging.PagingConfig;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.VideoDetailActivity;
import com.example.viewit.data.RatingRepository;
import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.ActivityVideoRatingsBinding;
import com.firebase.ui.firestore.paging.FirestorePagingOptions;
import com.google.firebase.firestore.FieldPath;

// 영상 상세의 '평가 전체 보기'. 같은 영상의 공개 평가만 페이지 단위로 조회한다.
// 상세와 같은 static Adapter를 재사용하므로 별점·프로필 표시 코드를 중복 작성하지 않는다.
public class VideoRatingsActivity extends AppCompatActivity {
    private ActivityVideoRatingsBinding binding;
    private VideoDetailActivity.MyAdapter adapter;
    private boolean loadFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityVideoRatingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        String videoId = getIntent().getStringExtra(VideoDetailActivity.EXTRA_VIDEO_ID);
        if (videoId == null || videoId.trim().isEmpty() || videoId.contains("/")) {
            finish();
            return;
        }
        binding.reviewsBack.getRoot().setContentDescription(getString(R.string.video_detail_back));
        binding.reviewsBack.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 새 상세 화면을 만들지 않고 기존 상세 화면으로 돌아간다.
                finish();
            }
        });

        FirestorePagingOptions<Rating> options = new FirestorePagingOptions.Builder<Rating>()
                .setLifecycleOwner(this)
                .setQuery(new RatingRepository().publicVideoRatingsQuery(videoId)
                                .orderBy("score").orderBy(FieldPath.documentId()),
                        new PagingConfig(10, 5, false), Rating.class)
                .build();
        adapter = new VideoDetailActivity.MyAdapter(options);
        binding.reviewsList.setLayoutManager(new LinearLayoutManager(this));
        binding.reviewsList.setAdapter(adapter);
        adapter.addLoadStateListener(states -> {
            renderLoadState(states);
            return null;
        });
        binding.reviewsRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.clearProfiles();
                if (loadFailed) {
                    adapter.retry();
                } else {
                    adapter.refresh();
                }
            }
        });
    }

    private void renderLoadState(CombinedLoadStates states) {
        if (isDestroyed()) {
            return;
        }
        LoadState refresh = states.getRefresh();
        LoadState append = states.getAppend();
        boolean loading = refresh instanceof LoadState.Loading || append instanceof LoadState.Loading;
        binding.reviewsLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        loadFailed = refresh instanceof LoadState.Error || append instanceof LoadState.Error;
        if (loadFailed) {
            LoadState.Error error = refresh instanceof LoadState.Error
                    ? (LoadState.Error) refresh : (LoadState.Error) append;
            Log.e("VideoRatingsActivity", "Public rating list load failed", error.getError());
            binding.reviewsStatus.setText(R.string.video_detail_public_list_error);
            binding.reviewsStatus.setVisibility(View.VISIBLE);
        } else if (refresh instanceof LoadState.NotLoading && adapter.getItemCount() == 0) {
            binding.reviewsStatus.setText(R.string.video_detail_public_empty);
            binding.reviewsStatus.setVisibility(View.VISIBLE);
        } else {
            binding.reviewsStatus.setVisibility(View.GONE);
        }
    }
}
