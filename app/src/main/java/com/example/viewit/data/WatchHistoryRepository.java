package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.viewit.data.model.FirestorePaths;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 앱에서 본 영상(시청 기록) 저장·조회 (5.1).
// 경로: users/{uid}/watchHistory/{videoId} (docs/FIRESTORE_SCHEMA.md)
// 영상 제목·썸네일은 videos 컬렉션에서 읽으므로 중복 저장하지 않고 lastWatchedAt만 기록한다.
public class WatchHistoryRepository {
    private static final String TAG = "WatchHistoryRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 시청 기록 1건 (문서 ID = videoId, 필드 = lastWatchedAt).
    public static class WatchHistoryEntry {
        private final String videoId;
        @Nullable
        private final Timestamp lastWatchedAt;

        public WatchHistoryEntry(@NonNull String videoId, @Nullable Timestamp lastWatchedAt) {
            this.videoId = videoId;
            this.lastWatchedAt = lastWatchedAt;
        }

        @NonNull
        public String getVideoId() {
            return videoId;
        }

        @Nullable
        public Timestamp getLastWatchedAt() {
            return lastWatchedAt;
        }
    }

    // 영상 재생 시작 시 호출한다. 같은 영상을 다시 재생하면 lastWatchedAt을 최신 서버 시각으로 갱신한다.
    public Task<Void> recordWatch(@NonNull String videoId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "로그인 사용자 없음: 시청 기록 저장 건너뜀");
            return Tasks.forResult(null);
        }
        final String trimmedVideoId = videoId.trim();
        if (trimmedVideoId.isEmpty() || trimmedVideoId.contains("/")) {
            return Tasks.forException(new IllegalArgumentException("영상 ID를 확인하세요."));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("lastWatchedAt", FieldValue.serverTimestamp());

        Log.d(TAG, "시청 기록 저장 요청: videoId=" + trimmedVideoId);
        return watchHistoryDoc(user.getUid(), trimmedVideoId)
                .set(data)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG, "시청 기록 저장 성공: videoId=" + trimmedVideoId);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        Log.e(TAG, "시청 기록 저장 실패: videoId=" + trimmedVideoId, error);
                    }
                });
    }

    // 현재 로그인 사용자의 최근 본 영상 목록을 최근 시청순(lastWatchedAt 내림차순)으로 조회한다.
    public Task<List<WatchHistoryEntry>> loadRecentWatches(int limit) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Log.d(TAG, "시청 기록 조회 시작: limit=" + safeLimit);
        return db.collection(FirestorePaths.USERS)
                .document(user.getUid())
                .collection(FirestorePaths.WATCH_HISTORY)
                .orderBy("lastWatchedAt", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .continueWith(new Continuation<QuerySnapshot, List<WatchHistoryEntry>>() {
                    @Override
                    public List<WatchHistoryEntry> then(@NonNull Task<QuerySnapshot> task) {
                        QuerySnapshot snapshot = task.getResult();
                        List<WatchHistoryEntry> entries = new ArrayList<>();
                        if (snapshot != null) {
                            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                entries.add(new WatchHistoryEntry(
                                        doc.getId(), doc.getTimestamp("lastWatchedAt")));
                            }
                        }
                        Log.d(TAG, "시청 기록 조회 성공: count=" + entries.size());
                        return entries;
                    }
                });
    }

    // 6.3 YouTube 계정 연동(users/{uid}/integrations/youtube) 여부를 확인한다.
    public Task<Boolean> isYouTubeConnected() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forResult(Boolean.FALSE);
        }
        return db.collection(FirestorePaths.USERS)
                .document(user.getUid())
                .collection(FirestorePaths.INTEGRATIONS)
                .document("youtube")
                .get()
                .continueWith(new Continuation<DocumentSnapshot, Boolean>() {
                    @Override
                    public Boolean then(@NonNull Task<DocumentSnapshot> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "YouTube 연동 상태 확인 실패", task.getException());
                            return Boolean.FALSE;
                        }
                        DocumentSnapshot doc = task.getResult();
                        return doc != null && Boolean.TRUE.equals(doc.getBoolean("connected"));
                    }
                });
    }

    private DocumentReference watchHistoryDoc(@NonNull String uid, @NonNull String videoId) {
        return db.collection(FirestorePaths.USERS)
                .document(uid)
                .collection(FirestorePaths.WATCH_HISTORY)
                .document(videoId);
    }
}
