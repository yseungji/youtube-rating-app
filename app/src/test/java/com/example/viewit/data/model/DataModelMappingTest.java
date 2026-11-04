package com.example.viewit.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.util.CustomClassMapper;

import org.junit.Test;

import java.util.Map;

// 데이터 클래스를 Firestore에 저장할 때 필드 이름이 docs/FIRESTORE_SCHEMA.md와 같은지 확인한다.
public class DataModelMappingTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toFirestoreMap(Object model) {
        return (Map<String, Object>) CustomClassMapper.convertToPlainJavaTypes(model);
    }

    @Test
    public void rating_usesSchemaFieldNames() {
        Rating rating = new Rating();
        rating.setId(Rating.documentId("uid1", "abc_DEF-123"));
        rating.setUserId("uid1");
        rating.setVideoId("abc_DEF-123");
        rating.setCategoryId("26");
        rating.setScore(4);

        Map<String, Object> map = toFirestoreMap(rating);

        assertFalse("문서 ID는 필드로 저장하지 않음", map.containsKey("id"));
        assertEquals(4L, map.get("score"));
        assertEquals("public", map.get("visibility"));
        assertEquals("", map.get("comment"));
        assertTrue(map.keySet().containsAll(java.util.Arrays.asList(
                "userId", "videoId", "categoryId", "likeCount", "commentCount", "createdAt", "updatedAt")));
        assertEquals("uid1_abc_DEF-123", Rating.documentId("uid1", "abc_DEF-123"));
    }

    @Test
    public void rating_scoreRange() {
        assertFalse(Rating.isValidScore(0));
        assertTrue(Rating.isValidScore(1));
        assertTrue(Rating.isValidScore(5));
        assertFalse(Rating.isValidScore(6));
    }

    @Test
    public void category_keepsIsActiveFieldName() {
        Category category = new Category();
        category.setCategoryId("10");
        category.setName("음악");
        category.setActive(true);

        Map<String, Object> map = toFirestoreMap(category);

        assertEquals(Boolean.TRUE, map.get("isActive"));
        assertFalse(map.containsKey("active"));
        assertFalse(map.containsKey("categoryId"));
    }

    @Test
    public void user_and_follow_and_video_skipDocumentIds() {
        User user = new User();
        user.setUid("uid1");
        assertFalse(toFirestoreMap(user).containsKey("uid"));

        Follow follow = new Follow();
        follow.setId(Follow.documentId("a", "b"));
        Map<String, Object> followMap = toFirestoreMap(follow);
        assertFalse(followMap.containsKey("id"));
        assertTrue(followMap.containsKey("followerId"));

        Video video = new Video();
        video.setVideoId("abc");
        Map<String, Object> videoMap = toFirestoreMap(video);
        assertFalse(videoMap.containsKey("videoId"));
        assertTrue(videoMap.containsKey("ratingDistribution"));
        assertEquals("s4", Video.distributionKey(4));
    }
}
