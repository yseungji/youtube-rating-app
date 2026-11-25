package com.example.viewit.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.viewit.R;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.RatedVideoItem;
import com.example.viewit.data.model.Video;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 프로필 "보관함"·"최근 본 영상" 탭 목록 (6.1).
// users/{uid}/watchLater (5.4 나중에 보기), users/{uid}/watchHistory (5.1 앱에서 본 영상)에서
// 영상 ID를 최신순으로 읽고, VideoRepository로 제목·썸네일을 채워 한 줄(RatedVideoItem)로 만든다.
// 지금은 내 프로필에서만 쓴다 (다른 사람 것은 6.3 공개 범위 이후).
public class ProfileVideoLoader {

    public interface Callback {
        void onLoaded(@NonNull List<RatedVideoItem> items);

        void onError(@NonNull Exception error);
    }

    private static final String TAG = "ProfileVideoLoader";
    private static final int LIMIT = 50;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final VideoRepository videoRepository = new VideoRepository();

    // watchLater가 true면 보관함, false면 최근 본 영상
    public void load(@NonNull Context context, boolean watchLater, @NonNull Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onLoaded(new ArrayList<>());
            return;
        }
        final Context appContext = context.getApplicationContext();
        String collection = watchLater ? FirestorePaths.WATCH_LATER : FirestorePaths.WATCH_HISTORY;
        final String timeField = watchLater ? "createdAt" : "lastWatchedAt";

        db.collection(FirestorePaths.USERS).document(user.getUid())
                .collection(collection)
                .orderBy(timeField, Query.Direction.DESCENDING)
                .limit(LIMIT)
                .get()
                .addOnSuccessListener(result -> {
                    final List<String> videoIds = new ArrayList<>();
                    final Map<String, Timestamp> timeById = new HashMap<>();
                    for (DocumentSnapshot doc : result.getDocuments()) {
                        videoIds.add(doc.getId());
                        timeById.put(doc.getId(), doc.getTimestamp(timeField));
                    }
                    if (videoIds.isEmpty()) {
                        callback.onLoaded(new ArrayList<>());
                        return;
                    }
                    videoRepository.getVideos(videoIds, new VideoRepository.Callback<List<Video>>() {
                        @Override
                        public void onSuccess(List<Video> videos) {
                            callback.onLoaded(buildItems(appContext, watchLater, videoIds, timeById, videos));
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            // 영상 정보를 못 받아도 목록은 보여 준다 (제목 대신 안내, 기본 썸네일)
                            Log.w(TAG, "video info failed", error);
                            callback.onLoaded(buildItems(appContext, watchLater, videoIds, timeById,
                                    new ArrayList<>()));
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "list load failed: " + collection, e);
                    callback.onError(e);
                });
    }

    // 저장한 순서(최신순) 그대로 한 줄씩 만든다. 메타: "채널 · 3일 전 보관" / "채널 · 어제 시청"
    private List<RatedVideoItem> buildItems(Context context, boolean watchLater, List<String> videoIds,
                                            Map<String, Timestamp> timeById, List<Video> videos) {
        Map<String, Video> byId = new HashMap<>();
        for (Video video : videos) {
            byId.put(video.getVideoId(), video);
        }
        Date now = new Date();
        List<RatedVideoItem> items = new ArrayList<>();
        for (String videoId : videoIds) {
            Video video = byId.get(videoId);
            String title = video != null && !TextUtils.isEmpty(video.getTitle())
                    ? video.getTitle() : context.getString(R.string.rated_unknown_video);
            String thumbnail = video != null && !TextUtils.isEmpty(video.getThumbnailUrl())
                    ? video.getThumbnailUrl() : "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
            String categoryId = video == null ? null : video.getCategoryId();
            String channel = video == null || video.getChannelTitle() == null ? "" : video.getChannelTitle().trim();

            Timestamp time = timeById.get(videoId);
            Date at = time == null ? now : time.toDate();
            String when = TimeAgoFormat.format(context, at, now);
            String meta = channel.isEmpty()
                    ? context.getString(watchLater ? R.string.profile_list_saved_time_only
                            : R.string.rate_watched_time_only, when)
                    : context.getString(watchLater ? R.string.profile_list_saved_meta
                            : R.string.rate_watched_meta, channel, when);

            items.add(new RatedVideoItem(videoId, categoryId, title, thumbnail, meta, 0, at));
        }
        return items;
    }
}
