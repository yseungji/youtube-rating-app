package com.example.viewit.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

// ratings/{uid}_{videoId} 문서. 사용자 한 명이 영상 하나에 평가 하나만 쓰고, 이후에는 같은 문서를 수정한다.
public class Rating {

    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 5;
    public static final String VISIBILITY_PUBLIC = "public";
    public static final String VISIBILITY_PRIVATE = "private";

    @DocumentId
    private String id;                  // 문서 ID({uid}_{videoId}). 필드로는 저장하지 않음
    private String userId;              // 작성자 UID
    private String videoId;             // 영상 ID
    private String categoryId;          // 영상의 카테고리 ID (videos.categoryId와 같게 유지)
    private long score;                 // 1~5 정수. 0점은 저장하지 않는다
    private String comment = "";        // 선택 한줄평, 미입력 시 빈 문자열
    private String visibility = VISIBILITY_PUBLIC; // public 또는 private
    private long likeCount;             // 좋아요 수, 새 평가는 0
    private long commentCount;          // 댓글 수, 새 평가는 0
    @ServerTimestamp
    private Timestamp createdAt;        // 최초 작성 시각 (새 평가 판단 기준)
    @ServerTimestamp
    private Timestamp updatedAt;        // 마지막 수정 시각

    public Rating() {
    }

    // 평가 문서 ID. Firebase UID에는 밑줄이 없어서, YouTube 영상 ID에 밑줄이 있어도 첫 밑줄로 구분된다.
    public static String documentId(String uid, String videoId) {
        return uid + "_" + videoId;
    }

    public static boolean isValidScore(long score) {
        return score >= MIN_SCORE && score <= MAX_SCORE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
