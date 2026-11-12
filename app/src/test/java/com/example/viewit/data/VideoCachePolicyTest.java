package com.example.viewit.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.viewit.data.model.Video;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// 4.1 영상 정보 30일 보관 기준 확인
public class VideoCachePolicyTest {

    private static final long NOW = 1_800_000_000_000L;

    private static Video videoFetchedDaysAgo(double days) {
        Video video = new Video();
        video.setTitle("제목");
        video.setFetchedAt(new Timestamp(new Date(NOW - (long) (days * TimeUnit.DAYS.toMillis(1)))));
        return video;
    }

    @Test
    public void within30Days_isFresh() {
        assertTrue(VideoCachePolicy.isFresh(videoFetchedDaysAgo(0), NOW));
        assertTrue(VideoCachePolicy.isFresh(videoFetchedDaysAgo(29.9), NOW));
    }

    @Test
    public void after30Days_isNotFresh() {
        assertFalse(VideoCachePolicy.isFresh(videoFetchedDaysAgo(30), NOW));
        assertFalse(VideoCachePolicy.isFresh(videoFetchedDaysAgo(31), NOW));
    }

    @Test
    public void missingInfo_isNotFresh() {
        assertFalse(VideoCachePolicy.isFresh(null, NOW));
        Video noFetchedAt = new Video();
        noFetchedAt.setTitle("제목");
        assertFalse(VideoCachePolicy.isFresh(noFetchedAt, NOW));
        Video statsOnly = videoFetchedDaysAgo(1);
        statsOnly.setTitle(null); // 정보 필드가 지워지고 집계값만 남은 문서
        assertFalse(VideoCachePolicy.isFresh(statsOnly, NOW));
    }

    @Test
    public void infoFields_doNotIncludeRatingStats() {
        Map<String, Object> fields = VideoCachePolicy.infoFields(videoFetchedDaysAgo(0));
        assertEquals(VideoCachePolicy.INFO_FIELDS.size(), fields.size());
        assertTrue(fields.keySet().containsAll(VideoCachePolicy.INFO_FIELDS));
        assertFalse(fields.containsKey("publicRatingCount"));
        assertFalse(fields.containsKey("ratingAverage"));
        assertFalse(fields.containsKey("ratingDistribution"));
        assertFalse(fields.containsKey("videoId"));
    }

    @Test
    public void copyStats_keepsRatingStatsOfSavedDocument() {
        Video saved = new Video();
        saved.setPublicRatingCount(3);
        saved.setRatingSum(12);
        saved.setRatingAverage(4.0);
        Map<String, Long> distribution = new HashMap<>();
        distribution.put("s4", 3L);
        saved.setRatingDistribution(distribution);

        Video fetched = videoFetchedDaysAgo(0);
        VideoCachePolicy.copyStats(saved, fetched);
        assertEquals(3, fetched.getPublicRatingCount());
        assertEquals(4.0, fetched.getRatingAverage(), 0.0001);
        assertEquals(Long.valueOf(3), fetched.getRatingDistribution().get("s4"));
    }

    @Test
    public void videoIdShape() {
        assertTrue(VideoCachePolicy.isValidVideoId("jNQXAC9IVRw"));
        assertTrue(VideoCachePolicy.isValidVideoId("a_b-C1"));
        assertFalse(VideoCachePolicy.isValidVideoId(""));
        assertFalse(VideoCachePolicy.isValidVideoId(null));
        assertFalse(VideoCachePolicy.isValidVideoId("a/b"));
        assertFalse(VideoCachePolicy.isValidVideoId("abc def"));
    }
}
