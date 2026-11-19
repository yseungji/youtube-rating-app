package com.example.viewit.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

// ratings/{ratingId}/comments 하위 컬렉션 문서.
public class Comment {

    @DocumentId
    private String id;
    private String ratingId;
    private String userId;
    private String authorName;
    private String authorUsername;
    private String parentId;         // 최상위 댓글이면 null, 1단 답글이면 최상위 댓글 ID
    private String targetUsername;   // 답글 언급 대상 아이디 (예: "doyeob", 없으면 null)
    private String content = "";
    private long likeCount;
    @com.google.firebase.firestore.Exclude
    private boolean likedByCurrentUser; // 현재 로그인 유저의 댓글 좋아요 여부 (Firestore 저장 제외)
    @ServerTimestamp
    private Timestamp createdAt;

    public Comment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRatingId() {
        return ratingId;
    }

    public void setRatingId(String ratingId) {
        this.ratingId = ratingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public boolean isLikedByCurrentUser() {
        return likedByCurrentUser;
    }

    public void setLikedByCurrentUser(boolean likedByCurrentUser) {
        this.likedByCurrentUser = likedByCurrentUser;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isReply() {
        return parentId != null && !parentId.trim().isEmpty();
    }
}
