package com.example.viewit.data;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

// 영상 상세 "팔로우한 사람의 평가" 필터 (4.2 장주원).
// 1) 내가 팔로우한 사람 UID 목록을 받고 2) 그 사람들이 이 영상에 남긴 공개 평가만 고르는 Query를 만든다.
// Query는 영상 상세의 평가 목록(FirebaseUI 페이징 어댑터)에 그대로 넣는다.
public class FollowingRatingsQuery {

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    // Firestore whereIn 한도. 팔로잉이 30명을 넘으면 최근에 팔로우한 30명까지만 본다 (과제 규모에서는 충분)
    private static final int MAX_FOLLOWING = 30;
    // 팔로잉이 없을 때 결과가 0개인 Query를 만들기 위한 값 (실제 UID와 겹치지 않음)
    private static final String NOBODY = "__nobody__";

    private final FirebaseFirestore db;

    public FollowingRatingsQuery() {
        this(FirebaseFirestore.getInstance());
    }

    FollowingRatingsQuery(FirebaseFirestore db) {
        this.db = db;
    }

    // 내가 팔로우한 사람 UID (최근에 팔로우한 순, 최대 30명)
    public void loadFollowingIds(@NonNull String myUid, @NonNull Callback<List<String>> callback) {
        db.collection(FirestorePaths.FOLLOWS)
                .whereEqualTo("followerId", myUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> {
                        if (a.getTimestamp("createdAt") == null || b.getTimestamp("createdAt") == null) {
                            return 0;
                        }
                        return b.getTimestamp("createdAt").compareTo(a.getTimestamp("createdAt"));
                    });
                    List<String> ids = new ArrayList<>();
                    for (DocumentSnapshot doc : docs) {
                        String id = doc.getString("followingId");
                        if (id != null && ids.size() < MAX_FOLLOWING) {
                            ids.add(id);
                        }
                    }
                    callback.onSuccess(ids);
                })
                .addOnFailureListener(callback::onError);
    }

    // followingIds가 이 영상에 남긴 공개 평가. 비어 있으면 결과가 0개인 Query.
    // 전체 목록과 달리 점수(1~5) whereIn은 넣지 않는다: Firestore는 whereIn 두 개를 곱한 조건 수(5 × 30)를 허용하지 않는다
    @NonNull
    public Query query(@NonNull String videoId, @NonNull List<String> followingIds) {
        Query base = db.collection(FirestorePaths.RATINGS)
                .whereEqualTo("videoId", videoId.trim())
                .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC);
        Query filtered = followingIds.isEmpty()
                ? base.whereEqualTo("userId", NOBODY)
                : base.whereIn("userId", new ArrayList<>(followingIds.subList(0, Math.min(MAX_FOLLOWING, followingIds.size()))));
        return filtered.orderBy(FieldPath.documentId());
    }
}
