package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;

// 프로필의 "나중에 볼 영상(보관함)" 서버 데이터 저장·조회·삭제 (5.4).
// 경로: users/{uid}/watchLater/{videoId} (docs/FIRESTORE_SCHEMA.md)
// 영상 제목·썸네일은 videos 컬렉션에서 읽으므로 중복 저장하지 않고 createdAt만 기록한다.
public class WatchLaterRepository {
    private static final String TAG = "WatchLaterRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 현재 로그인 사용자가 이 영상을 보관함에 담았는지 확인한다.
    public Task<Boolean> isSaved(@NonNull String videoId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forResult(Boolean.FALSE);
        }
        final String trimmedVideoId = videoId.trim();
        if (trimmedVideoId.isEmpty() || trimmedVideoId.contains("/")) {
            return Tasks.forException(new IllegalArgumentException("영상 ID를 확인하세요."));
        }
        return watchLaterDoc(user.getUid(), trimmedVideoId)
                .get(Source.SERVER)
                .continueWith(new Continuation<DocumentSnapshot, Boolean>() {
                    @Override
                    public Boolean then(@NonNull Task<DocumentSnapshot> task) {
                        DocumentSnapshot snapshot = task.getResult();
                        return snapshot != null && snapshot.exists();
                    }
                });
    }

    // 나중에 보기 추가: users/{uid}/watchLater/{videoId} 문서를 생성(또는 시각 갱신)한다.
    public Task<Void> saveWatchLater(@NonNull String videoId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        }
        final String trimmedVideoId = videoId.trim();
        if (trimmedVideoId.isEmpty() || trimmedVideoId.contains("/")) {
            return Tasks.forException(new IllegalArgumentException("영상 ID를 확인하세요."));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("createdAt", FieldValue.serverTimestamp());

        Log.d(TAG, "나중에 보기 저장 요청: videoId=" + trimmedVideoId);
        return watchLaterDoc(user.getUid(), trimmedVideoId)
                .set(data)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG, "나중에 보기 저장 성공: videoId=" + trimmedVideoId);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        Log.e(TAG, "나중에 보기 저장 실패: videoId=" + trimmedVideoId, error);
                    }
                });
    }

    // 나중에 보기 취소: users/{uid}/watchLater/{videoId} 문서를 삭제한다.
    public Task<Void> removeWatchLater(@NonNull String videoId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        }
        final String trimmedVideoId = videoId.trim();
        if (trimmedVideoId.isEmpty() || trimmedVideoId.contains("/")) {
            return Tasks.forException(new IllegalArgumentException("영상 ID를 확인하세요."));
        }

        Log.d(TAG, "나중에 보기 삭제 요청: videoId=" + trimmedVideoId);
        return watchLaterDoc(user.getUid(), trimmedVideoId)
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG, "나중에 보기 삭제 성공: videoId=" + trimmedVideoId);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        Log.e(TAG, "나중에 보기 삭제 실패: videoId=" + trimmedVideoId, error);
                    }
                });
    }

    private DocumentReference watchLaterDoc(@NonNull String uid, @NonNull String videoId) {
        return db.collection(FirestorePaths.USERS)
                .document(uid)
                .collection(FirestorePaths.WATCH_LATER)
                .document(videoId);
    }
}
