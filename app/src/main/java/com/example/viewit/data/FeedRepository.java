package com.example.viewit.data;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 화면에 보여 줄 20개를 만든다. 실시간 구독 없이 당겨서 새로고침할 때만 처음부터 읽는다.
// 최신순: 팔로잉 30명씩 여러 Query. 인기순: 전체 공개 평가 Query 하나.
public class FeedRepository {
    private static final int PAGE_SIZE = 20;
    private static final int AUTHORS_PER_GROUP = 30;
    private static final int MAX_PARALLEL_READS = 4;

    public interface Callback {
        void onPage(List<Rating> ratings, boolean hasMore);
        void onNoFollowing();
        void onError(@NonNull Exception error);
    }

    // 묶음마다 "조회한 마지막 문서"와 "조회했지만 표시하지 않은 평가"를 따로 기억한다.
    private static class FeedGroup {
        final Query query;
        final List<Rating> remaining = new ArrayList<>();
        DocumentSnapshot lastRead;
        boolean exhausted;

        FeedGroup(Query query) {
            this.query = query;
        }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<FeedGroup> groups = new ArrayList<>();
    private final List<Rating> pendingPage = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private boolean latest;
    private boolean loading;
    private boolean followingReady;
    private String sessionUid = "";
    private int generation;

    // 이전 요청은 취소할 수 없어도 결과를 적용하지 않을 수 있다.
    public void cancel() {
        generation++;
        loading = false;
        followingReady = false;
        groups.clear();
        pendingPage.clear();
        selectedIds.clear();
    }

    public void refresh(boolean latestOrder, Callback callback) {
        cancel();
        latest = latestOrder;
        sessionUid = currentUid();
        loadNext(callback);
    }

    public void loadNext(Callback callback) {
        if (loading) {
            return;
        }
        if (!sessionUid.equals(currentUid())) {
            callback.onError(new IllegalStateException("로그인 계정이 변경되었습니다. 새로고침하세요."));
            return;
        }
        loading = true;
        final int request = generation;
        if (!followingReady) {
            if (latest) {
                loadFollowing(request, callback);
            } else {
                groups.add(new FeedGroup(publicRatings()
                        .orderBy("likeCount", Query.Direction.DESCENDING)));
                followingReady = true;
                buildPage(request, callback);
            }
        } else {
            buildPage(request, callback);
        }
    }

    private String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? "" : user.getUid();
    }

    private boolean isCurrent(int request) {
        return request == generation;
    }

    private Query publicRatings() {
        return db.collection(FirestorePaths.RATINGS)
                .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC);
    }

    private void loadFollowing(int request, Callback callback) {
        if (sessionUid.isEmpty()) {
            fail(request, callback, new IllegalStateException("로그인이 필요합니다."));
            return;
        }
        // 새로고침 때 팔로잉 전체를 읽고, 스크롤할 때는 이 목록을 다시 읽지 않는다.
        db.collection(FirestorePaths.FOLLOWS)
                .whereEqualTo("followerId", sessionUid)
                .get(Source.SERVER)
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot snapshot) {
                        if (!isCurrent(request)) {
                            return;
                        }
                        if (!sessionUid.equals(currentUid())) {
                            fail(request, callback, new IllegalStateException("로그인 계정이 변경되었습니다."));
                            return;
                        }
                        Set<String> uniqueIds = new LinkedHashSet<>();
                        try {
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
                                String uid = document.getString("followingId");
                                if (uid == null || uid.trim().isEmpty() || uid.contains("/")) {
                                    throw new IllegalStateException("팔로잉 UID를 확인하세요.");
                                }
                                uniqueIds.add(uid);
                            }
                        } catch (RuntimeException error) {
                            fail(request, callback, error);
                            return;
                        }
                        List<String> ids = new ArrayList<>(uniqueIds);
                        for (int start = 0; start < ids.size(); start += AUTHORS_PER_GROUP) {
                            List<String> part = new ArrayList<>(ids.subList(
                                    start, Math.min(start + AUTHORS_PER_GROUP, ids.size())));
                            groups.add(new FeedGroup(publicRatings().whereIn("userId", part)
                                    .orderBy("createdAt", Query.Direction.DESCENDING)));
                        }
                        followingReady = true;
                        if (groups.isEmpty()) {
                            loading = false;
                            callback.onNoFollowing();
                        } else {
                            buildPage(request, callback);
                        }
                    }
                })
                .addOnFailureListener(error -> fail(request, callback, error));
    }

    private void buildPage(int request, Callback callback) {
        if (!isCurrent(request)) {
            return;
        }
        if (!sessionUid.equals(currentUid())) {
            fail(request, callback, new IllegalStateException("로그인 계정이 변경되었습니다."));
            return;
        }
        while (pendingPage.size() < PAGE_SIZE) {
            List<FeedGroup> missing = new ArrayList<>();
            for (FeedGroup group : groups) {
                if (group.remaining.isEmpty() && !group.exhausted) {
                    missing.add(group);
                }
            }
            // 비어 있는 묶음을 먼저 채워야 다른 묶음의 오래된 평가를 앞에 표시하지 않는다.
            if (!missing.isEmpty()) {
                readGroups(missing, 0, request, callback);
                return;
            }
            FeedGroup newest = null;
            for (FeedGroup group : groups) {
                if (!group.remaining.isEmpty() && (newest == null
                        || compare(group.remaining.get(0), newest.remaining.get(0)) < 0)) {
                    newest = group;
                }
            }
            if (newest == null) {
                break;
            }
            Rating rating = newest.remaining.remove(0);
            if (selectedIds.add(rating.getId())) {
                pendingPage.add(rating);
            }
        }
        boolean hasMore = false;
        for (FeedGroup group : groups) {
            if (!group.remaining.isEmpty() || !group.exhausted) {
                hasMore = true;
                break;
            }
        }
        List<Rating> page = new ArrayList<>(pendingPage);
        pendingPage.clear();
        loading = false;
        callback.onPage(page, hasMore);
    }

    // 수백 묶음이 있어도 한꺼번에 요청하지 않고 최대 4개씩 읽는다.
    private void readGroups(List<FeedGroup> missing, int start, int request, Callback callback) {
        if (!isCurrent(request)) {
            return;
        }
        int end = Math.min(start + MAX_PARALLEL_READS, missing.size());
        List<Task<QuerySnapshot>> reads = new ArrayList<>();
        for (int i = start; i < end; i++) {
            FeedGroup group = missing.get(i);
            Query query = group.query;
            if (group.lastRead != null) {
                query = query.startAfter(group.lastRead);
            }
            reads.add(query.limit(PAGE_SIZE).get(Source.SERVER));
        }
        Tasks.whenAllComplete(reads)
                .addOnSuccessListener(new OnSuccessListener<List<Task<?>>>() {
                    @Override
                    public void onSuccess(List<Task<?>> completed) {
                        if (!isCurrent(request)) {
                            return;
                        }
                        Exception failure = null;
                        for (int i = 0; i < reads.size(); i++) {
                            Task<QuerySnapshot> read = reads.get(i);
                            if (!read.isSuccessful()) {
                                failure = read.getException();
                                if (failure == null) {
                                    failure = new IllegalStateException("평가 조회가 취소되었습니다.");
                                }
                                continue;
                            }
                            try {
                                saveRead(missing.get(start + i), read.getResult());
                            } catch (RuntimeException error) {
                                failure = error;
                            }
                        }
                        // 성공한 묶음의 결과와 만들던 페이지는 유지한다.
                        // 재시도할 때 실패한 묶음만 읽으므로 누락·중복을 줄인다.
                        if (failure != null) {
                            fail(request, callback, failure);
                        } else if (end < missing.size()) {
                            readGroups(missing, end, request, callback);
                        } else {
                            buildPage(request, callback);
                        }
                    }
                })
                .addOnFailureListener(error -> fail(request, callback, error));
    }

    private void saveRead(FeedGroup group, QuerySnapshot snapshot) {
        List<Rating> ratings = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Rating rating = document.toObject(Rating.class);
            if (rating == null) {
                throw new IllegalStateException("평가 데이터를 확인하세요.");
            }
            rating.setId(document.getId());
            ratings.add(rating);
        }
        // 변환에 모두 성공한 뒤 커서를 옮겨야 잘못된 문서를 건너뛰지 않는다.
        group.remaining.addAll(ratings);
        if (!snapshot.isEmpty()) {
            group.lastRead = snapshot.getDocuments().get(snapshot.size() - 1);
        }
        group.exhausted = snapshot.size() < PAGE_SIZE;
    }

    private int compare(Rating left, Rating right) {
        int result;
        if (latest) {
            Timestamp a = left.getCreatedAt();
            Timestamp b = right.getCreatedAt();
            result = a == null ? (b == null ? 0 : 1) : b == null ? -1 : b.compareTo(a);
        } else {
            result = Long.compare(right.getLikeCount(), left.getLikeCount());
        }
        // Firestore는 정렬값이 같으면 문서 경로도 같은 방향(DESCENDING)으로 정렬한다.
        // 같은 ratings 컬렉션의 문서 ID를 비교해 묶음 간 순서도 동일하게 맞춘다.
        return result == 0 ? right.getId().compareTo(left.getId()) : result;
    }

    private void fail(int request, Callback callback, Exception error) {
        if (!isCurrent(request)) {
            return;
        }
        loading = false;
        callback.onError(error);
    }
}
