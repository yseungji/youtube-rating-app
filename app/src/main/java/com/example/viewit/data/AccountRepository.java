package com.example.viewit.data;

import android.app.Activity;
import android.os.CancellationSignal;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.example.viewit.R;
import com.example.viewit.data.model.FirestorePaths;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseUserMetadata;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.function.IntConsumer;

// 로그아웃·회원 탈퇴 (6.3). 화면(SettingsActivity)은 결과만 콜백으로 받아 안내와 화면 이동을 한다.
public class AccountRepository {

    private static final String TAG = "AccountRepository";

    // Firebase는 계정 삭제 같은 민감한 작업에 "최근 로그인"을 요구한다(대략 몇 분 이내).
    // 미리 확인해서, 프로필만 지워지고 계정 삭제는 실패하는 상황을 줄인다.
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
    // 성공하면 onSuccess, 실패하면 onFailure(안내할 문자열 ID)를 메인 스레드에서 부른다.
    // 내 평가·하위 컬렉션·프로필 사진 정리는 6.4(탈퇴 데이터 정리 함수)에서 한다.
    public void deleteAccount(@NonNull Activity activity,
                              @NonNull Runnable onSuccess,
                              @NonNull IntConsumer onFailure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onFailure.accept(R.string.delete_account_failed);
            return;
        }
        if (!signedInRecently(user)) {
            onFailure.accept(R.string.delete_account_relogin);
            return;
        }

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

    private void clearCredentials(Activity activity, Runnable onDone) {
        CredentialManager.create(activity).clearCredentialStateAsync(
            new ClearCredentialStateRequest(),
            new CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            new CredentialManagerCallback<Void, ClearCredentialException>() {
                @Override
                public void onResult(Void unused) {
                    onDone.run();
                }

                @Override
                public void onError(@NonNull ClearCredentialException e) {
                    Log.w(TAG, "clear credential state failed", e);
                    onDone.run();   // 로그아웃 자체는 이미 끝났으므로 그대로 진행
                }
            });
    }

    private static Exception orDefault(Exception e) {
        return e != null ? e : new IllegalStateException("task failed without exception");
    }
}
