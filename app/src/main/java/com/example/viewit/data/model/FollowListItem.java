package com.example.viewit.data.model;

// 팔로워·팔로잉 목록 한 줄에 필요한 값 (6.2). Firestore에 저장하지 않고 화면에서만 쓴다.
public class FollowListItem {

    private final String uid;
    private final String displayName;
    private final String username;
    private final String photoUrl;
    private final long publicRatingCount;   // 그 사람의 공개 평가 수
    private boolean followedByMe;           // 내가 이 사람을 팔로우하고 있는지 (버튼 모양)
    private long newRatingCount;            // 마지막으로 확인한 뒤 새로 쓴 공개 평가 수 (내 팔로잉 목록에서만)

    public FollowListItem(String uid, String displayName, String username, String photoUrl,
                          long publicRatingCount, boolean followedByMe) {
        this.uid = uid;
        this.displayName = displayName;
        this.username = username;
        this.photoUrl = photoUrl;
        this.publicRatingCount = publicRatingCount;
        this.followedByMe = followedByMe;
    }

    public String getUid() {
        return uid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public long getPublicRatingCount() {
        return publicRatingCount;
    }

    public boolean isFollowedByMe() {
        return followedByMe;
    }

    public void setFollowedByMe(boolean followedByMe) {
        this.followedByMe = followedByMe;
    }

    public long getNewRatingCount() {
        return newRatingCount;
    }

    public void setNewRatingCount(long newRatingCount) {
        this.newRatingCount = newRatingCount;
    }
}
