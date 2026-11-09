package com.example.viewit;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.data.RatingRepository;
import com.example.viewit.data.VideoInfoLoader;
import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.ActivityVideoDetailBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Figma 06 영상 상세: 공통 평가 패널을 표시하는 화면.
public class VideoDetailActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_ID = "videoId";
    public static final String EXTRA_CATEGORY_ID = "categoryId";
    private final ExecutorService videoExecutor = Executors.newSingleThreadExecutor();
    private ActivityVideoDetailBinding binding;
    private String videoId;
    private String categoryId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityVideoDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.videoBack.getRoot().setContentDescription(
                getString(R.string.video_detail_back_to_feed));
        binding.videoBack.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VideoDetailActivity.this, MainActivity.class);
                // 기존 메인 위의 화면을 정리하고, 메인을 다시 열어 기본 피드 탭을 표시한다.
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });

        // 평가 탭에서 전달한 영상이 있으면 우선 사용한다.
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        // 디버그 화면을 직접 실행할 때만 '봤다고 가정한' 테스트 영상을 선택한다.
        // 실제 시청 기록이나 평가는 여기서 저장하지 않는다.
        if ((videoId == null || videoId.trim().isEmpty()) && BuildConfig.DEBUG) {
            videoId = "cUgYiPrtAA0";
            categoryId = null;
        }
        if (videoId == null || videoId.trim().isEmpty() || videoId.contains("/")) {
            makeToast(R.string.video_detail_missing_ids);
            finish();
            return;
        }
        videoId = videoId.trim();
        binding.videoId.setText(getString(R.string.video_detail_video_id, videoId));
        binding.videoOpenYoutube.setOnClickListener(v -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + videoId))));
        loadVideoInfo();
        RatingRepository repository = new RatingRepository();

        // 패널 전체가 아니라 패널 안의 '평가 저장' 버튼에 리스너를 연결한다.
        binding.ratingPanel.setOnSaveClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoId == null || videoId.trim().isEmpty() || videoId.contains("/")
                        || categoryId == null || categoryId.trim().isEmpty()) {
                    makeToast(R.string.video_detail_missing_ids);
                    return;
                }

                // 현재 화면의 입력값으로 저장할 평가 객체를 만든다.
                Rating rating = new Rating();
                rating.setVideoId(videoId.trim());
                rating.setCategoryId(categoryId.trim());
                rating.setScore(binding.ratingPanel.getScore());
                rating.setComment(binding.ratingPanel.getComment());
                rating.setVisibility(binding.ratingPanel.getRatingVisibility());

                // 작성자 UID는 Repository가 로그인 정보에서 채운다.
                // 같은 사용자·영상의 평가는 Repository가 기존 문서를 수정한다.
                repository.saveRating(rating)
                        .addOnSuccessListener(VideoDetailActivity.this, unused -> {
                            binding.ratingPanel.setSaved(true);
                            Toast.makeText(VideoDetailActivity.this,
                                    R.string.video_detail_save_success, LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(VideoDetailActivity.this, error -> {
                            Log.e("VideoDetailActivity", "Rating save failed", error);
                            Toast.makeText(VideoDetailActivity.this,
                                    R.string.video_detail_save_error, Toast.LENGTH_LONG).show();
                        });
            }
        });

    }

    private void loadVideoInfo() {
        if (BuildConfig.YOUTUBE_API_KEY.trim().isEmpty()) {
            binding.videoTitle.setText(R.string.video_detail_api_key_missing);
            return;
        }
        final String requestedVideoId = videoId;
        videoExecutor.execute(() -> {
            try {
                VideoInfoLoader.Result result = new VideoInfoLoader().load(requestedVideoId);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    categoryId = result.video.getCategoryId();
                    binding.videoTitle.setText(result.video.getTitle());
                    binding.videoChannel.setText(result.video.getChannelTitle());
                    binding.videoCategory.setText(getString(
                            R.string.video_detail_category_id, categoryId));
                    if (result.thumbnail != null) {
                        binding.videoThumbnail.setImageBitmap(result.thumbnail);
                    }
                });
            } catch (Exception error) {
                // 요청 URL에 API 키가 들어 있으므로 예외 원문은 로그에 남기지 않는다.
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        binding.videoTitle.setText(R.string.video_detail_load_error);
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        videoExecutor.shutdownNow();
        super.onDestroy();
    }

    private void makeToast(@StringRes int messageResId) {
        Toast.makeText(this, messageResId, LENGTH_SHORT).show();
    }
}
