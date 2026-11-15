package com.example.viewit.data;

import com.example.viewit.BuildConfig;
import com.example.viewit.data.model.Rating;

// 실제 영상·평가와 구분하는 개발용 샘플 규칙. Firestore 조회·페이징은 기존 코드를 사용한다.
public final class FeedPagingSamples {
    public static final int COUNT = 45;
    private static final String VIDEO_PREFIX = "paging-sample-v1-";
    private static final String COMMENT_PREFIX = "[페이징샘플 v1] ";

    private FeedPagingSamples() {
    }

    public static String videoId(int number) {
        return VIDEO_PREFIX + (number < 10 ? "0" + number : String.valueOf(number));
    }

    public static Rating create(String uid, int number) {
        Rating rating = new Rating();
        rating.setUserId(uid);
        rating.setVideoId(videoId(number));
        rating.setCategoryId("20");
        rating.setScore(1 + (number - 1) % 5);
        rating.setComment(COMMENT_PREFIX + number + " / " + COUNT);
        rating.setVisibility(Rating.VISIBILITY_PUBLIC);
        // 좋아요 문서를 만들지 않으므로 개수도 0이다. 같은 정렬값의 페이지 경계를 확인한다.
        rating.setLikeCount(0);
        rating.setCommentCount(0);
        // createdAt·updatedAt은 Rating의 @ServerTimestamp로 서버에서 채운다.
        return rating;
    }

    public static boolean isSample(Rating rating) {
        return BuildConfig.DEBUG && rating != null
                && rating.getVideoId() != null
                && rating.getVideoId().startsWith(VIDEO_PREFIX)
                && rating.getComment() != null
                && rating.getComment().startsWith(COMMENT_PREFIX);
    }
}
