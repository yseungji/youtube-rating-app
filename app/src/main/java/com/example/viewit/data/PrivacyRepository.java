package com.example.viewit.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Follow;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

// 보관함·최근 본 영상 공개 범위 (6.3). users/{uid}/settings/privacy 문서 하나.
// 값은 public(전체 공개) / followers(팔로워만) / private(나만 보기). 문서나 값이 없으면 전체 공개로 본다.
// 실제로 막는 것은 firestore.rules(watchLater·watchHistory 읽기)이고, 이 클래스는 화면에 보일 탭을 정한다.
public class PrivacyRepository {

    private static final String TAG = "PrivacyRepository";
    private static final String DOC_PRIVACY = "privacy";

    public static final String PUBLIC = "public";
    public static final String FOLLOWERS = "followers";
    public static final String PRIVATE = "private";
    public static final String DEFAULT = PUBLIC;

    public static final String FIELD_WATCH_LATER = "watchLaterVisibility";
    public static final String FIELD_WATCH_HISTORY = "watchHistoryVisibility";

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    // 한 사람의 공개 범위 두 가지
    public static class Settings {
        public final String watchLater;
        public final String watchHistory;

        Settings(String watchLater, String watchHistory) {
            this.watchLater = watchLater;
            this.watchHistory = watchHistory;
        }
    }

    // 다른 사람 프로필에서 보여도 되는 탭
    public static class VisibleLists {
        public final boolean watchLater;
        public final boolean watchHistory;

        VisibleLists(boolean watchLater, boolean watchHistory) {
            this.watchLater = watchLater;
            this.watchHistory = watchHistory;
        }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // uid의 공개 범위 (없으면 전체 공개)
    public void load(@NonNull String uid, @NonNull Callback<Settings> callback) {
        privacyRef(uid).get()
            .addOnSuccessListener(doc -> callback.onSuccess(toSettings(doc)))
            .addOnFailureListener(e -> {
                Log.w(TAG, "privacy load failed", e);
                callback.onError(e);
            });
    }

    // 내 공개 범위 하나 바꾸기 (field: FIELD_WATCH_LATER 또는 FIELD_WATCH_HISTORY)
    public void save(@NonNull String myUid, @NonNull String field, @NonNull String value,
                     @NonNull Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put(field, value);
        data.put("updatedAt", FieldValue.serverTimestamp());
        privacyRef(myUid).set(data, SetOptions.merge())
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(e -> {
                Log.w(TAG, "privacy save failed", e);
                callback.onError(e);
            });
    }

    // viewer가 owner 프로필에서 볼 수 있는 탭. 팔로워만이면 viewer가 owner를 팔로우하는지도 본다.
    // owner가 viewer를 차단했는지는 앱에서 알 수 없어서(차단 목록은 본인만 읽음) 그때는 목록 조회가 거절된다.
    public void loadVisibleLists(@NonNull String viewerUid, @NonNull String ownerUid,
                                 @NonNull Callback<VisibleLists> callback) {
        Task<DocumentSnapshot> privacyTask = privacyRef(ownerUid).get();
        Task<DocumentSnapshot> followTask = db.collection(FirestorePaths.FOLLOWS)
            .document(Follow.documentId(viewerUid, ownerUid)).get();
        Tasks.whenAllSuccess(privacyTask, followTask)
            .addOnSuccessListener(results -> {
                Settings settings = toSettings(privacyTask.getResult());
                boolean following = followTask.getResult().exists();
                callback.onSuccess(new VisibleLists(
                    canSee(settings.watchLater, following),
                    canSee(settings.watchHistory, following)));
            })
            .addOnFailureListener(e -> {
                Log.w(TAG, "visible lists failed", e);
                callback.onError(e);
            });
    }

    private static boolean canSee(String visibility, boolean following) {
        return PUBLIC.equals(visibility) || (FOLLOWERS.equals(visibility) && following);
    }

    private static Settings toSettings(@Nullable DocumentSnapshot doc) {
        return new Settings(valueOf(doc, FIELD_WATCH_LATER), valueOf(doc, FIELD_WATCH_HISTORY));
    }

    private static String valueOf(@Nullable DocumentSnapshot doc, String field) {
        String value = doc == null || !doc.exists() ? null : doc.getString(field);
        if (PUBLIC.equals(value) || FOLLOWERS.equals(value) || PRIVATE.equals(value)) {
            return value;
        }
        return DEFAULT;
    }

    private DocumentReference privacyRef(String uid) {
        return db.collection(FirestorePaths.USERS).document(uid)
            .collection(FirestorePaths.SETTINGS).document(DOC_PRIVACY);
    }
}
