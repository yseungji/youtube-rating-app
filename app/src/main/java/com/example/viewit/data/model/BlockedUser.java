package com.example.viewit.data.model;

import androidx.annotation.Nullable;

import java.util.Date;

// 차단한 사용자 목록 한 줄 (6.3). users/{내 uid}/blockedUsers/{uid} + 그 사람의 users 문서
public class BlockedUser {
    private final String uid;
    private final String displayName;
    private final String username;
    private final String photoUrl;
    private final Date blockedAt;

    public BlockedUser(String uid, String displayName, String username, String photoUrl, @Nullable Date blockedAt) {
        this.uid = uid;
        this.displayName = displayName;
        this.username = username;
        this.photoUrl = photoUrl;
        this.blockedAt = blockedAt;
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

    @Nullable
    public Date getBlockedAt() {
        return blockedAt;
    }
}
