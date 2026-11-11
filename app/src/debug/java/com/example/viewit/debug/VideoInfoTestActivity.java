package com.example.viewit.debug;

import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.R;
import com.example.viewit.data.model.Video;
import com.example.viewit.data.youtube.YouTubeApi;
import com.example.viewit.data.youtube.YouTubeException;
import com.example.viewit.databinding.ActivityVideoInfoTestBinding;
import com.example.viewit.databinding.ItemVideoRowBinding;
import com.example.viewit.ui.common.DurationFormat;
import com.example.viewit.ui.common.ThumbnailLoader;
import com.example.viewit.ui.common.YouTubeErrorMessage;

import java.util.ArrayList;
import java.util.List;

// YouTube videos.list 호출 확인 화면 (디버그 빌드 전용, 4.1).
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
    }

    private void load() {
        hideKeyboard();
        List<String> ids = parseIds(binding.videoTestInput.getText().toString());
        if (ids.isEmpty()) {
            binding.videoTestStatus.setText(R.string.video_test_empty);
            return;
        }
        binding.videoTestLoad.setEnabled(false);
        binding.videoTestStatus.setText(R.string.video_test_loading);

        YouTubeApi.getInstance().fetchVideos(ids, new YouTubeApi.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> videos) {
                binding.videoTestLoad.setEnabled(true);
                showVideos(ids.size(), videos);
            }

            @Override
            public void onError(YouTubeException error) {
                binding.videoTestLoad.setEnabled(true);
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
