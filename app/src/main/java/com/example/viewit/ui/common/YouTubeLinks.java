package com.example.viewit.ui.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.viewit.R;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// YouTube 영상 주소 생성·외부 앱 열기(4.2)와 공유받은 링크의 영상 ID 파싱(5.1)을 담당한다.
// 예: YouTubeLinks.open(context, "jNQXAC9IVRw");
//     YouTubeLinks.extractVideoId("재밌는 영상 https://youtu.be/jNQXAC9IVRw?si=abc");
public final class YouTubeLinks {
    // YouTube 영상 ID는 영문·숫자·'-'·'_' 조합 11자리이다.
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    // 제목 등 일반 문장과 섞인 공유 텍스트에서 http(s) URL 후보를 추출한다.
    private static final Pattern HTTP_URL_PATTERN =
            Pattern.compile("https?://[^\\s<>\"')\\]]+", Pattern.CASE_INSENSITIVE);
    // 스킴(https://) 없이 도메인부터 붙여 넣은 YouTube 링크 후보를 추출한다.
    private static final Pattern BARE_YOUTUBE_URL_PATTERN = Pattern.compile(
            "(?:^|[\\s(<\\[{:\"'])((?:[A-Za-z0-9-]+\\.)*(?:youtube\\.com|youtu\\.be|youtube-nocookie\\.com)/[^\\s<>\"')\\]]+)",
            Pattern.CASE_INSENSITIVE);

    private YouTubeLinks() {
    }

    public static Uri watchUri(@NonNull String videoId) {
        return Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(videoId));
    }

    public static void open(@NonNull Context context, @NonNull String videoId) {
        // 주소만 알려 주면 기기에서 YouTube 주소를 여는 앱(YouTube 앱 또는 브라우저)이 열린다 (암시적 인텐트)
        Intent intent = new Intent(Intent.ACTION_VIEW, watchUri(videoId));
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.video_player_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    // YouTube 공유 텍스트나 URL에서 11자리 videoId를 추출한다. 유효한 YouTube 영상 링크가 아니면 null.
    // 지원 예: youtu.be/ID?si=..., youtube.com/watch?v=ID, m.youtube.com/watch?v=ID,
    //          music.youtube.com/watch?v=ID, youtube.com/shorts/ID, /live/ID, /embed/ID, /v/ID,
    //          제목과 URL이 함께 들어온 공유 텍스트.
    @Nullable
    public static String extractVideoId(@Nullable String sharedText) {
        if (TextUtils.isEmpty(sharedText)) {
            return null;
        }
        String trimmed = sharedText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // 1) http:// 또는 https:// 로 시작하는 URL 후보부터 검사한다.
        Matcher httpMatcher = HTTP_URL_PATTERN.matcher(trimmed);
        while (httpMatcher.find()) {
            String videoId = extractVideoIdFromUrlCandidate(httpMatcher.group());
            if (videoId != null) {
                return videoId;
            }
        }

        // 2) 스킴 없이 도메인만 들어온 경우(예: youtu.be/ID, www.youtube.com/watch?v=ID)를 검사한다.
        Matcher bareMatcher = BARE_YOUTUBE_URL_PATTERN.matcher(trimmed);
        while (bareMatcher.find()) {
            String candidate = bareMatcher.group(1);
            if (candidate != null) {
                String videoId = extractVideoIdFromUrlCandidate("https://" + candidate);
                if (videoId != null) {
                    return videoId;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String extractVideoIdFromUrlCandidate(@Nullable String rawCandidate) {
        if (TextUtils.isEmpty(rawCandidate)) {
            return null;
        }
        String cleaned = stripTrailingPunctuation(rawCandidate);
        if (cleaned.isEmpty()) {
            return null;
        }
        Uri uri = Uri.parse(cleaned);
        return extractVideoIdFromUri(uri);
    }

    @Nullable
    private static String extractVideoIdFromUri(@NonNull Uri uri) {
        String rawHost = uri.getHost();
        if (TextUtils.isEmpty(rawHost)) {
            return null;
        }
        String host = rawHost.toLowerCase(Locale.ROOT);
        List<String> segments = uri.getPathSegments();

        // youtu.be/{videoId}
        if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
            if (!segments.isEmpty() && isValidVideoId(segments.get(0))) {
                return segments.get(0);
            }
            return null;
        }

        boolean isYouTubeHost = host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("youtube-nocookie.com")
                || host.endsWith(".youtube-nocookie.com");
        if (!isYouTubeHost) {
            return null;
        }

        // /watch?v={videoId} (쿼리 순서와 무관하게 v 파라미터 추출)
        String vParam = uri.getQueryParameter("v");
        if (isValidVideoId(vParam)) {
            return vParam;
        }

        // /attribution_link?u=%2Fwatch%3Fv%3D{videoId}...
        if (!segments.isEmpty() && "attribution_link".equalsIgnoreCase(segments.get(0))) {
            String embeddedPath = uri.getQueryParameter("u");
            if (!TextUtils.isEmpty(embeddedPath)) {
                String normalized = embeddedPath.startsWith("http")
                        ? embeddedPath
                        : "https://www.youtube.com" + (embeddedPath.startsWith("/") ? "" : "/") + embeddedPath;
                return extractVideoIdFromUri(Uri.parse(normalized));
            }
        }

        // /shorts/{videoId}, /live/{videoId}, /embed/{videoId}, /v/{videoId}, /e/{videoId}
        if (segments.size() >= 2) {
            String prefix = segments.get(0).toLowerCase(Locale.ROOT);
            if ("shorts".equals(prefix)
                    || "live".equals(prefix)
                    || "embed".equals(prefix)
                    || "v".equals(prefix)
                    || "e".equals(prefix)) {
                String candidateId = segments.get(1);
                if (isValidVideoId(candidateId)) {
                    return candidateId;
                }
            }
        }
        return null;
    }

    private static boolean isValidVideoId(@Nullable String videoId) {
        return videoId != null && VIDEO_ID_PATTERN.matcher(videoId).matches();
    }

    // URL 뒤에 문장부호(.,!?;:등)가 붙어 공유된 경우 끝 문자만 정리한다.
    @NonNull
    private static String stripTrailingPunctuation(@NonNull String url) {
        int end = url.length();
        while (end > 0) {
            char c = url.charAt(end - 1);
            if (c == '.' || c == ',' || c == '!' || c == '?' || c == ';' || c == ':'
                    || c == ')' || c == ']' || c == '}' || c == '\'' || c == '"') {
                end--;
            } else {
                break;
            }
        }
        return url.substring(0, end);
    }
}
