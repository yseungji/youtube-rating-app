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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

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
    private boolean ratingWriteInProgress;

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

        // 화면 진입 시 내 평가를 조회하도록 여기서 loadMyRating(); 호출.
        loadMyRating();

        // 패널 전체가 아니라 패널 안의 '평가 저장' 버튼에 리스너를 연결한다.
        binding.ratingPanel.setOnSaveClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ratingWriteInProgress) {
                    return;
                }
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
                ratingWriteInProgress = true;
                repository.saveRating(rating)
                        .addOnSuccessListener(unused -> {
                            ratingWriteInProgress = false;
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            binding.ratingPanel.setSaved(true);
                            binding.myRatingDeleteArea.setVisibility(View.VISIBLE);
                            setDeleteConfirmation(false);
                            Toast.makeText(VideoDetailActivity.this,
                                    R.string.video_detail_save_success, LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(error -> {
                            ratingWriteInProgress = false;
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Log.e("VideoDetailActivity", "Rating save failed", error);
                            Toast.makeText(VideoDetailActivity.this,
                                    R.string.video_detail_save_error, Toast.LENGTH_LONG).show();
                        });
            }
        });

    }

    // 로그인한 사용자의 평가를 조회한다. 이 메서드는 문서를 저장하거나 수정하지 않는다.
    private void loadMyRating() {
        if (ratingWriteInProgress) {
            return;
        }
        setupDeleteActions();
        binding.myRatingDeleteArea.setVisibility(View.GONE);
        setDeleteConfirmation(false);
        binding.ratingPanel.setVisibility(View.GONE);
        binding.myRatingStatus.setVisibility(View.VISIBLE);
        binding.myRatingRetry.setVisibility(View.GONE);
        binding.myRatingRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMyRating();
            }
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        //유저가 없다면
        if (user == null) {
            binding.myRatingStatus.setText(R.string.video_detail_rating_login_required);
            binding.myRatingRetry.setVisibility(View.VISIBLE);
            return;
        }

        binding.myRatingStatus.setText(R.string.video_detail_rating_loading);
        final String uid = user.getUid();
        RatingRepository repository = new RatingRepository();
        repository.getRating(uid, videoId)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser == null || !uid.equals(currentUser.getUid())) {
                            showMyRatingLoadError();
                            return;
                        }

                        try {
                            if (snapshot.exists()) {
                                // 평가 있음: 서버의 데이터를 입력 패널에 채운다.
                                Rating rating = snapshot.toObject(Rating.class);
                                if (rating == null || !Rating.isValidScore(rating.getScore())) {
                                    showMyRatingLoadError();
                                    return;
                                }
                                binding.ratingPanel.setScore((int) rating.getScore());
                                binding.ratingPanel.setComment(rating.getComment());
                                binding.ratingPanel.setRatingVisibility(rating.getVisibility());
                                binding.ratingPanel.setSaved(true);
                                binding.myRatingDeleteArea.setVisibility(View.VISIBLE);

                                // 영상 정보 조회에 실패해도 저장된 평가의 카테고리를 사용할 수 있다.
                                if ((categoryId == null || categoryId.trim().isEmpty())
                                        && rating.getCategoryId() != null) {
                                    categoryId = rating.getCategoryId().trim();
                                    binding.videoCategory.setText(getString(
                                            R.string.video_detail_category_id, categoryId));
                                }
                            } else {
                                // 평가 없음: 새로운 평가를 입력하는 상태로 만든다.
                                binding.ratingPanel.setScore(0);
                                binding.ratingPanel.setComment("");
                                binding.ratingPanel.setRatingVisibility(Rating.VISIBILITY_PUBLIC);
                                binding.ratingPanel.setSaved(false);
                            }

                            binding.myRatingStatus.setVisibility(View.GONE);
                            binding.myRatingRetry.setVisibility(View.GONE);
                            binding.ratingPanel.setVisibility(View.VISIBLE);
                        } catch (RuntimeException error) {
                            Log.e("VideoDetailActivity", "Rating data conversion failed", error);
                            showMyRatingLoadError();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        Log.e("VideoDetailActivity", "Rating load failed", error);
                        // 실패를 '평가 없음'으로 처리하지 않는다.
                        showMyRatingLoadError();
                    }
                });
    }

    private void showMyRatingLoadError() {
        binding.myRatingDeleteArea.setVisibility(View.GONE);
        binding.ratingPanel.setVisibility(View.GONE);
        binding.myRatingStatus.setText(R.string.video_detail_rating_load_error);
        binding.myRatingStatus.setVisibility(View.VISIBLE);
        binding.myRatingRetry.setVisibility(View.VISIBLE);
    }

    private void setupDeleteActions() {
        binding.myRatingDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ratingWriteInProgress) {
                    setDeleteConfirmation(true);
                }
            }
        });
        binding.myRatingDeleteCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDeleteConfirmation(false);
            }
        });
        binding.myRatingDeleteConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteMyRating();
            }
        });
    }

    private void setDeleteConfirmation(boolean confirming) {
        binding.myRatingDelete.setVisibility(confirming ? View.GONE : View.VISIBLE);
        binding.myRatingDeleteConfirmation.setVisibility(confirming ? View.VISIBLE : View.GONE);
    }

    private void deleteMyRating() {
        if (ratingWriteInProgress) {
            return;
        }
        ratingWriteInProgress = true;
        binding.ratingPanel.setVisibility(View.GONE);
        binding.myRatingDeleteArea.setVisibility(View.GONE);
        binding.myRatingRetry.setVisibility(View.GONE);
        binding.myRatingStatus.setText(R.string.video_detail_delete_loading);
        binding.myRatingStatus.setVisibility(View.VISIBLE);

        new RatingRepository().deleteRating(videoId)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        ratingWriteInProgress = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        binding.ratingPanel.setScore(0);
                        binding.ratingPanel.setComment("");
                        binding.ratingPanel.setRatingVisibility(Rating.VISIBILITY_PUBLIC);
                        binding.ratingPanel.setSaved(false);
                        setDeleteConfirmation(false);
                        binding.myRatingStatus.setVisibility(View.GONE);
                        binding.ratingPanel.setVisibility(View.VISIBLE);
                        makeToast(R.string.video_detail_delete_success);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        ratingWriteInProgress = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        // 실패하면 입력값은 그대로 두고 다시 시도할 수 있게 한다.
                        Log.e("VideoDetailActivity", "Rating delete failed", error);
                        setDeleteConfirmation(false);
                        binding.myRatingStatus.setVisibility(View.GONE);
                        binding.ratingPanel.setVisibility(View.VISIBLE);
                        binding.myRatingDeleteArea.setVisibility(View.VISIBLE);
                        makeToast(R.string.video_detail_delete_error);
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
