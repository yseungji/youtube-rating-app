package com.example.viewit.data;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.FollowListItem;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 탐색 탭 검색 (4.4).
// Firestore에는 "제목·이름 중간 글자로 찾기"가 없어서, 후보(사용자 전체, 평가된 영상 전체)를 한 번 받아 두고
// 앱에서 contains로 거른다 (팀 결정 B). 후보는 탐색 화면이 살아 있는 동안 이 객체에 보관해 다시 읽지 않는다.
// 사용자·영상이 수천 개 넘게 쌓이면 검색용 단어 목록 필드 방식으로 바꾼다. 화면 코드는 이 클래스만 바꾸면 된다.
public class SearchRepository {

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    private final FirebaseFirestore db;
    private final VideoRepository videoRepository;

    // 한 번 받아 둔 후보. null이면 아직 안 받음
    private List<DocumentSnapshot> users;
    private List<Video> ratedVideos;

    public SearchRepository() {
        this(FirebaseFirestore.getInstance(), new VideoRepository());
    }

    SearchRepository(FirebaseFirestore db, VideoRepository videoRepository) {
        this.db = db;
        this.videoRepository = videoRepository;
    }

    // 후보를 다시 받게 한다 (당겨서 새로고침 등)
    public void clearCache() {
        users = null;
        ratedVideos = null;
    }

    // ---------- 사용자 ----------

    // 이름이나 아이디에 검색어가 들어 있는 사람 모두 (나는 제외).
    // 줄마다 공개 평가 수와 내가 팔로우 중인지를 채운다 (팔로우 목록과 같은 FollowListItem)
    public void searchUsers(@NonNull String query, @NonNull String myUid,
                            @NonNull Callback<List<FollowListItem>> callback) {
        String needle = normalize(query);
        loadUsers().continueWithTask(task -> {
            List<DocumentSnapshot> matched = new ArrayList<>();
            for (DocumentSnapshot user : task.getResult()) {
                if (user.getId().equals(myUid)) {
                    continue;
                }
                if (normalize(user.getString("displayName")).contains(needle)
                        || normalize(user.getString("username")).contains(needle)) {
                    matched.add(user);
                }
            }
            return buildUserItems(matched, myUid);
        })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    private Task<List<DocumentSnapshot>> loadUsers() {
        if (users != null) {
            return Tasks.forResult(users);
        }
        return db.collection(FirestorePaths.USERS).get().continueWith(task -> {
            users = new ArrayList<>(task.getResult().getDocuments());
            return users;
        });
    }

    // 공개 평가 수(사람마다 count 1번)와 내 팔로잉 목록을 함께 받아 한 줄씩 만든다
    private Task<List<FollowListItem>> buildUserItems(List<DocumentSnapshot> matched, String myUid) {
        if (matched.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }
        Task<QuerySnapshot> followingTask = db.collection(FirestorePaths.FOLLOWS)
                .whereEqualTo("followerId", myUid)
                .get();
        List<Task<AggregateQuerySnapshot>> countTasks = new ArrayList<>();
        for (DocumentSnapshot user : matched) {
            countTasks.add(db.collection(FirestorePaths.RATINGS)
                    .whereEqualTo("userId", user.getId())
                    .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC)
                    .count().get(AggregateSource.SERVER));
        }
        List<Task<?>> all = new ArrayList<>(countTasks);
        all.add(followingTask);
        return Tasks.whenAll(all).continueWith(done -> {
            if (!done.isSuccessful()) {
                throw done.getException() != null ? done.getException() : new IllegalStateException("search failed");
            }
            Set<String> myFollowing = new HashSet<>();
            for (DocumentSnapshot follow : followingTask.getResult().getDocuments()) {
                String id = follow.getString("followingId");
                if (id != null) {
                    myFollowing.add(id);
                }
            }
            List<FollowListItem> items = new ArrayList<>();
            for (int i = 0; i < matched.size(); i++) {
                DocumentSnapshot user = matched.get(i);
                items.add(new FollowListItem(
                        user.getId(),
                        user.getString("displayName"),
                        user.getString("username"),
                        user.getString("photoUrl"),
                        countTasks.get(i).getResult().getCount(),
                        myFollowing.contains(user.getId())));
            }
            return items;
        });
    }

    // ---------- 서비스에 등록된 영상 ----------

    // 우리 앱에서 공개 평가가 있는 영상 중 제목·채널 이름에 검색어가 들어 있는 것 모두 (평가 수 많은 순).
    // 영상 정보는 VideoRepository.getVideos로 받아 30일 규칙을 그대로 따르고, 평균 별점도 함께 들어 있다
    public void searchRegisteredVideos(@NonNull String query, @NonNull Callback<List<Video>> callback) {
        String needle = normalize(query);
        loadRatedVideos().addOnSuccessListener(rated -> {
            List<String> ids = new ArrayList<>();
            for (Video video : rated) {
                if (normalize(video.getTitle()).contains(needle)
                        || normalize(video.getChannelTitle()).contains(needle)) {
                    ids.add(video.getVideoId());
                }
            }
            videoRepository.getVideos(ids, new VideoRepository.Callback<List<Video>>() {
                @Override
                public void onSuccess(List<Video> result) {
                    callback.onSuccess(result);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    callback.onError(error);
                }
            });
        }).addOnFailureListener(callback::onError);
    }

    private Task<List<Video>> loadRatedVideos() {
        if (ratedVideos != null) {
            return Tasks.forResult(ratedVideos);
        }
        return db.collection(FirestorePaths.VIDEOS)
                .whereGreaterThan("publicRatingCount", 0)
                .get()
                .continueWith(task -> {
                    List<Video> list = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        Video video = document.toObject(Video.class);
                        if (video != null && video.getTitle() != null) {
                            list.add(video);
                        }
                    }
                    list.sort((a, b) -> Long.compare(b.getPublicRatingCount(), a.getPublicRatingCount()));
                    ratedVideos = list;
                    return list;
                });
    }

    // 대소문자·앞뒤 공백을 무시하고 비교한다
    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
