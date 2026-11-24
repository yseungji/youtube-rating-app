package com.example.viewit.data.model;

import java.util.Date;

// 평가한 영상 목록 한 줄 (6.1). 평가와 영상 정보를 합쳐 화면에서만 쓴다.
public class RatedVideoItem {

    private final String videoId;
    private final String categoryId;
    private final String title;          // 영상 제목 (영상 정보를 못 받으면 안내 문구)
    private final String thumbnailUrl;
    private final String meta;           // "★★★★☆ 4점 · 3일 전" 같은 한 줄
    private final long score;
    private final Date ratedAt;          // 평가한 시각 (최신순 정렬)

    public RatedVideoItem(String videoId, String categoryId, String title, String thumbnailUrl,
                          String meta, long score, Date ratedAt) {
        this.videoId = videoId;
        this.categoryId = categoryId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.meta = meta;
        this.score = score;
        this.ratedAt = ratedAt;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getTitle() {
        return title;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getMeta() {
        return meta;
    }

    public long getScore() {
        return score;
    }

    public Date getRatedAt() {
        return ratedAt;
    }
}
