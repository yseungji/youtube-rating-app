package com.example.viewit.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

// follows/{followerId}_{followingId} 문서. 자기 자신은 팔로우하지 않는다.
public class Follow {

    @DocumentId
    private String id;                  // 문서 ID({followerId}_{followingId}). 필드로는 저장하지 않음
    private String followerId;          // 팔로우하는 사용자 UID
    private String followingId;         // 팔로우받는 사용자 UID
    @ServerTimestamp
    private Timestamp createdAt;        // 팔로우 시각

    public Follow() {
    }

    // 팔로우 문서 ID. Firebase UID에는 밑줄이 없어서 두 UID를 밑줄 하나로 이어도 구분된다.
    public static String documentId(String followerId, String followingId) {
        return followerId + "_" + followingId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFollowerId() {
        return followerId;
    }

    public void setFollowerId(String followerId) {
        this.followerId = followerId;
    }

    public String getFollowingId() {
        return followingId;
    }

    public void setFollowingId(String followingId) {
        this.followingId = followingId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
