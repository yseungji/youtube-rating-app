package com.example.viewit.data;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
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

    // 단건 조회 예: getRating(uid, videoId).addOnSuccessListener(snapshot -> snapshot.toObject(Rating.class));
    public Task<DocumentSnapshot> getRating(String uid, String videoId) { //Snapshot을 찍어 단건을 조회한다.
        return db.collection(FirestorePaths.RATINGS)
                .document(Rating.documentId(uid, videoId)).get();
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

    // 저장 버튼에서 호출할 API. 기존 평가는 최초 작성 시각과 반응 수를 유지한다.
    // 공개 집계 갱신은 이 메서드의 범위가 아니며 서버 함수 구현 단계에서 연결한다.
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
