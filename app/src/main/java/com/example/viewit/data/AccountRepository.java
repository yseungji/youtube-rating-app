package com.example.viewit.data;

import android.app.Activity;
import android.os.CancellationSignal;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.viewit.R;
import com.example.viewit.data.model.FirestorePaths;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseUserMetadata;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.function.IntConsumer;

// 로그아웃·회원 탈퇴 (6.3). 화면(SettingsActivity)은 결과만 콜백으로 받아 안내와 화면 이동을 한다.
public class AccountRepository {

    private static final String TAG = "AccountRepository";

    // Firebase는 계정 삭제 같은 민감한 작업에 "최근 로그인"을 요구한다(대략 몇 분 이내).
    // 그보다 오래됐으면 먼저 다시 인증해서, 프로필만 지워지고 계정 삭제는 실패하는 상황을 막는다.
    private static final long RECENT_LOGIN_MILLIS = 5 * 60 * 1000L;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 로그아웃: Firebase 로그아웃 + Credential Manager에 남은 Google 계정 선택 상태 지우기.
    // 끝나면(실패해도) onDone을 메인 스레드에서 부른다. 다음 로그인 때 계정 선택 창이 다시 뜬다.
    public void signOut(@NonNull Activity activity, @NonNull Runnable onDone) {
        auth.signOut();
        clearCredentials(activity, onDone);
    }

    // 회원 탈퇴: 프로필(users/{uid})과 아이디 예약(usernames/{username})을 지우고 Firebase 계정을 삭제한다.
    // 로그인한 지 오래됐으면 먼저 Google 계정 확인 창을 띄워 다시 인증(reauthenticate)한 뒤 진행한다.
    // 성공하면 onSuccess, 실패·취소하면 onFailure(안내할 문자열 ID)를 메인 스레드에서 부른다.
    // 내 평가·하위 컬렉션·프로필 사진 정리는 6.4(탈퇴 데이터 정리 함수)에서 한다.
    public void deleteAccount(@NonNull Activity activity,
                              @NonNull Runnable onSuccess,
                              @NonNull IntConsumer onFailure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onFailure.accept(R.string.delete_account_failed);
            return;
        }
        if (signedInRecently(user)) {
            deleteUserData(activity, user, onSuccess, onFailure);
        } else {
            reauthenticate(activity, user,
                () -> deleteUserData(activity, user, onSuccess, onFailure), onFailure);
        }
    }

    // Google 계정 확인 창 → 받은 ID 토큰으로 지금 사용자를 다시 인증한다.
    // 다른 계정을 고르면 Firebase가 거절한다(ERROR_USER_MISMATCH).
    private void reauthenticate(Activity activity, FirebaseUser user,
                                Runnable onDone, IntConsumer onFailure) {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build();

        CredentialManager.create(activity).getCredentialAsync(
            activity,
            request,
            new CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override
                public void onResult(GetCredentialResponse result) {
                    activity.runOnUiThread(() -> onCredential(result));
                }

                @Override
                public void onError(@NonNull GetCredentialException e) {
                    Log.w(TAG, "reauthenticate credential failed", e);
                    int messageRes = e instanceof GetCredentialCancellationException
                        ? R.string.delete_account_canceled
                        : R.string.delete_account_failed;
                    activity.runOnUiThread(() -> onFailure.accept(messageRes));
                }

                // 고른 계정의 ID 토큰으로 지금 사용자를 다시 인증한다 (메인 스레드).
                private void onCredential(GetCredentialResponse result) {
                    String idToken = googleIdToken(result.getCredential());
                    if (idToken == null) {
                        onFailure.accept(R.string.delete_account_failed);
                        return;
                    }
                    AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
                    user.reauthenticate(credential)
                        .addOnSuccessListener(activity, unused -> onDone.run())
                        .addOnFailureListener(activity, e -> {
                            Log.w(TAG, "reauthenticate failed", e);
                            if (e instanceof FirebaseAuthInvalidUserException) {
                                onFailure.accept(R.string.delete_account_wrong_account);
                            } else {
                                onFailure.accept(R.string.delete_account_failed);
                            }
                        });
                }
            });
    }

    // Credential Manager 결과에서 Google ID 토큰을 꺼낸다. Google 계정이 아니면 null.
    private static String googleIdToken(Credential credential) {
        if (credential instanceof CustomCredential
            && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            return GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
        }
        return null;
    }

    // 프로필·아이디 예약 삭제 → Firebase 계정 삭제 → Google 계정 선택 상태 지우기
    private void deleteUserData(Activity activity, FirebaseUser user,
                                Runnable onSuccess, IntConsumer onFailure) {
        DocumentReference userRef = db.collection(FirestorePaths.USERS).document(user.getUid());
        userRef.get()
            // 1) 프로필에서 아이디를 읽어 두 문서를 한 번에(batch) 삭제
            .continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw orDefault(task.getException());
                }
                String username = task.getResult() == null ? null : task.getResult().getString("username");
                WriteBatch batch = db.batch();
                batch.delete(userRef);
                if (!TextUtils.isEmpty(username)) {
                    batch.delete(db.collection(FirestorePaths.USERNAMES).document(username));
                }
                return batch.commit();
            })
            // 2) Firestore 삭제가 끝나면 Firebase 계정 삭제 (삭제 후에는 Firestore에 쓸 권한이 없어지므로 마지막에)
            .continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw orDefault(task.getException());
                }
                return user.delete();
            })
            .addOnSuccessListener(activity, unused -> clearCredentials(activity, onSuccess))
            .addOnFailureListener(activity, e -> {
                Log.w(TAG, "delete account failed", e);
                if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                    onFailure.accept(R.string.delete_account_relogin);
                } else {
                    onFailure.accept(R.string.delete_account_failed);
                }
            });
    }

    private boolean signedInRecently(FirebaseUser user) {
        FirebaseUserMetadata metadata = user.getMetadata();
        if (metadata == null) {
            return true;   // 알 수 없으면 일단 시도하고, 실패하면 RecentLoginRequired로 안내
        }
        return System.currentTimeMillis() - metadata.getLastSignInTimestamp() < RECENT_LOGIN_MILLIS;
    }

    // Android 14 이상 일부 기기에서는 executor를 넘겨도 이 콜백이 백그라운드(Binder) 스레드에서 불린다.
    // 그러면 화면 쪽에서 Toast·화면 이동을 할 때 앱이 죽으므로, onDone은 항상 메인 스레드로 넘긴다.
    private void clearCredentials(Activity activity, Runnable onDone) {
        CredentialManager.create(activity).clearCredentialStateAsync(
            new ClearCredentialStateRequest(),
            new CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            new CredentialManagerCallback<Void, ClearCredentialException>() {
                @Override
                public void onResult(Void unused) {
                    activity.runOnUiThread(onDone);
                }

                @Override
                public void onError(@NonNull ClearCredentialException e) {
                    Log.w(TAG, "clear credential state failed", e);
                    activity.runOnUiThread(onDone);   // 로그아웃 자체는 이미 끝났으므로 그대로 진행
                }
            });
    }

    private static Exception orDefault(Exception e) {
        return e != null ? e : new IllegalStateException("task failed without exception");
    }
}
