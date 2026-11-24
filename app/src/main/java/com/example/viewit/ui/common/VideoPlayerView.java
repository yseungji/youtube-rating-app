package com.example.viewit.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.example.viewit.R;
import com.example.viewit.databinding.ViewVideoPlayerBinding;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;

// 앱 안 YouTube 플레이어 (4.2). 16:9 크기로, 화면이 멈추면 재생도 멈추고 화면이 끝나면 정리된다.
// 퍼가기가 막혔거나 재생할 수 없는 영상이면 플레이어 대신 안내 문구와 "YouTube에서 보기" 버튼을 보여 준다.
//
// 사용 예 (Activity의 onCreate):
//   binding.videoPlayer.load(this, videoId);                 // this = Activity (LifecycleOwner)
//   binding.videoPlayer.setThumbnailUrl(video.getThumbnailUrl()); // 재생 불가 안내 뒤에 깔 썸네일 (선택)
public class VideoPlayerView extends FrameLayout {

    // 영상 재생이 실제로 시작되었을 때(또는 퍼가기 불가 영상에서 YouTube 열기를 눌렀을 때) 알린다.
    public interface OnPlaybackStartedListener {
        void onPlaybackStarted(@NonNull String videoId);
    }

    private final ViewVideoPlayerBinding binding;
    private String videoId;
    private boolean initialized;
    private boolean playbackNotified;
    @Nullable
    private OnPlaybackStartedListener playbackStartedListener;

    public VideoPlayerView(@NonNull Context context) {
        this(context, null);
    }

    public VideoPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        binding = ViewVideoPlayerBinding.inflate(LayoutInflater.from(context), this);
        binding.playerOpenYoutube.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoId != null) {
                    notifyPlaybackStarted();
                    YouTubeLinks.open(getContext(), videoId);
                }
            }
        });
    }

    public void setOnPlaybackStartedListener(@Nullable OnPlaybackStartedListener listener) {
        this.playbackStartedListener = listener;
    }

    // 영상을 재생 대기 상태로 준비한다(자동 재생 안 함). 한 화면에서 한 번만 부른다.
    // owner의 수명에 맞춰 화면이 멈추면 일시정지, 화면이 끝나면 플레이어를 정리한다.
    public void load(@NonNull LifecycleOwner owner, @NonNull String videoId) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.videoId = videoId;
        owner.getLifecycle().addObserver(binding.playerYoutube);

        IFramePlayerOptions options = new IFramePlayerOptions.Builder(getContext())
                .controls(1)
                .rel(0)          // 끝난 뒤 다른 채널의 추천 영상을 띄우지 않음
                .fullscreen(0)   // 전체화면 버튼 숨김 (전체화면 처리는 아직 없음)
                .build();
        binding.playerYoutube.initialize(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer player) {
                player.cueVideo(videoId, 0f);
            }

            @Override
            public void onStateChange(@NonNull YouTubePlayer player,
                                      @NonNull PlayerConstants.PlayerState state) {
                if (state == PlayerConstants.PlayerState.PLAYING) {
                    notifyPlaybackStarted();
                }
            }

            @Override
            public void onError(@NonNull YouTubePlayer player, @NonNull PlayerConstants.PlayerError error) {
                showFallback(error == PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER
                        ? R.string.video_player_blocked : R.string.video_player_error);
            }
        }, options);
    }

    private void notifyPlaybackStarted() {
        if (playbackNotified || videoId == null) {
            return;
        }
        playbackNotified = true;
        if (playbackStartedListener != null) {
            playbackStartedListener.onPlaybackStarted(videoId);
        }
    }

    // 가로 전체화면 여부에 맞춰 내부 YouTubePlayerView 높이를 화면 가득(matchParent) / 16:9(wrapContent)로 전환한다.
    public void setFullscreen(boolean fullscreen) {
        if (fullscreen) {
            binding.playerYoutube.matchParent();
        } else {
            binding.playerYoutube.wrapContent();
        }
    }

    // 재생할 수 없을 때 안내 뒤에 흐리게 깔 썸네일
    public void setThumbnailUrl(@Nullable String thumbnailUrl) {
        ThumbnailLoader.load(binding.playerFallbackThumbnail, thumbnailUrl);
    }

    private void showFallback(int messageRes) {
        binding.playerFallbackMessage.setText(messageRes);
        binding.playerFallback.setVisibility(View.VISIBLE);
        binding.playerYoutube.setVisibility(View.INVISIBLE); // 크기는 유지해 화면이 흔들리지 않게
    }
}
