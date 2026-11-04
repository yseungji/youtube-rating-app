package com.example.viewit.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

// videos/{videoId} 문서. YouTube 영상 정보와 공개 평가 집계값을 함께 둔다.
public class Video {

    @DocumentId
    private String videoId;             // 문서 ID(YouTube 영상 ID). 필드로는 저장하지 않음
    private String title;               // 영상 제목
    private String channelId;           // YouTube 채널 ID
    private String channelTitle;        // 채널 이름
    private String thumbnailUrl;        // 썸네일 URL
    private String categoryId;          // YouTube 카테고리 ID
    private long durationSeconds;       // 재생 시간, 초
    private Timestamp publishedAt;      // 영상 게시 시각
    private Timestamp fetchedAt;        // 영상 정보 수집 시각 (30일 지나면 다시 받기)
    // 아래는 ratings에서 계산하는 공개 평가 집계값. 일반 사용자가 직접 수정하지 않는다.
    private long publicRatingCount;     // 공개 평가 수
    private long ratingSum;             // 공개 별점 합계
    private double ratingAverage;       // 공개 별점 평균
    private Map<String, Long> ratingDistribution = new HashMap<>(); // 별점 분포, 키 s1~s5
    private Timestamp statsUpdatedAt;   // 집계 갱신 시각

    public Video() {
    }

    // ratingDistribution의 키 이름. 예: distributionKey(4) → "s4"
    public static String distributionKey(int score) {
        return "s" + score;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public void setChannelTitle(String channelTitle) {
        this.channelTitle = channelTitle;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Timestamp getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Timestamp publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Timestamp getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Timestamp fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public long getPublicRatingCount() {
        return publicRatingCount;
    }

    public void setPublicRatingCount(long publicRatingCount) {
        this.publicRatingCount = publicRatingCount;
    }

    public long getRatingSum() {
        return ratingSum;
    }

    public void setRatingSum(long ratingSum) {
        this.ratingSum = ratingSum;
    }

    public double getRatingAverage() {
        return ratingAverage;
    }

    public void setRatingAverage(double ratingAverage) {
        this.ratingAverage = ratingAverage;
    }

    public Map<String, Long> getRatingDistribution() {
        return ratingDistribution;
    }

    public void setRatingDistribution(Map<String, Long> ratingDistribution) {
        this.ratingDistribution = ratingDistribution;
    }

    public Timestamp getStatsUpdatedAt() {
        return statsUpdatedAt;
    }

    public void setStatsUpdatedAt(Timestamp statsUpdatedAt) {
        this.statsUpdatedAt = statsUpdatedAt;
    }
}
