package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Follow;
import com.example.viewit.data.model.FollowListItem;
import com.example.viewit.data.model.Rating;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 팔로우 (6.2). follows/{followerId}_{followingId} 문서 하나가 "follower가 following을 팔로우함"이다.
// 팔로워·팔로잉 수는 users의 숫자 필드를 고치지 않고 follows를 그때그때 센다(count 조회).
// 남의 users 문서를 고칠 권한을 열지 않아도 되고, 숫자가 어긋날 일도 없다.
public class FollowRepository {

    private static final String TAG = "FollowRepository";
    private static final int MAX_IDS_PER_QUERY = 30;    // Firestore whereIn 한도
    private static final int MAX_LIST_SIZE = 100;       // 목록은 최근 100명까지 (시연 규모)
    private static final int MAX_RECOMMEND = 10;        // 온보딩 추천 인원
    private static final int MAX_RECOMMEND_SCAN = 300;  // 추천 계산에 읽는 공개 평가 수 (시연 규모)

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ---------- 팔로우 / 언팔로우 ----------

    // 팔로우: follows 문서 + 새 평가 표시 기준 시각(ratingReadStates)을 함께 만든다. 자기 자신은 팔로우하지 않는다.
    public void follow(@NonNull String myUid, @NonNull String targetUid, @NonNull Callback<Void> callback) {
        if (myUid.equals(targetUid)) {
            callback.onError(new IllegalArgumentException("cannot follow yourself"));
            return;
        }
        Map<String, Object> follow = new HashMap<>();
        follow.put("followerId", myUid);
        follow.put("followingId", targetUid);
        follow.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> readState = new HashMap<>();
        readState.put("lastCheckedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(followRef(myUid, targetUid), follow);
        batch.set(db.collection(FirestorePaths.USERS).document(myUid)
            .collection(FirestorePaths.RATING_READ_STATES).document(targetUid), readState);
        batch.commit()
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(callback::onError);
    }

    // 언팔로우: follows 문서와 새 평가 기준 시각을 지운다.
    public void unfollow(@NonNull String myUid, @NonNull String targetUid, @NonNull Callback<Void> callback) {
        WriteBatch batch = db.batch();
        batch.delete(followRef(myUid, targetUid));
        batch.delete(db.collection(FirestorePaths.USERS).document(myUid)
            .collection(FirestorePaths.RATING_READ_STATES).document(targetUid));
        batch.commit()
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(callback::onError);
    }

    // 내가 target을 팔로우하고 있는지
    public void isFollowing(@NonNull String myUid, @NonNull String targetUid, @NonNull Callback<Boolean> callback) {
        followRef(myUid, targetUid).get()
            .addOnSuccessListener(doc -> callback.onSuccess(doc.exists()))
            .addOnFailureListener(callback::onError);
    }

    // ---------- 숫자 ----------

    // uid를 팔로우하는 사람 수
    public void countFollowers(@NonNull String uid, @NonNull Callback<Long> callback) {
        count(db.collection(FirestorePaths.FOLLOWS).whereEqualTo("followingId", uid), callback);
    }

    // uid가 팔로우하는 사람 수
    public void countFollowing(@NonNull String uid, @NonNull Callback<Long> callback) {
        count(db.collection(FirestorePaths.FOLLOWS).whereEqualTo("followerId", uid), callback);
    }

    private void count(Query query, Callback<Long> callback) {
        query.count().get(AggregateSource.SERVER)
            .addOnSuccessListener(result -> callback.onSuccess(result.getCount()))
            .addOnFailureListener(callback::onError);
    }

    // ---------- 목록 ----------

    // uid의 팔로워 목록 (최근 팔로우순). 각 줄에 이름·아이디·사진·공개 평가 수·내가 팔로우 중인지를 채운다.
    public void loadFollowers(@NonNull String uid, @NonNull String myUid,
                              @NonNull Callback<List<FollowListItem>> callback) {
        loadList(db.collection(FirestorePaths.FOLLOWS).whereEqualTo("followingId", uid),
            "followerId", myUid, callback);
    }

    // uid의 팔로잉 목록 (최근 팔로우순)
    public void loadFollowing(@NonNull String uid, @NonNull String myUid,
                              @NonNull Callback<List<FollowListItem>> callback) {
        loadList(db.collection(FirestorePaths.FOLLOWS).whereEqualTo("followerId", uid),
            "followingId", myUid, callback);
    }

    // 1) follows에서 상대 UID 목록 → 2) 내가 팔로우 중인 UID 집합 → 3) users 문서 → 4) 공개 평가 수 → 목록
    private void loadList(Query relationQuery, String otherField, String myUid,
                          Callback<List<FollowListItem>> callback) {
        loadRelations(relationQuery)
            .continueWithTask(task -> {
                List<String> uids = new ArrayList<>();
                for (DocumentSnapshot doc : task.getResult()) {
                    String other = doc.getString(otherField);
                    if (other != null) {
                        uids.add(other);
                    }
                }
                return buildItems(uids, myUid);
            })
            .addOnSuccessListener(callback::onSuccess)
            .addOnFailureListener(e -> {
                Log.w(TAG, "follow list failed", e);
                callback.onError(e);
            });
    }

    // 최근 팔로우순 관계 문서. 복합 인덱스(…Id + createdAt↓)가 아직 없으면 Firestore가 FAILED_PRECONDITION으로
    // 거절한다(문서가 0개여도). 그때는 정렬 없이 받아 와서 기기에서 createdAt 최신순으로 정렬한다.
    // 인덱스를 만들면(Logcat 링크 또는 firestore.indexes.json 배포) 첫 번째 조회가 그대로 성공한다.
    private Task<List<DocumentSnapshot>> loadRelations(Query relationQuery) {
        return relationQuery.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_LIST_SIZE)
            .get()
            .continueWithTask(task -> {
                if (task.isSuccessful()) {
                    return Tasks.forResult(task.getResult().getDocuments());
                }
                Exception e = task.getException();
                if (e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    Log.w(TAG, "follow index missing, sorting on device. Create the index from this link:", e);
                    return relationQuery.limit(MAX_LIST_SIZE).get().continueWith(fallback -> {
                        List<DocumentSnapshot> docs = new ArrayList<>(fallback.getResult().getDocuments());
                        docs.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("createdAt");
                            Timestamp tb = b.getTimestamp("createdAt");
                            if (ta == null || tb == null) {
                                return ta == null ? (tb == null ? 0 : 1) : -1;
                            }
                            return tb.compareTo(ta);
                        });
                        return docs;
                    });
                }
                throw e != null ? e : new IllegalStateException("follow relations failed");
            });
    }

    // ---------- 온보딩 평가자 추천 (3.4) ----------

    // 고른 카테고리의 공개 평가를 많이 쓴 사람 순으로 최대 10명 (나는 제외).
    // 한 줄의 "평가 N개"는 고른 카테고리 안의 공개 평가 수다.
    public void loadRecommended(@NonNull String myUid, @NonNull List<String> categoryIds,
                                @NonNull Callback<List<FollowListItem>> callback) {
        if (categoryIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        List<String> ids = categoryIds.size() > MAX_IDS_PER_QUERY
            ? new ArrayList<>(categoryIds.subList(0, MAX_IDS_PER_QUERY)) : categoryIds;
        db.collection(FirestorePaths.RATINGS)
            .whereIn("categoryId", ids)
            .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC)
            .limit(MAX_RECOMMEND_SCAN)
            .get()
            .continueWithTask(task -> {
                Map<String, Long> counts = new HashMap<>();
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    String author = doc.getString("userId");
                    if (author != null && !author.equals(myUid)) {
                        Long current = counts.get(author);
                        counts.put(author, current == null ? 1L : current + 1);
                    }
                }
                List<String> top = new ArrayList<>(counts.keySet());
                top.sort((a, b) -> Long.compare(counts.get(b), counts.get(a)));
                if (top.size() > MAX_RECOMMEND) {
                    top = new ArrayList<>(top.subList(0, MAX_RECOMMEND));
                }
                return buildItems(top, myUid, counts);
            })
            .addOnSuccessListener(callback::onSuccess)
            .addOnFailureListener(e -> {
                Log.w(TAG, "recommend failed", e);
                callback.onError(e);
            });
    }

    private Task<List<FollowListItem>> buildItems(List<String> uids, String myUid) {
        return buildItems(uids, myUid, null);
    }

    // knownCounts가 있으면 그 숫자를 "평가 N개"로 쓰고, 없으면 사람마다 공개 평가 수를 센다
    private Task<List<FollowListItem>> buildItems(List<String> uids, String myUid,
                                                 Map<String, Long> knownCounts) {
        if (uids.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }
        Task<Set<String>> myFollowingTask = loadFollowingIds(myUid);
        Task<Map<String, DocumentSnapshot>> usersTask = loadUsers(uids);
        List<Task<AggregateQuerySnapshot>> countTasks = new ArrayList<>();
        if (knownCounts == null) {
            for (String uid : uids) {
                countTasks.add(db.collection(FirestorePaths.RATINGS)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC)
                    .count().get(AggregateSource.SERVER));
            }
        }

        List<Task<?>> all = new ArrayList<>(countTasks);
        all.add(myFollowingTask);
        all.add(usersTask);
        return Tasks.whenAll(all).continueWith(done -> {
            if (!done.isSuccessful()) {
                throw done.getException() != null ? done.getException() : new IllegalStateException("load failed");
            }
            Set<String> myFollowing = myFollowingTask.getResult();
            Map<String, DocumentSnapshot> users = usersTask.getResult();
            List<FollowListItem> items = new ArrayList<>();
            for (int i = 0; i < uids.size(); i++) {
                String uid = uids.get(i);
                DocumentSnapshot user = users.get(uid);
                if (user == null || !user.exists()) {
                    continue;   // 탈퇴한 사용자
                }
                long ratingCount = knownCounts != null
                    ? (knownCounts.containsKey(uid) ? knownCounts.get(uid) : 0L)
                    : countTasks.get(i).getResult().getCount();
                items.add(new FollowListItem(
                    uid,
                    user.getString("displayName"),
                    user.getString("username"),
                    user.getString("photoUrl"),
                    ratingCount,
                    myFollowing.contains(uid)));
            }
            return items;
        });
    }

    // 내가 팔로우하는 사람들의 UID (목록의 버튼 모양을 정할 때 쓴다)
    private Task<Set<String>> loadFollowingIds(String myUid) {
        return db.collection(FirestorePaths.FOLLOWS)
            .whereEqualTo("followerId", myUid)
            .get()
            .continueWith(task -> {
                Set<String> ids = new HashSet<>();
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    String id = doc.getString("followingId");
                    if (id != null) {
                        ids.add(id);
                    }
                }
                return ids;
            });
    }

    // users 문서를 30개씩 나눠 읽는다
    private Task<Map<String, DocumentSnapshot>> loadUsers(List<String> uids) {
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

    private DocumentReference followRef(String followerId, String followingId) {
        return db.collection(FirestorePaths.FOLLOWS).document(Follow.documentId(followerId, followingId));
    }
}
