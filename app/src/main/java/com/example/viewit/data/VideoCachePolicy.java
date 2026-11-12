package com.example.viewit.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.viewit.data.model.Video;
import com.google.firebase.Timestamp;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// videos 문서에 저장한 YouTube 영상 정보의 보관 기준 (4.1).
// YouTube API 정책상 받아 온 영상 정보는 30일 안에 새로 받아야 하므로, 수집 시각(fetchedAt)이 30일 넘으면 다시 받는다.
public final class VideoCachePolicy {

    public static final int MAX_AGE_DAYS = 30;
    private static final long MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(MAX_AGE_DAYS);

    // YouTube에서 받아 저장하는 필드 이름. 평가 집계 필드(publicRatingCount 등)는 여기서 다루지 않는다
    public static final List<String> INFO_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "title", "channelId", "channelTitle", "thumbnailUrl", "categoryId",
            "durationSeconds", "publishedAt", "fetchedAt"));

    private VideoCachePolicy() {
    }

    // 저장된 정보를 그대로 써도 되는지. 정보가 없거나(제목 없음) 30일이 지났으면 false
    public static boolean isFresh(@Nullable Video video, long nowMillis) {
        if (video == null || video.getTitle() == null) {
            return false;
        }
        Timestamp fetchedAt = video.getFetchedAt();
        if (fetchedAt == null) {
            return false;
        }
        return nowMillis - fetchedAt.toDate().getTime() < MAX_AGE_MILLIS;
    }

    // Firestore에 merge로 쓸 영상 정보 필드. 평가 집계 필드는 넣지 않아 기존 집계값을 덮어쓰지 않는다
    @NonNull
    public static Map<String, Object> infoFields(@NonNull Video video) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", video.getTitle());
        fields.put("channelId", video.getChannelId());
        fields.put("channelTitle", video.getChannelTitle());
        fields.put("thumbnailUrl", video.getThumbnailUrl());
        fields.put("categoryId", video.getCategoryId());
        fields.put("durationSeconds", video.getDurationSeconds());
        fields.put("publishedAt", video.getPublishedAt());
        fields.put("fetchedAt", video.getFetchedAt());
        return fields;
    }

    // 새로 받은 영상 정보에 기존 문서의 평가 집계값을 옮겨 담는다 (화면에 평균 별점을 함께 보여 주기 위해)
    public static void copyStats(@Nullable Video from, @NonNull Video to) {
        if (from == null) {
            return;
        }
        to.setPublicRatingCount(from.getPublicRatingCount());
        to.setRatingSum(from.getRatingSum());
        to.setRatingAverage(from.getRatingAverage());
        to.setRatingDistribution(new HashMap<>(from.getRatingDistribution()));
        to.setStatsUpdatedAt(from.getStatsUpdatedAt());
    }

    // YouTube 영상 ID 모양인지 (영문·숫자·_·-). Firestore 문서 경로로 써도 안전한지 확인용
    public static boolean isValidVideoId(@Nullable String videoId) {
        return videoId != null && videoId.matches("[A-Za-z0-9_-]{1,64}");
    }
}
