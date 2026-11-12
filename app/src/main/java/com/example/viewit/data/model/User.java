package com.example.viewit.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// users/{uid} 문서. 다른 사용자에게도 보이는 공개 프로필이라 이메일·토큰은 넣지 않는다.
public class User {

    @DocumentId
    private String uid;                 // 문서 ID(Firebase Authentication UID). 필드로는 저장하지 않음
    private String username;            // 사용자 아이디, 영문·숫자·밑줄 3~20자, 소문자
    private String displayName;         // 표시 이름
    private String photoUrl;            // 프로필 사진 URL(Storage profileImages/{uid}.jpg), 없으면 빈 문자열
    private String introduction;        // 자기소개, 선택·최대 40자
    private List<String> categoryIds = new ArrayList<>();                   // 관심 카테고리, 최소 3개
    private long publicRatingCount;     // 공개 평가 수 (집계값)
    private Map<String, Long> publicRatingCountsByCategory = new HashMap<>(); // 카테고리별 공개 평가 수 (집계값)
    private long followerCount;         // 팔로워 수 (집계값)
    private long followingCount;        // 팔로잉 수 (집계값)
    @ServerTimestamp
    private Timestamp createdAt;        // 가입 시각
    @ServerTimestamp
    private Timestamp updatedAt;        // 프로필 수정 시각

    // Firestore가 문서를 객체로 바꿀 때 필요한 빈 생성자
    public User() {
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public List<String> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<String> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public long getPublicRatingCount() {
        return publicRatingCount;
    }

    public void setPublicRatingCount(long publicRatingCount) {
        this.publicRatingCount = publicRatingCount;
    }

    public Map<String, Long> getPublicRatingCountsByCategory() {
        return publicRatingCountsByCategory;
    }

    public void setPublicRatingCountsByCategory(Map<String, Long> publicRatingCountsByCategory) {
        this.publicRatingCountsByCategory = publicRatingCountsByCategory;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long followerCount) {
        this.followerCount = followerCount;
    }

    public long getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(long followingCount) {
        this.followingCount = followingCount;
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
