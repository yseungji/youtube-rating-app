package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.BlockedUser;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Follow;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 사용자 차단 (6.3). users/{내 uid}/blockedUsers/{상대 uid} 문서 하나가 "내가 상대를 차단함"이다.
//
// 차단하면:
//   - 서로의 팔로우를 둘 다 끊는다 (내 팔로우 + 상대가 나를 팔로우한 것)
//   - 상대는 나를 다시 팔로우할 수 없다 (firestore.rules follows create에서 막음)
//   - 내 화면에서 상대를 숨긴다: 상대 프로필은 차단 안내만, 팔로워·팔로잉 목록과 추천에서 빠짐
// 한계: 차단 목록은 본인만 읽으므로, 상대가 내 공개 평가를 읽는 것까지 규칙으로 막지는 못한다.
//
// 다른 화면(피드·영상 상세·댓글)에서 차단한 사람을 거를 때:
//   new BlockRepository().loadBlockedIds(myUid, callback) → 받은 Set에 작성자 uid가 있으면 목록에서 뺀다.
public class BlockRepository {

    private static final String TAG = "BlockRepository";
    private static final int MAX_IDS_PER_QUERY = 30;   // Firestore whereIn 한도

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ---------- 차단 / 해제 ----------

    // 차단: 있는 팔로우 문서만 골라 지우고(없는 문서를 지우면 규칙이 거절) 차단 문서를 만든다. 한 batch.
    public void block(@NonNull String myUid, @NonNull String targetUid, @NonNull Callback<Void> callback) {
        if (myUid.equals(targetUid)) {
            callback.onError(new IllegalArgumentException("cannot block yourself"));
            return;
        }
        DocumentReference myFollow = followRef(myUid, targetUid);
        DocumentReference theirFollow = followRef(targetUid, myUid);
        Task<DocumentSnapshot> myFollowTask = myFollow.get();
        Task<DocumentSnapshot> theirFollowTask = theirFollow.get();

        Tasks.whenAllSuccess(myFollowTask, theirFollowTask)
            .continueWithTask(task -> {
                Map<String, Object> block = new HashMap<>();
                block.put("createdAt", FieldValue.serverTimestamp());

                WriteBatch batch = db.batch();
                batch.set(blockRef(myUid, targetUid), block);
                if (myFollowTask.getResult().exists()) {
                    batch.delete(myFollow);
                    batch.delete(db.collection(FirestorePaths.USERS).document(myUid)
                        .collection(FirestorePaths.RATING_READ_STATES).document(targetUid));
                }
                if (theirFollowTask.getResult().exists()) {
                    batch.delete(theirFollow);
                }
                return batch.commit();
            })
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(e -> {
                Log.w(TAG, "block failed", e);
                callback.onError(e);
            });
    }

    // 차단 해제: 차단 문서만 지운다. 끊긴 팔로우는 되살리지 않는다.
    public void unblock(@NonNull String myUid, @NonNull String targetUid, @NonNull Callback<Void> callback) {
        blockRef(myUid, targetUid).delete()
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(e -> {
                Log.w(TAG, "unblock failed", e);
                callback.onError(e);
            });
    }

    // ---------- 조회 ----------

    // 내가 target을 차단했는지
    public void isBlocked(@NonNull String myUid, @NonNull String targetUid, @NonNull Callback<Boolean> callback) {
        blockRef(myUid, targetUid).get()
            .addOnSuccessListener(doc -> callback.onSuccess(doc.exists()))
            .addOnFailureListener(callback::onError);
    }

    // 내가 차단한 사람 수 (설정 화면 "N명")
    public void countBlocked(@NonNull String myUid, @NonNull Callback<Long> callback) {
        blockedCollection(myUid).count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener(result -> callback.onSuccess(result.getCount()))
            .addOnFailureListener(callback::onError);
    }

    // 내가 차단한 사람들의 uid (다른 목록에서 거를 때)
    public void loadBlockedIds(@NonNull String myUid, @NonNull Callback<Set<String>> callback) {
        blockedIdsTask(myUid)
            .addOnSuccessListener(callback::onSuccess)
            .addOnFailureListener(callback::onError);
    }

    // FollowRepository가 목록을 만들 때 같이 기다리는 용도
    Task<Set<String>> blockedIdsTask(@NonNull String myUid) {
        return blockedCollection(myUid).get().continueWith(task -> {
            Set<String> ids = new HashSet<>();
            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                ids.add(doc.getId());
            }
            return ids;
        });
    }

    // 차단한 사용자 목록 (최근 차단순). 탈퇴해서 users 문서가 없는 사람은 뺀다.
    public void loadBlockedUsers(@NonNull String myUid, @NonNull Callback<List<BlockedUser>> callback) {
        blockedCollection(myUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .continueWithTask(task -> {
                List<String> uids = new ArrayList<>();
                Map<String, Timestamp> blockedAt = new HashMap<>();
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    uids.add(doc.getId());
                    blockedAt.put(doc.getId(), doc.getTimestamp("createdAt"));
                }
                return loadUsers(uids).continueWith(usersTask -> {
                    Map<String, DocumentSnapshot> users = usersTask.getResult();
                    List<BlockedUser> items = new ArrayList<>();
                    for (String uid : uids) {
                        DocumentSnapshot user = users.get(uid);
                        if (user == null || !user.exists()) {
                            continue;
                        }
                        Timestamp at = blockedAt.get(uid);
                        items.add(new BlockedUser(uid,
                            user.getString("displayName"),
                            user.getString("username"),
                            user.getString("photoUrl"),
                            at == null ? null : at.toDate()));
                    }
                    return items;
                });
            })
            .addOnSuccessListener(callback::onSuccess)
            .addOnFailureListener(e -> {
                Log.w(TAG, "blocked list failed", e);
                callback.onError(e);
            });
    }

    // users 문서를 30개씩 나눠 읽는다
    private Task<Map<String, DocumentSnapshot>> loadUsers(List<String> uids) {
        if (uids.isEmpty()) {
            return Tasks.forResult(Collections.emptyMap());
        }
        List<Task<QuerySnapshot>> chunks = new ArrayList<>();
        for (int start = 0; start < uids.size(); start += MAX_IDS_PER_QUERY) {
            List<String> chunk = uids.subList(start, Math.min(start + MAX_IDS_PER_QUERY, uids.size()));
            chunks.add(db.collection(FirestorePaths.USERS)
                .whereIn(FieldPath.documentId(), new ArrayList<>(chunk))
                .get());
        }
        return Tasks.whenAllSuccess(chunks).continueWith(task -> {
            Map<String, DocumentSnapshot> users = new HashMap<>();
            for (Object result : task.getResult()) {
                for (DocumentSnapshot doc : ((QuerySnapshot) result).getDocuments()) {
                    users.put(doc.getId(), doc);
                }
            }
            return users;
        });
    }

    private CollectionReference blockedCollection(String myUid) {
        return db.collection(FirestorePaths.USERS).document(myUid).collection(FirestorePaths.BLOCKED_USERS);
    }

    private DocumentReference blockRef(String myUid, String targetUid) {
        return blockedCollection(myUid).document(targetUid);
    }

    private DocumentReference followRef(String followerId, String followingId) {
        return db.collection(FirestorePaths.FOLLOWS).document(Follow.documentId(followerId, followingId));
    }
}
