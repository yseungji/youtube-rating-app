package com.example.viewit.data.model;

// Firestore 컬렉션 이름. 문자열을 직접 쓰지 말고 이 상수를 사용한다. (docs/FIRESTORE_SCHEMA.md)
public final class FirestorePaths {

    public static final String USERS = "users";
    public static final String USERNAMES = "usernames";
    public static final String CATEGORIES = "categories";
    public static final String VIDEOS = "videos";
    public static final String RATINGS = "ratings";
    public static final String FOLLOWS = "follows";

    // users/{uid} 아래 하위 컬렉션
    public static final String SETTINGS = "settings";
    public static final String INTEGRATIONS = "integrations";
    public static final String WATCH_HISTORY = "watchHistory";
    public static final String WATCH_LATER = "watchLater";
    public static final String BLOCKED_USERS = "blockedUsers";
    public static final String RATING_READ_STATES = "ratingReadStates";

    // ratings/{ratingId} 아래 하위 컬렉션
    public static final String LIKES = "likes";
    public static final String COMMENTS = "comments";

    private FirestorePaths() {
    }
}
