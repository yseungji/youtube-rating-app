package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Video;
import com.example.viewit.data.youtube.YouTubeApi;
import com.example.viewit.data.youtube.YouTubeException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// 영상 정보를 Firestore videos에 저장해 두고 다시 쓰는 곳 (4.1).
// 1) videos/{영상ID}에 30일 안에 받은 정보가 있으면 그대로 쓴다 (YouTube 사용량 0)
// 2) 없거나 30일이 지났으면 YouTube에서 새로 받아 videos에 저장한다 (50개마다 1유닛)
// 3) YouTube에서 사라진(삭제·비공개) 영상은 저장해 둔 영상 정보 필드를 지운다. 평가 집계값은 남긴다
//
// 사용 예:
//   new VideoRepository().getVideo("영상ID", new VideoRepository.Callback<Video>() {
//       public void onSuccess(Video video) { ... }
//       public void onError(Exception e) { 안내 문구는 YouTubeErrorMessage.of(context, e) }
//   });
public class VideoRepository {

    private static final String TAG = "VideoRepository";
    private static final int MAX_IDS_PER_QUERY = 30;   // Firestore whereIn 한도
    private static final int MAX_WRITES_PER_BATCH = 450; // Firestore 한 번에 쓰기 한도(500)보다 여유 있게

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    private final CollectionReference videos;
    private final YouTubeApi youTubeApi;

    public VideoRepository() {
        this(FirebaseFirestore.getInstance(), YouTubeApi.getInstance());
    }

    VideoRepository(FirebaseFirestore db, YouTubeApi youTubeApi) {
        this.videos = db.collection(FirestorePaths.VIDEOS);
        this.youTubeApi = youTubeApi;
    }

    // 영상 하나. 없거나 YouTube에서 볼 수 없는 영상이면 YouTubeException(NOT_FOUND)
    public void getVideo(@NonNull String videoId, @NonNull Callback<Video> callback) {
        List<String> ids = new ArrayList<>();
        ids.add(videoId);
        getVideos(ids, new Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> result) {
                if (result.isEmpty()) {
                    callback.onError(new YouTubeException(YouTubeException.Reason.NOT_FOUND,
                            "영상을 찾을 수 없습니다: " + videoId));
                } else {
                    callback.onSuccess(result.get(0));
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }

    // 여러 영상. 결과는 요청한 순서대로이며, 찾을 수 없는 영상과 ID 모양이 아닌 값은 빠진다
    public void getVideos(@NonNull List<String> videoIds, @NonNull Callback<List<Video>> callback) {
        List<String> ids = new ArrayList<>();
        for (String id : new LinkedHashSet<>(videoIds)) {
            if (VideoCachePolicy.isValidVideoId(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        List<Task<QuerySnapshot>> reads = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += MAX_IDS_PER_QUERY) {
            List<String> chunk = ids.subList(start, Math.min(start + MAX_IDS_PER_QUERY, ids.size()));
            reads.add(videos.whereIn(FieldPath.documentId(), new ArrayList<>(chunk)).get());
        }
        Tasks.whenAllSuccess(reads)
                .addOnSuccessListener(results -> {
                    Map<String, Video> saved = new HashMap<>();
                    for (Object result : results) {
                        for (DocumentSnapshot document : ((QuerySnapshot) result).getDocuments()) {
                            Video video = document.toObject(Video.class);
                            if (video != null) {
                                saved.put(document.getId(), video);
                            }
                        }
                    }
                    useSavedOrFetch(ids, saved, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private void useSavedOrFetch(List<String> ids, Map<String, Video> saved, Callback<List<Video>> callback) {
        long now = System.currentTimeMillis();
        Map<String, Video> ready = new HashMap<>();
        List<String> toFetch = new ArrayList<>();
        for (String id : ids) {
            Video video = saved.get(id);
            if (VideoCachePolicy.isFresh(video, now)) {
                ready.put(id, video);
            } else {
                toFetch.add(id);
            }
        }
        if (toFetch.isEmpty()) {
            callback.onSuccess(inOrder(ids, ready));
            return;
        }

        youTubeApi.fetchVideos(toFetch, new YouTubeApi.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> fetched) {
                List<String> missing = new ArrayList<>(toFetch);
                for (Video video : fetched) {
                    VideoCachePolicy.copyStats(saved.get(video.getVideoId()), video);
                    ready.put(video.getVideoId(), video);
                    missing.remove(video.getVideoId());
                }
                save(fetched, missing, saved);
                callback.onSuccess(inOrder(ids, ready));
            }

            @Override
            public void onError(YouTubeException error) {
                // 30일 지난 정보는 다시 보여 주지 않는다. 새로 받지 못하면 오류로 알린다
                callback.onError(error);
            }
        });
    }

    // 새로 받은 정보는 merge로 저장하고, YouTube에서 사라진 영상은 저장해 둔 정보 필드를 지운다.
    // 저장이 실패해도 화면에는 이미 받은 정보를 보여 주므로 로그만 남긴다 (다음에 다시 받게 됨)
    private void save(List<Video> fetched, List<String> missing, Map<String, Video> saved) {
        Map<String, Object> clear = new HashMap<>();
        for (String field : VideoCachePolicy.INFO_FIELDS) {
            clear.put(field, FieldValue.delete());
        }
        WriteBatch batch = null;
        int count = 0;
        for (Video video : fetched) {
            batch = batchFor(batch, count++);
            batch.set(videos.document(video.getVideoId()), VideoCachePolicy.infoFields(video), SetOptions.merge());
        }
        for (String id : missing) {
            if (saved.containsKey(id)) { // 저장된 적 없는 영상은 지울 것도 없다
                batch = batchFor(batch, count++);
                batch.update(videos.document(id), clear);
            }
        }
        if (batch != null) {
            commit(batch);
        }
    }

    // 쓰기가 한도만큼 쌓이면 지금 묶음을 보내고 새 묶음을 시작한다
    private WriteBatch batchFor(WriteBatch current, int count) {
        if (current == null) {
            return videos.getFirestore().batch();
        }
        if (count % MAX_WRITES_PER_BATCH == 0) {
            commit(current);
            return videos.getFirestore().batch();
        }
        return current;
    }

    private static void commit(WriteBatch batch) {
        batch.commit().addOnFailureListener(e -> Log.w(TAG, "영상 정보 저장 실패", e));
    }

    private static List<Video> inOrder(List<String> ids, Map<String, Video> videosById) {
        List<Video> result = new ArrayList<>();
        for (String id : ids) {
            Video video = videosById.get(id);
            if (video != null) {
                result.add(video);
            }
        }
        return result;
    }
}
