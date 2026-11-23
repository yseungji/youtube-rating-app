package com.example.viewit.data;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.User;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

// 프로필 편집 (6.3). 불러오기, 사진 업로드, 저장(아이디를 바꾸면 usernames까지 한 트랜잭션).
public class ProfileRepository {

    private static final String PROFILE_IMAGES_DIR = "profileImages";   // 3.2와 같은 폴더·파일 이름

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(@NonNull Exception error);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 다른 사람이 쓰는 아이디로 바꾸려 해서 저장이 거절됐는지
    public static boolean isUsernameTaken(Exception e) {
        return e instanceof FirebaseFirestoreException
            && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ALREADY_EXISTS;
    }

    public void loadProfile(@NonNull String uid, @NonNull Callback<User> callback) {
        db.collection(FirestorePaths.USERS).document(uid).get()
            .addOnSuccessListener(doc -> {
                User user = doc.toObject(User.class);
                if (user == null) {
                    callback.onError(new IllegalStateException("profile not found"));
                } else {
                    callback.onSuccess(user);
                }
            })
            .addOnFailureListener(callback::onError);
    }

    // 사진을 512×512 JPEG로 줄여 profileImages/{uid}.jpg에 덮어쓰고 다운로드 주소를 돌려준다 (3.2와 같은 방식)
    public void uploadPhoto(@NonNull Activity activity, @NonNull String uid, @NonNull Uri uri,
                            @NonNull Callback<String> callback) {
        Glide.with(activity)
            .asBitmap()
            .load(uri)
            .override(512, 512)
            .centerCrop()
            .into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                    StorageReference ref = FirebaseStorage.getInstance().getReference()
                        .child(PROFILE_IMAGES_DIR + "/" + uid + ".jpg");
                    StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
                    ref.putBytes(out.toByteArray(), metadata)
                        .continueWithTask(task -> {
                            if (!task.isSuccessful()) {
                                throw task.getException();
                            }
                            return ref.getDownloadUrl();
                        })
                        .addOnSuccessListener(downloadUri -> callback.onSuccess(downloadUri.toString()))
                        .addOnFailureListener(callback::onError);
                }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    callback.onError(new IllegalStateException("cannot open photo"));
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                    // CustomTarget 필수 메서드. 쓸 일 없음
                }
            });
    }

    // 이름·아이디·소개(·사진 주소)를 저장한다. 사진을 바꾸지 않았으면 photoUrl은 null로 넘긴다.
    // 아이디가 바뀌면 새 아이디를 예약하고(다른 사람 것이면 ALREADY_EXISTS) 옛 아이디 예약을 지운다.
    public void saveProfile(@NonNull String uid, @Nullable String oldUsername,
                            @NonNull String name, @NonNull String username, @NonNull String intro,
                            @Nullable String photoUrl, @NonNull Callback<Void> callback) {
        DocumentReference userRef = db.collection(FirestorePaths.USERS).document(uid);
        boolean usernameChanged = !username.equals(oldUsername);
        DocumentReference newNameRef = db.collection(FirestorePaths.USERNAMES).document(username);

        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("displayName", name);
        userUpdate.put("username", username);
        userUpdate.put("introduction", intro);
        if (photoUrl != null) {
            userUpdate.put("photoUrl", photoUrl);
        }
        userUpdate.put("updatedAt", FieldValue.serverTimestamp());

        db.runTransaction(transaction -> {
                if (usernameChanged) {
                    // 트랜잭션에서는 읽기를 쓰기보다 먼저 한다
                    DocumentSnapshot existing = transaction.get(newNameRef);
                    if (existing.exists() && !uid.equals(existing.getString("uid"))) {
                        throw new FirebaseFirestoreException("username already taken",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS);
                    }
                    Map<String, Object> reservation = new HashMap<>();
                    reservation.put("uid", uid);
                    reservation.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(newNameRef, reservation);
                    if (!TextUtils.isEmpty(oldUsername)) {
                        transaction.delete(db.collection(FirestorePaths.USERNAMES).document(oldUsername));
                    }
                }
                transaction.update(userRef, userUpdate);
                return null;
            })
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(callback::onError);
    }
}
