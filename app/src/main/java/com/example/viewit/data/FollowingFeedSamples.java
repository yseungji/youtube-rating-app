package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.viewit.BuildConfig;
import com.example.viewit.data.model.Follow;
import com.example.viewit.data.model.Rating;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 로그인 계정은 그대로 두고 샘플 UID의 프로필·평가·팔로우 문서만 준비한다.
// 다른 UID의 문서 쓰기가 보안 규칙에 막히면 실패를 알린다. 관리자 권한을 사용하지 않는다.
public final class FollowingFeedSamples {
    private static final String TAG = "FollowingFeedSample";
    private static final String SET = "following-feed-v1";
    private static final String VIDEO_PREFIX = "following-sample-v1-";
    private static final String COMMENT_PREFIX = "[팔로잉샘플 v1] ";
    // 모든 재실행에서 같은 작성 시각을 사용해, 일부 생성 후 재시도해도 순서가 바뀌지 않는다.
    private static final long BASE_SECONDS = 1794754800L; // 2026-11-16 00:00 KST

    public interface Callback {
        void onProgress(int completed, int total);
        void onComplete(int changed, int skipped, int failed);
    }

    private static class SampleDocument {
        final DocumentReference reference;
        final Map<String, Object> data;
        final boolean rating;

        SampleDocument(DocumentReference reference, Map<String, Object> data, boolean rating) {
            this.reference = reference;
            this.data = data;
            this.rating = rating;
        }
    }

    private boolean busy;
    private int completed;
    private int changed;
    private int skipped;
    private int failed;

    public static boolean isSample(Rating rating) {
        return BuildConfig.DEBUG && rating != null
                && rating.getVideoId() != null && rating.getVideoId().startsWith(VIDEO_PREFIX)
                && rating.getComment() != null && rating.getComment().startsWith(COMMENT_PREFIX);
    }

    private static String number(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String authorId(String owner, int author) {
        return "paging-follow-v1-" + owner + "-" + number(author);
    }

    private static Map<String, Object> markedData(String owner) {
        Map<String, Object> data = new HashMap<>();
        data.put("sampleSet", SET);
        data.put("sampleOwnerUid", owner);
        return data;
    }

    private List<SampleDocument> documents(FirebaseFirestore db, String owner) {
        List<SampleDocument> documents = new ArrayList<>();
        for (int author = 1; author <= 32; author++) {
            String uid = authorId(owner, author);
            if (author <= 31) {
                Map<String, Object> follow = markedData(owner);
                follow.put("followerId", owner);
                follow.put("followingId", uid);
                follow.put("createdAt", FieldValue.serverTimestamp());
                documents.add(new SampleDocument(db.collection("follows").document(
                        Follow.documentId(owner, uid)), follow, false));
            }
            Map<String, Object> profile = markedData(owner);
            profile.put("displayName", "샘플 작성자 " + number(author));
            profile.put("username", "follow_sample_" + number(author));
            profile.put("introduction", "팔로잉 피드 확인용 가상 사용자");
            profile.put("photoUrl", "");
            profile.put("createdAt", FieldValue.serverTimestamp());
            profile.put("updatedAt", FieldValue.serverTimestamp());
            documents.add(new SampleDocument(db.collection("users").document(uid), profile, false));

            if (author <= 31) {
                for (int round = 0; round < 2; round++) {
                    // 작성자31을 각 묶음의 맨 앞에 배치해, 두 Query의 결과를 섞어야 한다.
                    int rank = round * 31 + author % 31;
                    documents.add(ratingDocument(db, owner, uid, rank + 1,
                            Rating.VISIBILITY_PUBLIC, rank,
                            COMMENT_PREFIX + "순서 " + number(rank + 1) + " / 62"));
                }
            } else {
                documents.add(ratingDocument(db, owner, uid, 64,
                        Rating.VISIBILITY_PUBLIC, -2,
                        COMMENT_PREFIX + "표시되면 안 됨: 미팔로우 작성자"));
            }
        }
        documents.add(ratingDocument(db, owner, authorId(owner, 1), 63,
                Rating.VISIBILITY_PRIVATE, -1,
                COMMENT_PREFIX + "표시되면 안 됨: 비공개 평가"));
        return documents;
    }

    private SampleDocument ratingDocument(FirebaseFirestore db, String owner, String author,
                                          int videoNumber, String visibility, int rank, String comment) {
        String videoId = VIDEO_PREFIX + number(videoNumber);
        Map<String, Object> data = markedData(owner);
        data.put("userId", author);
        data.put("videoId", videoId);
        data.put("categoryId", "20");
        data.put("score", (long) (1 + videoNumber % 5));
        data.put("comment", comment);
        data.put("visibility", visibility);
        data.put("likeCount", 0L);
        data.put("commentCount", 0L);
        data.put("createdAt", new Timestamp(BASE_SECONDS - rank * 60L, 0));
        data.put("updatedAt", FieldValue.serverTimestamp());
        return new SampleDocument(db.collection("ratings").document(
                Rating.documentId(author, videoId)), data, true);
    }

    public void run(FirebaseFirestore db, final String owner, final boolean deleting,
                    final Callback callback) {
        if (busy) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (!BuildConfig.DEBUG || user == null || !owner.equals(user.getUid())) {
            Log.e(TAG, "디버그 빌드와 로그인 계정을 확인하세요.");
            callback.onComplete(0, 0, 1);
            return;
        }
        final List<SampleDocument> documents = documents(db, owner);
        final int total = documents.size(); // 프로필32 + 평가64 + 팔로우31 = 127
        busy = true;
        completed = changed = skipped = failed = 0;
        callback.onProgress(0, total);
        Log.d(TAG, (deleting ? "팔로잉 샘플 삭제 시작" : "팔로잉 샘플 생성 시작")
                + ": documents=" + total);
        for (final SampleDocument document : documents) {
            document.reference.get(Source.SERVER)
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot snapshot) {
                            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                            if (current == null || !owner.equals(current.getUid())) {
                                fail(deleting, total, callback,
                                        new IllegalStateException("로그인 계정이 변경되었습니다."));
                                return;
                            }
                            try {
                                if (snapshot.exists()) {
                                    if (!SET.equals(snapshot.getString("sampleSet"))
                                            || !owner.equals(snapshot.getString("sampleOwnerUid"))) {
                                        throw new IllegalStateException(
                                                "샘플 소유 표시가 없는 기존 문서는 변경하지 않습니다.");
                                    }
                                    if (!deleting) {
                                        finish(false, false, total, callback);
                                        return;
                                    }
                                    if (document.rating
                                            && (!Long.valueOf(0).equals(snapshot.getLong("likeCount"))
                                            || !Long.valueOf(0).equals(snapshot.getLong("commentCount")))) {
                                        Log.w(TAG, "반응이 생긴 샘플 평가는 삭제를 건너뜁니다.");
                                        finish(false, false, total, callback);
                                        return;
                                    }
                                } else if (deleting) {
                                    finish(false, false, total, callback);
                                    return;
                                }
                            } catch (RuntimeException error) {
                                fail(deleting, total, callback, error);
                                return;
                            }
                            Task<Void> operation = deleting ? document.reference.delete()
                                    : document.reference.set(document.data);
                            operation.addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    finish(true, false, total, callback);
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception error) {
                                    fail(deleting, total, callback, error);
                                }
                            });
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception error) {
                            fail(deleting, total, callback, error);
                        }
                    });
        }
    }

    private void fail(boolean deleting, int total, Callback callback, Exception error) {
        Log.e(TAG, deleting ? "팔로잉 샘플 삭제 실패" : "팔로잉 샘플 생성 실패", error);
        finish(false, true, total, callback);
    }

    private void finish(boolean didChange, boolean didFail, int total, Callback callback) {
        completed++;
        if (didFail) {
            failed++;
        } else if (didChange) {
            changed++;
        } else {
            skipped++;
        }
        if (completed == total) {
            busy = false;
            Log.d(TAG, "팔로잉 샘플 작업 종료: success=" + changed
                    + ", skipped=" + skipped + ", failed=" + failed);
            callback.onComplete(changed, skipped, failed);
        } else {
            callback.onProgress(completed, total);
        }
    }
}
