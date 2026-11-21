package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateField;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 평가 문서의 저장·조회 및 videos 공개 집계 갱신.
public class RatingRepository {
    private static final String TAG = "RatingRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();//dp 객체 생성

    // 페이징용 Query에는 limit/startAt/endAt을 넣지 않는다. 페이지 경계는 FirebaseUI가 관리한다.
    // 공개 평가만 조회하며 문서 ID순으로 고정한다. 최신순 정렬은 별도 복합 인덱스가 필요하다.
    public Query publicRatingsQuery() {
        return db.collection(FirestorePaths.RATINGS)
                .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC) //where로 공개평가만 조회한다
                .orderBy(FieldPath.documentId()); //documentId를 가져온다.
    }

    // 전체 사람 = 팔로우 조건 없이, 이 영상에 남긴 공개 평가 전체(내 공개 평가 포함).
    // 페이지 크기 제한은 목록 Adapter가 설정한다. 집계에는 limit를 넣으면 안 된다.
    public Query publicVideoRatingsQuery(@NonNull String videoId) {
        if (videoId.trim().isEmpty() || videoId.contains("/")) {
            throw new IllegalArgumentException("영상 ID를 확인하세요.");
        }
        return db.collection(FirestorePaths.RATINGS)
                .whereEqualTo("videoId", videoId.trim())
                .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC)
                // 테스트 문서의 4.5점이나 필드 누락은 서비스용 평가에서 제외한다.
                .whereIn("score", Arrays.asList(1, 2, 3, 4, 5));
    }

    /**
     * Firestore가 계산한 평균·개수와 점수별 개수를 읽는다. 서버 함수 배포는 필요 없다.
     * 실시간 구독이 아니므로 화면 진입/저장·수정·삭제 성공 시 다시 호출한다.
     * 이 조회 메서드 자체는 videos 문서를 수정하지 않으며, Video는 통계 전달용으로 사용한다.
     */
    public void loadPublicVideoStatistics(
            @NonNull String videoId,
            @NonNull OnSuccessListener<Video> onSuccess,
            @NonNull OnFailureListener onFailure) {
        queryPublicVideoStatistics(videoId)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // 서버 집계 쿼리로 해당 영상의 공개 평가 개수·합계·평균·분포를 계산한다.
    private Task<Video> queryPublicVideoStatistics(@NonNull String videoId) {
        final String trimmedVideoId = videoId.trim();
        final Query query;
        try {
            query = publicVideoRatingsQuery(trimmedVideoId);
        } catch (IllegalArgumentException error) {
            return Tasks.forException(error);
        }
        List<Task<AggregateQuerySnapshot>> requests = new ArrayList<>();
        requests.add(query.aggregate(AggregateField.count(),
                        AggregateField.sum("score"), AggregateField.average("score"))
                .get(AggregateSource.SERVER));
        for (int score = Rating.MIN_SCORE; score <= Rating.MAX_SCORE; score++) {
            requests.add(query.whereEqualTo("score", score).count().get(AggregateSource.SERVER));
        }

        // 요청들은 순서대로 기다리지 않고 함께 보낸다.
        // whenAllSuccess는 모두 성공했을 때만 결과 목록을 전달하는 Firebase Task 기능이다.
        // 하나라도 실패하면 실패 Task가 반환되므로 오류를 '평가 0개'로 처리하지 않는다.
        return Tasks.<Object>whenAllSuccess(requests)
                .continueWith(new Continuation<List<Object>, Video>() {
                    @Override
                    public Video then(@NonNull Task<List<Object>> task) {
                        List<Object> results = task.getResult();
                        AggregateQuerySnapshot summary = (AggregateQuerySnapshot) results.get(0);
                        long count = summary.getCount();
                        Number sum = (Number) summary.get(AggregateField.sum("score"));
                        Number average = (Number) summary.get(AggregateField.average("score"));
                        Map<String, Long> distribution = new HashMap<>();
                        long bucketCount = 0;
                        long bucketSum = 0;
                        for (int score = Rating.MIN_SCORE; score <= Rating.MAX_SCORE; score++) {
                            long number = ((AggregateQuerySnapshot) results.get(score)).getCount();
                            distribution.put(Video.distributionKey(score), number);
                            bucketCount += number;
                            bucketSum += score * number;
                        }
                        // 여섯 쿼리는 같은 시점의 원자적 조회가 아니다.
                        // 조회 도중 평가가 바뀌어 요약과 분포가 다르면 새로고침하도록 실패를 전달한다.
                        if (count != bucketCount || (sum != null && sum.doubleValue() != bucketSum)
                                || (count > 0 && (average == null || sum == null))) {
                            throw new IllegalStateException("평가가 변경되었습니다. 다시 조회해 주세요.");
                        }
                        Video statistics = new Video();
                        statistics.setVideoId(trimmedVideoId);
                        statistics.setPublicRatingCount(count);
                        statistics.setRatingSum(bucketSum);
                        // 평가 0개이면 average가 null이다. 통계 View는 count 0일 때 '—'를 표시한다.
                        statistics.setRatingAverage(count == 0 ? 0.0 : average.doubleValue());
                        statistics.setRatingDistribution(distribution);
                        return statistics;
                    }
                });
    }

    // videos/{videoId}의 공개 집계 필드가 아직 없거나 어긋난 경우 서버 집계 쿼리 결과로 맞춘다.
    // SetOptions.merge()를 사용해 제목·썸네일·카테고리 등 영상 기본 정보는 건드리지 않는다.
    private Task<Void> syncPublicVideoStatistics(@NonNull final String videoId) {
        Log.d(TAG, "videos 집계 보정 시작: videoId=" + videoId);
        return queryPublicVideoStatistics(videoId)
                .continueWithTask(new Continuation<Video, Task<Void>>() {
                    @Override
                    public Task<Void> then(@NonNull Task<Video> task) {
                        if (!task.isSuccessful()) {
                            Exception error = task.getException();
                            Log.e(TAG, "videos 집계 보정용 쿼리 실패: videoId=" + videoId, error);
                            return Tasks.forException(error != null
                                    ? error : new IllegalStateException("영상 집계 조회에 실패했습니다."));
                        }
                        Video stats = task.getResult();
                        Map<String, Object> fields = buildVideoStatsMap(
                                stats.getPublicRatingCount(),
                                stats.getRatingSum(),
                                stats.getRatingAverage(),
                                stats.getRatingDistribution());
                        DocumentReference videoRef = db.collection(FirestorePaths.VIDEOS).document(videoId);
                        return videoRef.set(fields, SetOptions.merge())
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Log.d(TAG, "videos 집계 보정 완료: videoId=" + videoId
                                                + ", count=" + stats.getPublicRatingCount()
                                                + ", average=" + stats.getRatingAverage());
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception error) {
                                        Log.e(TAG, "videos 집계 보정 저장 실패: videoId=" + videoId, error);
                                    }
                                });
                    }
                });
    }

    // 단건 조회 예: getRating(uid, videoId).addOnSuccessListener(snapshot -> snapshot.toObject(Rating.class));
    public Task<DocumentSnapshot> getRating(String uid, String videoId) { //Snapshot을 찍어 단건을 조회한다.
        // 서버 조회 실패와 실제 평가 없음을 구분한다.
        return db.collection(FirestorePaths.RATINGS)
                .document(Rating.documentId(uid, videoId)).get(Source.SERVER);
    }

    // Query.get() + onSuccess + DocumentSnapshot을 직접 연습할 때 사용하는 일회성 조회.
    // PagingAdapter는 Query를 직접 사용하므로 이 메서드를 먼저 호출할 필요가 없다.
    public void getMultipleDocumentsFromACollection( //테스트 query 만드는 함수
            @NonNull OnSuccessListener<List<Rating>> onSuccess,
            @NonNull OnFailureListener onFailure) {
        publicRatingsQuery().limit(20).get()
                .addOnSuccessListener(documents -> {
                    try {
                        List<Rating> ratings = new ArrayList<>();
                        for (DocumentSnapshot document : documents.getDocuments()) {
                            Rating rating = document.toObject(Rating.class);
                            if (rating != null) {
                                ratings.add(rating);
                            }
                        }
                        onSuccess.onSuccess(ratings);
                    } catch (RuntimeException e) {
                        onFailure.onFailure(e);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    // 현재 로그인한 사용자의 해당 영상 평가를 삭제하고 videos/{videoId}의 공개 집계도 함께 갱신한다.
    // 반응 하위 컬렉션 정리는 별도 처리 범위다.
    public Task<Void> deleteRating(@NonNull String videoId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        }
        final String trimmedVideoId = videoId.trim();
        if (trimmedVideoId.isEmpty() || trimmedVideoId.contains("/")) {
            return Tasks.forException(new IllegalArgumentException("영상 ID를 확인하세요."));
        }
        final DocumentReference ratingRef = db.collection(FirestorePaths.RATINGS)
                .document(Rating.documentId(user.getUid(), trimmedVideoId));
        final DocumentReference videoRef = db.collection(FirestorePaths.VIDEOS)
                .document(trimmedVideoId);

        Log.d(TAG, "평가 삭제 및 videos 집계 갱신 시작: videoId=" + trimmedVideoId);
        return db.runTransaction(new Transaction.Function<Boolean>() {
            @Override
            public Boolean apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                // Firestore 트랜잭션 규칙상 읽기(get)를 쓰기(delete/set)보다 먼저 실행한다.
                DocumentSnapshot existingRating = transaction.get(ratingRef);
                DocumentSnapshot existingVideo = transaction.get(videoRef);

                int oldPublicScore = extractPublicScore(existingRating);
                if (existingRating.exists()) {
                    transaction.delete(ratingRef);
                }
                if (oldPublicScore == 0) {
                    // 비공개 평가였거나 이미 없는 문서면 공개 집계는 변하지 않는다.
                    return Boolean.FALSE;
                }

                Map<String, Object> updatedStats = buildDeltaVideoStats(existingVideo, oldPublicScore, 0);
                if (updatedStats != null) {
                    transaction.set(videoRef, updatedStats, SetOptions.merge());
                    return Boolean.FALSE;
                }
                // videos에 이전 집계 기준이 없거나 불일치하면 삭제 완료 후 집계 쿼리로 맞춘다.
                return Boolean.TRUE;
            }
        }).continueWithTask(new Continuation<Boolean, Task<Void>>() {
            @Override
            public Task<Void> then(@NonNull Task<Boolean> task) {
                if (!task.isSuccessful()) {
                    Exception error = task.getException();
                    Log.e(TAG, "평가 삭제 트랜잭션 실패: videoId=" + trimmedVideoId, error);
                    return Tasks.forException(error != null
                            ? error : new IllegalStateException("평가 삭제에 실패했습니다."));
                }
                if (Boolean.TRUE.equals(task.getResult())) {
                    Log.w(TAG, "videos 집계 초기값 없음 또는 불일치로 보정 실행: videoId=" + trimmedVideoId);
                    return syncPublicVideoStatistics(trimmedVideoId);
                }
                Log.d(TAG, "평가 삭제 및 videos 집계 갱신 완료: videoId=" + trimmedVideoId);
                return Tasks.forResult(null);
            }
        });
    }

    // 저장 버튼에서 호출할 API. 기존 평가는 최초 작성 시각과 반응 수를 유지하며 videos 공개 집계도 갱신한다.
    // 성공 리스너에서 loadPublicVideoStatistics를 호출하면 변경된 집계를 읽을 수 있다.
    public Task<Void> saveRating(@NonNull Rating rating) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser(); //유저 정보 반환
        if (user == null) { //로그인 안되어 있을 때
            return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        }
        if (rating.getVideoId() == null || rating.getVideoId().trim().isEmpty()
                || rating.getVideoId().contains("/")
                || rating.getCategoryId() == null || rating.getCategoryId().trim().isEmpty()
                || !Rating.isValidScore(rating.getScore())
                || !(Rating.VISIBILITY_PUBLIC.equals(rating.getVisibility())
                || Rating.VISIBILITY_PRIVATE.equals(rating.getVisibility()))) {
            return Tasks.forException(new IllegalArgumentException("영상·카테고리·별점·공개 범위를 확인하세요."));
        } // 뭔가 잘못됐을 경우
        // 전달받은 객체를 나중에 바꾸어도 저장 요청의 값이 바뀌지 않도록 복사한다.
        final Rating value = new Rating();
        value.setUserId(user.getUid());
        value.setVideoId(rating.getVideoId().trim());
        value.setCategoryId(rating.getCategoryId().trim());
        value.setScore(rating.getScore());
        value.setComment(rating.getComment() == null ? "" : rating.getComment().trim());
        value.setVisibility(rating.getVisibility());

        final String trimmedVideoId = value.getVideoId();
        final DocumentReference ratingRef = db.collection(FirestorePaths.RATINGS)
                .document(Rating.documentId(user.getUid(), trimmedVideoId));
        final DocumentReference videoRef = db.collection(FirestorePaths.VIDEOS)
                .document(trimmedVideoId);

        Log.d(TAG, "평가 저장 및 videos 집계 갱신 시작: videoId=" + trimmedVideoId
                + ", score=" + value.getScore() + ", visibility=" + value.getVisibility());
        return db.runTransaction(new Transaction.Function<Boolean>() {
            @Override
            public Boolean apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                // Firestore 트랜잭션 규칙상 읽기(get)를 쓰기(set/update)보다 먼저 실행한다.
                DocumentSnapshot existingRating = transaction.get(ratingRef);
                DocumentSnapshot existingVideo = transaction.get(videoRef);

                int oldPublicScore = extractPublicScore(existingRating);
                int newPublicScore = Rating.VISIBILITY_PUBLIC.equals(value.getVisibility())
                        ? (int) value.getScore() : 0;

                if (existingRating.exists()) { //평점 수정될 때 로직.
                    Map<String, Object> changes = new HashMap<>();
                    changes.put("score", value.getScore());
                    changes.put("comment", value.getComment());
                    changes.put("visibility", value.getVisibility());
                    changes.put("categoryId", value.getCategoryId());
                    changes.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.update(ratingRef, changes);
                } else { //저장
                    // null인 @ServerTimestamp 필드는 서버 작성 시각으로 저장된다.
                    transaction.set(ratingRef, value);
                }

                boolean videoStatsInitialized = existingVideo.exists()
                        && existingVideo.getTimestamp("statsUpdatedAt") != null;
                if (oldPublicScore == newPublicScore) {
                    // 별점과 공개 여부가 그대로면 이미 초기화된 영상은 집계를 다시 쓸 필요가 없다.
                    // 단, 예전 평가가 있는데 아직 videos 집계가 한 번도 저장되지 않았다면 보정한다.
                    return !videoStatsInitialized && newPublicScore > 0;
                }

                Map<String, Object> updatedStats = buildDeltaVideoStats(
                        existingVideo, oldPublicScore, newPublicScore);
                if (updatedStats != null) {
                    transaction.set(videoRef, updatedStats, SetOptions.merge());
                    return Boolean.FALSE;
                }
                // videos에 이전 집계 기준이 없거나 불일치하면 저장 완료 후 집계 쿼리로 맞춘다.
                return Boolean.TRUE;
            }
        }).continueWithTask(new Continuation<Boolean, Task<Void>>() {
            @Override
            public Task<Void> then(@NonNull Task<Boolean> task) {
                if (!task.isSuccessful()) {
                    Exception error = task.getException();
                    Log.e(TAG, "평가 저장 트랜잭션 실패: videoId=" + trimmedVideoId, error);
                    return Tasks.forException(error != null
                            ? error : new IllegalStateException("평가 저장에 실패했습니다."));
                }
                if (Boolean.TRUE.equals(task.getResult())) {
                    Log.w(TAG, "videos 집계 초기값 없음 또는 불일치로 보정 실행: videoId=" + trimmedVideoId);
                    return syncPublicVideoStatistics(trimmedVideoId);
                }
                Log.d(TAG, "평가 저장 및 videos 집계 갱신 완료: videoId=" + trimmedVideoId);
                return Tasks.forResult(null);
            }
        });
    }

    // 기존 평가 문서가 유효한 공개 평가(1~5점 정수)이면 그 점수를, 아니면 0을 반환한다.
    private static int extractPublicScore(@NonNull DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            return 0;
        }
        if (!Rating.VISIBILITY_PUBLIC.equals(snapshot.getString("visibility"))) {
            return 0;
        }
        Long score = readExactLong(snapshot.get("score"));
        if (score == null || !Rating.isValidScore(score)) {
            return 0;
        }
        return score.intValue();
    }

    // 기존 videos 문서의 집계값에 이번 변화(oldPublicScore -> newPublicScore)를 더해 새 집계 맵을 만든다.
    // 아직 집계가 한 번도 저장되지 않았거나 기존 분포 합계가 맞지 않으면 null을 반환해 보정으로 넘긴다.
    @Nullable
    private static Map<String, Object> buildDeltaVideoStats(
            @NonNull DocumentSnapshot existingVideo,
            int oldPublicScore,
            int newPublicScore) {
        if (!existingVideo.exists() || existingVideo.getTimestamp("statsUpdatedAt") == null) {
            return null;
        }
        Long storedCount = readExactLong(existingVideo.get("publicRatingCount"));
        Long storedSum = readExactLong(existingVideo.get("ratingSum"));
        Object rawDistribution = existingVideo.get("ratingDistribution");
        if (storedCount == null || storedSum == null || !(rawDistribution instanceof Map)) {
            return null;
        }

        Map<?, ?> distributionMap = (Map<?, ?>) rawDistribution;
        long[] counts = new long[Rating.MAX_SCORE + 1];
        long checkCount = 0;
        long checkSum = 0;
        for (int score = Rating.MIN_SCORE; score <= Rating.MAX_SCORE; score++) {
            Long value = readExactLong(distributionMap.get(Video.distributionKey(score)));
            if (value == null || value < 0) {
                return null;
            }
            counts[score] = value;
            checkCount += value;
            checkSum += score * value;
        }
        if (checkCount != storedCount || checkSum != storedSum) {
            return null;
        }

        if (Rating.isValidScore(oldPublicScore)) {
            counts[oldPublicScore]--;
            if (counts[oldPublicScore] < 0) {
                return null;
            }
        }
        if (Rating.isValidScore(newPublicScore)) {
            counts[newPublicScore]++;
        }

        Map<String, Long> updatedDistribution = new HashMap<>();
        long newCount = 0;
        long newSum = 0;
        for (int score = Rating.MIN_SCORE; score <= Rating.MAX_SCORE; score++) {
            updatedDistribution.put(Video.distributionKey(score), counts[score]);
            newCount += counts[score];
            newSum += score * counts[score];
        }
        double newAverage = newCount == 0 ? 0.0 : (double) newSum / newCount;
        return buildVideoStatsMap(newCount, newSum, newAverage, updatedDistribution);
    }

    @NonNull
    private static Map<String, Object> buildVideoStatsMap(
            long publicRatingCount,
            long ratingSum,
            double ratingAverage,
            @NonNull Map<String, Long> ratingDistribution) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("publicRatingCount", publicRatingCount);
        fields.put("ratingSum", ratingSum);
        fields.put("ratingAverage", ratingAverage);
        fields.put("ratingDistribution", new HashMap<>(ratingDistribution));
        fields.put("statsUpdatedAt", FieldValue.serverTimestamp());
        return fields;
    }

    @Nullable
    private static Long readExactLong(@Nullable Object raw) {
        if (raw instanceof Long) {
            return (Long) raw;
        }
        if (raw instanceof Integer) {
            return ((Integer) raw).longValue();
        }
        return null;
    }
}
