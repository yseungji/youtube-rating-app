package com.example.viewit.data.youtube;

import android.accounts.Account;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Video;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// YouTube 계정 연동 (6.3). 연동·해제와, 연동한 계정의 구독 채널 새 영상·좋아요 영상 불러오기.
//
// - 권한: Google 권한 창에서 youtube.readonly(읽기 전용)만 받는다.
// - 토큰: 앱 메모리에서만 쓰고 Firestore에 저장하지 않는다. 필요할 때마다 Google Play 서비스에서 다시 받는다
//   (한 번 허용했으면 창 없이 바로 나온다).
// - Firestore에는 users/{uid}/integrations/youtube { connected, connectedAt, updatedAt } 만 남긴다.
// - 결과는 YouTubeLimits.CACHE_MILLIS 동안 메모리에 두고 다시 쓴다 (할당량 절약).
//
// 평가하기 탭(5.1) 사용 예:
//   repository.loadSubscriptionVideos(requireContext(), forceRefresh, new YouTubeApi.Callback<List<Video>>() {
//       public void onSuccess(List<Video> videos) { ... RateVideoItem으로 바꿔 표시 ... }
//       public void onError(YouTubeException e) {
//           // e.getReason() == AUTH_REQUIRED 이면 "설정에서 YouTube 계정을 연동해 주세요"
//           // 그 밖에는 YouTubeErrorMessage.of(context, e)
//       }
//   });
public final class YouTubeAccountRepository {

    // 연동 창 결과
    public interface ConnectCallback {
        void onConnected();

        void onCancelled();             // 사용자가 권한 창을 닫거나 거부함

        void onError(@NonNull Exception error);
    }

    public interface ErrorListener {
        void onError(@NonNull Exception error);
    }

    private static final String SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly";
    private static final String DOC_YOUTUBE = "youtube";
    private static final String FIELD_CONNECTED = "connected";
    private static final String FIELD_CONNECTED_AT = "connectedAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    // 메모리 캐시. 화면이 바뀌어도 유지되도록 static, 다른 계정으로 로그인하면 비운다
    private static String cacheUid;
    private static List<Video> cachedSubscriptions;
    private static long subscriptionsLoadedAt;
    private static List<Video> cachedLiked;
    private static long likedLoadedAt;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final YouTubeApi api = YouTubeApi.getInstance();

    // ---------- 연동 상태 ----------

    // users/{uid}/integrations/youtube 의 connected. 로그인 안 했거나 읽기 실패면 false
    public Task<Boolean> isConnected() {
        DocumentReference doc = integrationDoc();
        if (doc == null) {
            return Tasks.forResult(Boolean.FALSE);
        }
        return doc.get().continueWith(task ->
                task.isSuccessful() && task.getResult() != null
                        && Boolean.TRUE.equals(task.getResult().getBoolean(FIELD_CONNECTED)));
    }

    // ---------- 연동 ----------

    // 권한을 요청한다. 처음이면 launcher로 Google 권한 창을 띄우고, 결과는 handleConnectResult로 넘긴다.
    // 이미 허용한 적이 있으면 창 없이 바로 onConnected
    public void connect(@NonNull Activity activity,
                        @NonNull ActivityResultLauncher<IntentSenderRequest> launcher,
                        @NonNull ConnectCallback callback) {
        Identity.getAuthorizationClient(activity)
                .authorize(buildRequest())
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        PendingIntent pendingIntent = result.getPendingIntent();
                        if (pendingIntent == null) {
                            callback.onError(new YouTubeException(YouTubeException.Reason.AUTH_REQUIRED,
                                    "권한 창을 열 수 없습니다"));
                            return;
                        }
                        launcher.launch(new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build());
                    } else {
                        saveConnected(callback);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    // 권한 창(launcher)의 결과
    public void handleConnectResult(@NonNull Activity activity, @NonNull ActivityResult result,
                                    @NonNull ConnectCallback callback) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            callback.onCancelled();
            return;
        }
        try {
            AuthorizationResult authorization = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(result.getData());
            if (TextUtils.isEmpty(authorization.getAccessToken())) {
                callback.onCancelled();
            } else {
                saveConnected(callback);
            }
        } catch (ApiException e) {
            callback.onError(e);
        }
    }

    private void saveConnected(ConnectCallback callback) {
        DocumentReference doc = integrationDoc();
        if (doc == null) {
            callback.onError(new IllegalStateException("로그인이 필요합니다"));
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CONNECTED, true);
        data.put(FIELD_CONNECTED_AT, FieldValue.serverTimestamp());
        data.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        doc.set(data)
                .addOnSuccessListener(unused -> {
                    clearCache();
                    callback.onConnected();
                })
                .addOnFailureListener(callback::onError);
    }

    // ---------- 해제 ----------

    // Google에 권한 취소를 요청하고(가능하면), connected를 false로 바꾸고, 메모리 캐시를 지운다
    public void disconnect(@NonNull Context context, @NonNull Runnable onDone, @NonNull ErrorListener onError) {
        Identity.getAuthorizationClient(context)
                .authorize(buildRequest())
                .addOnCompleteListener(task -> {
                    String token = task.isSuccessful() && task.getResult() != null
                            && !task.getResult().hasResolution() ? task.getResult().getAccessToken() : null;
                    if (TextUtils.isEmpty(token)) {
                        markDisconnected(onDone, onError);
                    } else {
                        api.revokeToken(token, () -> markDisconnected(onDone, onError));
                    }
                });
    }

    private void markDisconnected(Runnable onDone, ErrorListener onError) {
        clearCache();
        DocumentReference doc = integrationDoc();
        if (doc == null) {
            onDone.run();
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CONNECTED, false);
        data.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        doc.set(data)
                .addOnSuccessListener(unused -> onDone.run())
                .addOnFailureListener(onError::onError);
    }

    // ---------- 영상 불러오기 (평가하기 탭 5.1) ----------

    // 구독 채널의 새 영상 (최신 업로드순, 최대 YouTubeLimits.SUBSCRIPTION_FEED_SIZE개).
    // forceRefresh가 false면 30분 안에 받은 결과를 다시 쓴다. 당겨서 새로고침일 때만 true
    public void loadSubscriptionVideos(@NonNull Context context, boolean forceRefresh,
                                       @NonNull YouTubeApi.Callback<List<Video>> callback) {
        String uid = currentUid();
        if (!forceRefresh && isFresh(uid, cachedSubscriptions, subscriptionsLoadedAt)) {
            callback.onSuccess(new ArrayList<>(cachedSubscriptions));
            return;
        }
        withAccessToken(context, callback, token -> api.fetchSubscriptionVideos(token,
                new YouTubeApi.Callback<List<Video>>() {
                    @Override
                    public void onSuccess(List<Video> videos) {
                        if (uid != null && uid.equals(currentUid())) {
                            rememberUid(uid);
                            cachedSubscriptions = Collections.unmodifiableList(new ArrayList<>(videos));
                            subscriptionsLoadedAt = SystemClock.elapsedRealtime();
                        }
                        callback.onSuccess(videos);
                    }

                    @Override
                    public void onError(YouTubeException error) {
                        callback.onError(error);
                    }
                }));
    }

    // YouTube에서 좋아요한 영상 (최근에 누른 순, 최대 YouTubeLimits.LIKED_VIDEOS개)
    public void loadLikedVideos(@NonNull Context context, boolean forceRefresh,
                                @NonNull YouTubeApi.Callback<List<Video>> callback) {
        String uid = currentUid();
        if (!forceRefresh && isFresh(uid, cachedLiked, likedLoadedAt)) {
            callback.onSuccess(new ArrayList<>(cachedLiked));
            return;
        }
        withAccessToken(context, callback, token -> api.fetchLikedVideos(token,
                new YouTubeApi.Callback<List<Video>>() {
                    @Override
                    public void onSuccess(List<Video> videos) {
                        if (uid != null && uid.equals(currentUid())) {
                            rememberUid(uid);
                            cachedLiked = Collections.unmodifiableList(new ArrayList<>(videos));
                            likedLoadedAt = SystemClock.elapsedRealtime();
                        }
                        callback.onSuccess(videos);
                    }

                    @Override
                    public void onError(YouTubeException error) {
                        callback.onError(error);
                    }
                }));
    }

    // ---------- 내부 ----------

    private interface TokenListener {
        void onToken(@NonNull String token);
    }

    // 창 없이 토큰을 받는다. 연동한 적이 없거나 권한이 취소되었으면 AUTH_REQUIRED (탭에서는 창을 띄우지 않음)
    private void withAccessToken(Context context, YouTubeApi.Callback<?> callback, TokenListener listener) {
        if (currentUid() == null) {
            callback.onError(new YouTubeException(YouTubeException.Reason.AUTH_REQUIRED, "로그인이 필요합니다"));
            return;
        }
        Identity.getAuthorizationClient(context)
                .authorize(buildRequest())
                .addOnSuccessListener(result -> {
                    String token = result.getAccessToken();
                    if (result.hasResolution() || TextUtils.isEmpty(token)) {
                        callback.onError(new YouTubeException(YouTubeException.Reason.AUTH_REQUIRED,
                                "YouTube 계정 연동이 필요합니다"));
                    } else {
                        listener.onToken(token);
                    }
                })
                .addOnFailureListener(e -> callback.onError(new YouTubeException(
                        YouTubeException.Reason.AUTH_REQUIRED, "YouTube 권한을 확인하지 못했습니다", e)));
    }

    // 로그인한 Google 계정으로 권한을 요청한다 (기기에 계정이 여러 개여도 같은 계정)
    private AuthorizationRequest buildRequest() {
        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE_YOUTUBE_READONLY)));
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user == null ? null : user.getEmail();
        if (!TextUtils.isEmpty(email)) {
            builder.setAccount(new Account(email, "com.google"));
        }
        return builder.build();
    }

    @Nullable
    private DocumentReference integrationDoc() {
        String uid = currentUid();
        if (uid == null) {
            return null;
        }
        return db.collection(FirestorePaths.USERS).document(uid)
                .collection(FirestorePaths.INTEGRATIONS).document(DOC_YOUTUBE);
    }

    @Nullable
    private static String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private static boolean isFresh(@Nullable String uid, @Nullable List<Video> cached, long loadedAt) {
        return uid != null && uid.equals(cacheUid) && cached != null
                && SystemClock.elapsedRealtime() - loadedAt < YouTubeLimits.CACHE_MILLIS;
    }

    // 다른 계정의 결과가 섞이지 않게, 계정이 바뀌면 캐시를 모두 비운다
    private static void rememberUid(@NonNull String uid) {
        if (!uid.equals(cacheUid)) {
            clearCache();
            cacheUid = uid;
        }
    }

    private static void clearCache() {
        cacheUid = null;
        cachedSubscriptions = null;
        cachedLiked = null;
    }
}
