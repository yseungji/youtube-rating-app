package com.example.viewit.data.youtube;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
    private static final String SUBSCRIPTIONS_URL = "https://www.googleapis.com/youtube/v3/subscriptions";
    private static final String CHANNELS_URL = "https://www.googleapis.com/youtube/v3/channels";
    private static final String PLAYLIST_ITEMS_URL = "https://www.googleapis.com/youtube/v3/playlistItems";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    // 필요한 필드만 받아 응답 크기를 줄인다. 사용량(1유닛)은 같다
    private static final String VIDEO_FIELDS =
            "items(id,snippet(title,channelId,channelTitle,categoryId,publishedAt,thumbnails),contentDetails/duration)";
    private static final int MAX_IDS_PER_REQUEST = 50;
    private static final String POPULAR_REGION = "KR";
    private static final int POPULAR_MAX_RESULTS = 30;
    private static final int TIMEOUT_MS = 10_000;

    private static YouTubeApi instance;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    // 구독 채널마다 최신 영상을 받는 요청을 동시에 보낸다 (30번을 차례로 보내면 몇 초 걸림)
    private final ExecutorService channelExecutor = Executors.newFixedThreadPool(6);
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

    // 인기 차트 한 페이지. nextPageToken이 null이면 차트의 끝 (KR 기준 전체 200개, 카테고리마다 더 적음)
    public static final class PopularPage {
        public final List<Video> videos;
        @Nullable
        public final String nextPageToken;

        PopularPage(List<Video> videos, @Nullable String nextPageToken) {
            this.videos = videos;
            this.nextPageToken = nextPageToken;
        }
    }

    // 한국 YouTube 인기 차트를 한 페이지(30개)씩 받는다(4.3 카테고리 인기순). 요청 1번 = 1유닛.
    // categoryId가 null이면 전체 인기 차트, pageToken이 null이면 첫 페이지
    public void fetchPopularVideos(@Nullable String categoryId, @Nullable String pageToken,
                                   @NonNull Callback<PopularPage> callback) {
        executor.execute(() -> {
            try {
                PopularPage page = fetchPopularPageBlocking(categoryId, pageToken);
                mainHandler.post(() -> callback.onSuccess(page));
            } catch (YouTubeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private PopularPage fetchPopularPageBlocking(@Nullable String categoryId, @Nullable String pageToken)
            throws YouTubeException {
        requireApiKey();
        Uri.Builder url = Uri.parse(VIDEOS_URL).buildUpon()
                .appendQueryParameter("part", "snippet,contentDetails")
                .appendQueryParameter("chart", "mostPopular")
                .appendQueryParameter("regionCode", POPULAR_REGION)
                .appendQueryParameter("maxResults", String.valueOf(POPULAR_MAX_RESULTS))
                .appendQueryParameter("fields", "nextPageToken," + VIDEO_FIELDS);
        if (!TextUtils.isEmpty(categoryId)) {
            url.appendQueryParameter("videoCategoryId", categoryId);
        }
        if (!TextUtils.isEmpty(pageToken)) {
            url.appendQueryParameter("pageToken", pageToken);
        }
        url.appendQueryParameter("key", apiKey);
        JSONObject response = get(url.build().toString());
        String next = response.optString("nextPageToken", "");
        return new PopularPage(YouTubeVideoParser.parse(response, Timestamp.now()),
                next.isEmpty() ? null : next);
    }

    // ---------- YouTube 계정 연동(OAuth)이 필요한 호출 (6.3) ----------
    // accessToken은 YouTubeAccountRepository가 Google 권한 창으로 받은 youtube.readonly 토큰.
    // 토큰으로 부르므로 API 키는 쓰지 않는다. 토큰이 만료·취소되었으면 AUTH_REQUIRED

    // 구독 채널의 새 영상을 최신 업로드순으로 받는다. 사용량은 YouTubeLimits 참고
    public void fetchSubscriptionVideos(@NonNull String accessToken, @NonNull Callback<List<Video>> callback) {
        executor.execute(() -> {
            try {
                List<Video> videos = fetchSubscriptionVideosBlocking(accessToken);
                mainHandler.post(() -> callback.onSuccess(videos));
            } catch (YouTubeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // YouTube에서 좋아요한 영상을 최근에 누른 순으로 받는다. 요청 1번 = 1유닛
    public void fetchLikedVideos(@NonNull String accessToken, @NonNull Callback<List<Video>> callback) {
        executor.execute(() -> {
            try {
                String url = Uri.parse(VIDEOS_URL).buildUpon()
                        .appendQueryParameter("part", "snippet,contentDetails")
                        .appendQueryParameter("myRating", "like")
                        .appendQueryParameter("maxResults", String.valueOf(YouTubeLimits.LIKED_VIDEOS))
                        .appendQueryParameter("fields", VIDEO_FIELDS)
                        .build()
                        .toString();
                List<Video> videos = YouTubeVideoParser.parse(get(url, accessToken), Timestamp.now());
                mainHandler.post(() -> callback.onSuccess(videos));
            } catch (YouTubeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // 연동 해제 때 Google에 토큰을 돌려줘 권한을 취소한다. 실패해도(이미 만료 등) 해제는 계속한다
    public void revokeToken(@NonNull String accessToken, @NonNull Runnable done) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(REVOKE_URL).openConnection();
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                byte[] body = ("token=" + Uri.encode(accessToken)).getBytes(StandardCharsets.UTF_8);
                connection.getOutputStream().write(body);
                connection.getResponseCode();
            } catch (IOException ignored) {
                // 권한 취소는 최선만 다한다. 사용자는 Google 계정 설정에서도 취소할 수 있다
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            mainHandler.post(done);
        });
    }

    // 1) 내 구독 채널 → 2) 채널별 업로드 재생목록 → 3) 재생목록마다 최신 영상 → 4) 영상 정보
    private List<Video> fetchSubscriptionVideosBlocking(String token) throws YouTubeException {
        // 1) 구독 채널 ID (관련성 순 앞에서 SUBSCRIPTION_CHANNELS개). 1유닛
        String subscriptionsUrl = Uri.parse(SUBSCRIPTIONS_URL).buildUpon()
                .appendQueryParameter("part", "snippet")
                .appendQueryParameter("mine", "true")
                .appendQueryParameter("order", "relevance")
                .appendQueryParameter("maxResults", String.valueOf(YouTubeLimits.SUBSCRIPTION_CHANNELS))
                .appendQueryParameter("fields", "items(snippet/resourceId/channelId)")
                .build()
                .toString();
        JSONArray subscriptions = get(subscriptionsUrl, token).optJSONArray("items");
        List<String> channelIds = new ArrayList<>();
        if (subscriptions != null) {
            for (int i = 0; i < subscriptions.length(); i++) {
                JSONObject snippet = subscriptions.optJSONObject(i).optJSONObject("snippet");
                JSONObject resourceId = snippet == null ? null : snippet.optJSONObject("resourceId");
                String channelId = resourceId == null ? "" : resourceId.optString("channelId");
                if (!channelId.isEmpty()) {
                    channelIds.add(channelId);
                }
            }
        }
        if (channelIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2) 채널마다 "업로드한 영상" 재생목록 ID. 50개까지 1유닛
        String channelsUrl = Uri.parse(CHANNELS_URL).buildUpon()
                .appendQueryParameter("part", "contentDetails")
                .appendQueryParameter("id", TextUtils.join(",", channelIds))
                .appendQueryParameter("maxResults", String.valueOf(MAX_IDS_PER_REQUEST))
                .appendQueryParameter("fields", "items(contentDetails/relatedPlaylists/uploads)")
                .build()
                .toString();
        JSONArray channels = get(channelsUrl, token).optJSONArray("items");
        List<String> uploadPlaylists = new ArrayList<>();
        if (channels != null) {
            for (int i = 0; i < channels.length(); i++) {
                JSONObject related = channels.optJSONObject(i).optJSONObject("contentDetails");
                related = related == null ? null : related.optJSONObject("relatedPlaylists");
                String uploads = related == null ? "" : related.optString("uploads");
                if (!uploads.isEmpty()) {
                    uploadPlaylists.add(uploads);
                }
            }
        }

        // 3) 재생목록마다 최신 영상 몇 개. 채널마다 1유닛, 동시에 보낸다
        List<Future<Map<String, String>>> futures = new ArrayList<>();
        for (String playlistId : uploadPlaylists) {
            futures.add(channelExecutor.submit(latestUploads(playlistId, token)));
        }
        Map<String, String> publishedById = new HashMap<>();   // 영상 ID → 업로드 시각(RFC 3339)
        for (Future<Map<String, String>> future : futures) {
            try {
                publishedById.putAll(future.get());
            } catch (ExecutionException e) {
                // 영상이 없는 채널 등은 건너뛰고, 계정·할당량·네트워크 문제는 그대로 알린다
                if (e.getCause() instanceof YouTubeException) {
                    YouTubeException cause = (YouTubeException) e.getCause();
                    if (cause.getReason() != YouTubeException.Reason.UNKNOWN
                            && cause.getReason() != YouTubeException.Reason.NOT_FOUND) {
                        throw cause;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new YouTubeException(YouTubeException.Reason.NETWORK, "요청이 중단되었습니다", e);
            }
        }

        // 업로드 시각은 RFC 3339(UTC, 같은 길이)라 문자열 비교가 곧 시간 비교
        List<String> videoIds = new ArrayList<>(publishedById.keySet());
        Collections.sort(videoIds, (a, b) -> publishedById.get(b).compareTo(publishedById.get(a)));
        if (videoIds.size() > YouTubeLimits.SUBSCRIPTION_FEED_SIZE) {
            videoIds = new ArrayList<>(videoIds.subList(0, YouTubeLimits.SUBSCRIPTION_FEED_SIZE));
        }
        if (videoIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 4) 영상 제목·채널·썸네일·길이. 50개까지 1유닛
        String videosUrl = Uri.parse(VIDEOS_URL).buildUpon()
                .appendQueryParameter("part", "snippet,contentDetails")
                .appendQueryParameter("id", TextUtils.join(",", videoIds))
                .appendQueryParameter("fields", VIDEO_FIELDS)
                .build()
                .toString();
        List<Video> videos = YouTubeVideoParser.parse(get(videosUrl, token), Timestamp.now());
        Collections.sort(videos, new Comparator<Video>() {
            @Override
            public int compare(Video a, Video b) {
                if (a.getPublishedAt() == null || b.getPublishedAt() == null) {
                    return a.getPublishedAt() == null ? (b.getPublishedAt() == null ? 0 : 1) : -1;
                }
                return b.getPublishedAt().compareTo(a.getPublishedAt());
            }
        });
        return videos;
    }

    private Callable<Map<String, String>> latestUploads(String playlistId, String token) {
        return () -> {
            String url = Uri.parse(PLAYLIST_ITEMS_URL).buildUpon()
                    .appendQueryParameter("part", "contentDetails")
                    .appendQueryParameter("playlistId", playlistId)
                    .appendQueryParameter("maxResults", String.valueOf(YouTubeLimits.VIDEOS_PER_CHANNEL))
                    .appendQueryParameter("fields", "items(contentDetails(videoId,videoPublishedAt))")
                    .build()
                    .toString();
            Map<String, String> result = new HashMap<>();
            JSONArray items = get(url, token).optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject details = items.optJSONObject(i).optJSONObject("contentDetails");
                    if (details == null) {
                        continue;
                    }
                    String videoId = details.optString("videoId");
                    String publishedAt = details.optString("videoPublishedAt");   // 비공개 영상은 없음
                    if (!videoId.isEmpty() && !publishedAt.isEmpty()) {
                        result.put(videoId, publishedAt);
                    }
                }
            }
            return result;
        };
    }

    private void requireApiKey() throws YouTubeException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new YouTubeException(YouTubeException.Reason.MISSING_API_KEY,
                    "local.properties에 YOUTUBE_API_KEY가 없습니다");
        }
    }

    private List<Video> fetchVideosBlocking(List<String> videoIds) throws YouTubeException {
        requireApiKey();
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
        return get(url, null);
    }

    // accessToken이 있으면 연동한 사용자 이름으로 요청한다 (Authorization: Bearer)
    private static JSONObject get(String url, @Nullable String accessToken) throws YouTubeException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (accessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
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
        // 토큰 만료·권한 취소(401), 권한 범위 부족(403) → 다시 연동
        if (code == 401 || code == 403 && (all.contains("insufficientPermissions")
                || all.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT"))) {
            return new YouTubeException(YouTubeException.Reason.AUTH_REQUIRED, message);
        }
        if (code == 404) {
            return new YouTubeException(YouTubeException.Reason.NOT_FOUND, message);
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
