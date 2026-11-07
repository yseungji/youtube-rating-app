package com.example.viewit.data.youtube;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.viewit.BuildConfig;
import com.example.viewit.data.model.Video;
import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// YouTube Data API 호출을 모아 둔 곳. 나중에 서버(Cloud Functions)로 옮기더라도 이 클래스만 바꾸면 된다.
// 요청은 백그라운드에서 보내고, 결과는 화면(메인 스레드)에서 받는다.
//
// 사용 예:
//   YouTubeApi.getInstance().fetchVideo("영상ID", new YouTubeApi.Callback<Video>() {
//       public void onSuccess(Video video) { ... }
//       public void onError(YouTubeException e) { ... }
//   });
public final class YouTubeApi {

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(YouTubeException error);
    }

    private static final String VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos";
    // 필요한 필드만 받아 응답 크기를 줄인다. 사용량(1유닛)은 같다
    private static final String VIDEO_FIELDS =
            "items(id,snippet(title,channelId,channelTitle,categoryId,publishedAt,thumbnails),contentDetails/duration)";
    private static final int MAX_IDS_PER_REQUEST = 50;
    private static final int TIMEOUT_MS = 10_000;

    private static YouTubeApi instance;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String apiKey;

    private YouTubeApi(String apiKey) {
        this.apiKey = apiKey;
    }

    public static synchronized YouTubeApi getInstance() {
        if (instance == null) {
            instance = new YouTubeApi(BuildConfig.YOUTUBE_API_KEY);
        }
        return instance;
    }

    // 영상 하나의 제목·채널·썸네일·재생 시간 등을 받는다. 없는 영상이면 NOT_FOUND
    public void fetchVideo(@NonNull String videoId, @NonNull Callback<Video> callback) {
        fetchVideos(Collections.singletonList(videoId), new Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> videos) {
                if (videos.isEmpty()) {
                    callback.onError(new YouTubeException(YouTubeException.Reason.NOT_FOUND,
                            "영상을 찾을 수 없습니다: " + videoId));
                } else {
                    callback.onSuccess(videos.get(0));
                }
            }

            @Override
            public void onError(YouTubeException error) {
                callback.onError(error);
            }
        });
    }

    // 여러 영상을 한 번에 받는다. 50개마다 요청 1번(1유닛). 없는 영상은 결과에서 빠진다
    public void fetchVideos(@NonNull List<String> videoIds, @NonNull Callback<List<Video>> callback) {
        executor.execute(() -> {
            try {
                List<Video> videos = fetchVideosBlocking(videoIds);
                mainHandler.post(() -> callback.onSuccess(videos));
            } catch (YouTubeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private List<Video> fetchVideosBlocking(List<String> videoIds) throws YouTubeException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new YouTubeException(YouTubeException.Reason.MISSING_API_KEY,
                    "local.properties에 YOUTUBE_API_KEY가 없습니다");
        }
        List<Video> videos = new ArrayList<>();
        Timestamp fetchedAt = Timestamp.now();
        for (int start = 0; start < videoIds.size(); start += MAX_IDS_PER_REQUEST) {
            List<String> chunk = videoIds.subList(start, Math.min(start + MAX_IDS_PER_REQUEST, videoIds.size()));
            String url = Uri.parse(VIDEOS_URL).buildUpon()
                    .appendQueryParameter("part", "snippet,contentDetails")
                    .appendQueryParameter("id", TextUtils.join(",", chunk))
                    .appendQueryParameter("fields", VIDEO_FIELDS)
                    .appendQueryParameter("key", apiKey)
                    .build()
                    .toString();
            videos.addAll(YouTubeVideoParser.parse(get(url), fetchedAt));
        }
        return videos;
    }

    private static JSONObject get(String url) throws YouTubeException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : read(stream);
            if (code >= 400) {
                throw errorFrom(code, body);
            }
            return new JSONObject(body);
        } catch (IOException e) {
            throw new YouTubeException(YouTubeException.Reason.NETWORK, "인터넷 연결을 확인해 주세요", e);
        } catch (JSONException e) {
            throw new YouTubeException(YouTubeException.Reason.UNKNOWN, "YouTube 응답 형식이 올바르지 않습니다", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    // 오류 응답 예: {"error":{"code":403,"message":"...","errors":[{"reason":"quotaExceeded"}]}}
    private static YouTubeException errorFrom(int code, String body) {
        String message = "YouTube 오류 " + code;
        StringBuilder reasons = new StringBuilder();
        try {
            JSONObject error = new JSONObject(body).getJSONObject("error");
            message = error.optString("message", message);
            appendReasons(reasons, error.optJSONArray("errors"));
            appendReasons(reasons, error.optJSONArray("details"));
        } catch (JSONException ignored) {
            // 본문이 JSON이 아니면 상태 코드만으로 판단
        }
        String all = reasons.toString();
        if (all.contains("quotaExceeded") || all.contains("dailyLimitExceeded")) {
            return new YouTubeException(YouTubeException.Reason.QUOTA_EXCEEDED, message);
        }
        if (code == 400 && all.contains("API_KEY")
                || code == 403 && (all.contains("accessNotConfigured") || all.contains("SERVICE_DISABLED")
                || all.contains("API_KEY") || all.contains("forbidden") || all.contains("Blocked"))) {
            return new YouTubeException(YouTubeException.Reason.KEY_REJECTED, message);
        }
        return new YouTubeException(YouTubeException.Reason.UNKNOWN, message);
    }

    private static void appendReasons(StringBuilder out, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                out.append(item.optString("reason")).append(' ');
            }
        }
    }
}
