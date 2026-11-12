package com.example.viewit.debug;

import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.R;
import com.example.viewit.data.VideoCachePolicy;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Video;
import com.example.viewit.data.youtube.YouTubeApi;
import com.example.viewit.data.youtube.YouTubeException;
import com.example.viewit.databinding.ActivityVideoInfoTestBinding;
import com.example.viewit.databinding.ItemVideoRowBinding;
import com.example.viewit.ui.common.DurationFormat;
import com.example.viewit.ui.common.ThumbnailLoader;
import com.example.viewit.ui.common.YouTubeErrorMessage;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

// YouTube videos.list 호출과 Firestore 30일 저장 확인 화면 (디버그 빌드 전용, 4.1).
// 영상 ID(쉼표로 여러 개)를 넣고 불러오면 첫 영상은 큰 썸네일에, 모든 영상은 영상 한 줄 목록에 채운다.
// 아래 bindRow()는 다른 화면에서 Video를 영상 한 줄에 채우는 방법의 예시이기도 하다.
public class VideoInfoTestActivity extends AppCompatActivity {

    private ActivityVideoInfoTestBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityVideoInfoTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        binding.videoTestLoad.setOnClickListener(v -> load());
        binding.videoTestLoadCached.setOnClickListener(v -> loadCached());
        binding.videoTestMakeStale.setOnClickListener(v -> makeStale());
    }

    // VideoRepository로 불러오기: Firestore에 30일 안의 저장본이 있으면 그대로, 없으면 YouTube에서 받아 저장
    private void loadCached() {
        hideKeyboard();
        List<String> ids = parseIds(binding.videoTestInput.getText().toString());
        if (ids.isEmpty()) {
            binding.videoTestStatus.setText(R.string.video_test_empty);
            return;
        }
        setButtonsEnabled(false);
        binding.videoTestStatus.setText(R.string.video_test_loading);
        long startedAt = System.currentTimeMillis();

        new VideoRepository().getVideos(ids, new VideoRepository.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> videos) {
                setButtonsEnabled(true);
                showVideos(ids.size(), videos);
                // 이번에 YouTube에서 받은 영상은 수집 시각이 방금 전, 저장본은 그 이전
                int fresh = 0;
                for (Video video : videos) {
                    if (video.getFetchedAt() != null && video.getFetchedAt().toDate().getTime() >= startedAt - 1000) {
                        fresh++;
                    }
                }
                binding.videoTestStatus.setText(getString(R.string.video_test_cached_result,
                        ids.size(), videos.size(), videos.size() - fresh, fresh));
            }

            @Override
            public void onError(@NonNull Exception error) {
                setButtonsEnabled(true);
                binding.videoTestStatus.setText(getString(R.string.video_test_cached_error,
                        YouTubeErrorMessage.of(VideoInfoTestActivity.this, error)));
            }
        });
    }

    // 30일 규칙 확인용: 저장본의 수집 시각만 31일 전으로 바꾼다. 다시 불러오면 YouTube에서 새로 받아야 한다
    private void makeStale() {
        List<String> ids = parseIds(binding.videoTestInput.getText().toString());
        if (ids.isEmpty()) {
            binding.videoTestStatus.setText(R.string.video_test_empty);
            return;
        }
        Timestamp old = new Timestamp(new Date(System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(VideoCachePolicy.MAX_AGE_DAYS + 1)));
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        for (String id : ids) {
            batch.update(db.collection(FirestorePaths.VIDEOS).document(id), "fetchedAt", old);
        }
        batch.commit()
                .addOnSuccessListener(unused -> binding.videoTestStatus.setText(
                        getString(R.string.video_test_stale_done, ids.size())))
                .addOnFailureListener(e -> binding.videoTestStatus.setText(
                        getString(R.string.video_test_stale_failed, e.getMessage())));
    }

    private void setButtonsEnabled(boolean enabled) {
        binding.videoTestLoad.setEnabled(enabled);
        binding.videoTestLoadCached.setEnabled(enabled);
        binding.videoTestMakeStale.setEnabled(enabled);
    }

    private void load() {
        hideKeyboard();
        List<String> ids = parseIds(binding.videoTestInput.getText().toString());
        if (ids.isEmpty()) {
            binding.videoTestStatus.setText(R.string.video_test_empty);
            return;
        }
        setButtonsEnabled(false);
        binding.videoTestStatus.setText(R.string.video_test_loading);

        YouTubeApi.getInstance().fetchVideos(ids, new YouTubeApi.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> videos) {
                setButtonsEnabled(true);
                showVideos(ids.size(), videos);
            }

            @Override
            public void onError(YouTubeException error) {
                setButtonsEnabled(true);
                binding.videoTestStatus.setText(getString(R.string.video_test_error,
                        YouTubeErrorMessage.of(VideoInfoTestActivity.this, error), error.getReason().name()));
            }
        });
    }

    private void showVideos(int requested, List<Video> videos) {
        binding.videoTestStatus.setText(getString(R.string.video_test_result, requested, videos.size()));
        binding.videoTestRows.removeAllViews();
        binding.videoTestFields.setText("");
        if (videos.isEmpty()) {
            ThumbnailLoader.load(binding.videoTestThumbnail.thumbnailImage, null);
            binding.videoTestThumbnail.thumbnailDuration.setText("");
            return;
        }

        Video first = videos.get(0);
        ThumbnailLoader.load(binding.videoTestThumbnail.thumbnailImage, first.getThumbnailUrl());
        binding.videoTestThumbnail.thumbnailDuration.setText(DurationFormat.format(first.getDurationSeconds()));

        StringBuilder fields = new StringBuilder();
        for (Video video : videos) {
            ItemVideoRowBinding row = ItemVideoRowBinding.inflate(getLayoutInflater(), binding.videoTestRows, true);
            bindRow(row, video);
            fields.append(describe(video)).append('\n');
        }
        binding.videoTestFields.setText(fields.toString().trim());
    }

    private void bindRow(ItemVideoRowBinding row, Video video) {
        ThumbnailLoader.load(row.videoRowThumbnail, video.getThumbnailUrl());
        row.videoRowTitle.setText(video.getTitle());
        row.videoRowMeta.setText(getString(R.string.video_meta,
                video.getChannelTitle(), DurationFormat.format(video.getDurationSeconds())));
    }

    private static String describe(Video video) {
        return "videoId: " + video.getVideoId()
                + "\nchannelId: " + video.getChannelId()
                + "\ncategoryId: " + video.getCategoryId()
                + "\ndurationSeconds: " + video.getDurationSeconds()
                + "\npublishedAt: " + (video.getPublishedAt() == null ? "-" : video.getPublishedAt().toDate())
                + "\nfetchedAt: " + (video.getFetchedAt() == null ? "-" : video.getFetchedAt().toDate())
                + "\nthumbnailUrl: " + video.getThumbnailUrl()
                + "\n";
    }

    // "a, b ,c" → [a, b, c]
    private static List<String> parseIds(String text) {
        List<String> ids = new ArrayList<>();
        for (String part : text.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.videoTestInput.getWindowToken(), 0);
        }
        binding.videoTestInput.clearFocus();
    }
}
