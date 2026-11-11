package com.example.viewit.data;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 평가 문서의 저장·조회.
public class RatingRepository {
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
     * 평가 문서 전체를 다운로드하거나 videos 문서의 집계 필드를 수정하지 않는다.
     * Video는 기존 공통 통계 View에 값을 전달하는 용도로만 사용한다.
     */
    public void loadPublicVideoStatistics(
            @NonNull String videoId,
            @NonNull OnSuccessListener<Video> onSuccess,
            @NonNull OnFailureListener onFailure) {
        final Query query;
        try {
            query = publicVideoRatingsQuery(videoId);
        } catch (IllegalArgumentException error) {
            onFailure.onFailure(error);
            return;
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
        // 하나라도 실패하면 onFailure가 실행되므로 오류를 '평가 0개'로 표시하지 않는다.
        Tasks.<Object>whenAllSuccess(requests)
                .addOnSuccessListener(new OnSuccessListener<List<Object>>() {
                    @Override
                    public void onSuccess(List<Object> results) {
                        final Video statistics = new Video();
                        try {
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
                            statistics.setVideoId(videoId.trim());
                            statistics.setPublicRatingCount(count);
                            statistics.setRatingSum(bucketSum);
                            // 평가 0개이면 average가 null이다. 통계 View는 count 0일 때 '—'를 표시한다.
                            statistics.setRatingAverage(count == 0 ? 0 : average.doubleValue());
                            statistics.setRatingDistribution(distribution);
                        } catch (RuntimeException error) {
                            onFailure.onFailure(error);
                            return;
                        }
                        onSuccess.onSuccess(statistics);
                    }
                })
                .addOnFailureListener(onFailure);
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

    // 현재 로그인한 사용자의 해당 영상 평가만 삭제한다.
    // 성공 후 공개 집계를 다시 조회한다. 반응 하위 컬렉션 정리는 별도 처리 범위다.
    public Task<Void> deleteRating(@NonNull String videoId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        }
        if (videoId.trim().isEmpty() || videoId.contains("/")) {
            return Tasks.forException(new IllegalArgumentException("영상 ID를 확인하세요."));
        }
        return db.collection(FirestorePaths.RATINGS)
                .document(Rating.documentId(user.getUid(), videoId.trim()))
                .delete();
    }

    // 저장 버튼에서 호출할 API. 기존 평가는 최초 작성 시각과 반응 수를 유지한다.
    // 성공 리스너에서 loadPublicVideoStatistics를 호출하면 변경된 집계를 읽을 수 있다.
    public Task<Void> saveRating(@NonNull Rating rating) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser(); //유저 정보 반환
        if (user == null) { //로그인 안되어 있을 때
            return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        }
        if (rating.getVideoId() == null || rating.getVideoId().trim().isEmpty()
                || rating.getCategoryId() == null || rating.getCategoryId().trim().isEmpty()
                || !Rating.isValidScore(rating.getScore())
                || !(Rating.VISIBILITY_PUBLIC.equals(rating.getVisibility())
                || Rating.VISIBILITY_PRIVATE.equals(rating.getVisibility()))) {
            return Tasks.forException(new IllegalArgumentException("영상·카테고리·별점·공개 범위를 확인하세요."));
        } // 뭔가 잘못됐을 경우
        // 전달받은 객체를 나중에 바꾸어도 저장 요청의 값이 바뀌지 않도록 복사한다.
        Rating value = new Rating();
        value.setUserId(user.getUid());
        value.setVideoId(rating.getVideoId().trim());
        value.setCategoryId(rating.getCategoryId().trim());
        value.setScore(rating.getScore());
        value.setComment(rating.getComment() == null ? "" : rating.getComment().trim());
        value.setVisibility(rating.getVisibility());
        //document 생성
        DocumentReference ref = db.collection(FirestorePaths.RATINGS)
                .document(Rating.documentId(user.getUid(), value.getVideoId()));
        return db.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(ref);
            if (existing.exists()) { //평점 수정될 때 로직.
                Map<String, Object> changes = new HashMap<>();
                changes.put("score", value.getScore());
                changes.put("comment", value.getComment());
                changes.put("visibility", value.getVisibility());
                changes.put("categoryId", value.getCategoryId());
                changes.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(ref, changes);
            } else { //저장
                // null인 @ServerTimestamp 필드는 서버 작성 시각으로 저장된다.
                transaction.set(ref, value);
            }
            return null;
        });
    }
}
