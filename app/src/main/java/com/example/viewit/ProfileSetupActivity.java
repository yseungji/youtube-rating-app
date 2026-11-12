package com.example.viewit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.util.Consumer;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.ActivityProfileSetupBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import java.util.Locale;
import java.util.Map;

// 온보딩 B · 프로필 만들기 (3.2). 이름·아이디·한줄 소개와 앨범 사진을 받아 users/{uid}를 만든다.
public class ProfileSetupActivity extends AppCompatActivity {

    private static final String TAG = "ProfileSetup";
    // Storage에서 프로필 사진을 두는 폴더. 사용자마다 {uid}.jpg 하나만 두고 다시 올리면 덮어쓴다.
    private static final String PROFILE_IMAGES_DIR = "profileImages";

    private ActivityProfileSetupBinding binding;
    private Uri selectedPhotoUri;   // 처음엔 null = 사진 안 고름

    // 앨범(Photo Picker)에서 사진 하나를 고른다. 권한 요청 없이 동작한다.
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
        registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) {
                return;                 // 사용자가 그냥 닫음
            }
            selectedPhotoUri = uri;
            // 업로드는 "다음"을 누를 때 한다. 여기서는 고른 사진을 보여 주기만 한다.
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(binding.avatarImage);
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.avatarImage.setOnClickListener(v -> pickPhoto.launch(
            new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.profileNextButton.setOnClickListener(v -> onNextClicked());
        // 온보딩 첫 화면이라 돌아갈 곳은 로그인뿐이다. 로그아웃하고 시작 화면으로 보낸다.
        binding.profileBackButton.getRoot().setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            openAndClear(GoogleSignInActivity.class);
        });
    }

    private void onNextClicked() {
        // 1. 이전 오류 문구 지우기
        binding.nameLayout.setError(null);
        binding.usernameLayout.setError(null);
        binding.introLayout.setError(null);

        // 2. 입력값 꺼내기
        String name = binding.nameInput.getText().toString().trim();
        String username = binding.usernameInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String intro = binding.introInput.getText().toString().trim();

        // 3. 검사: 하나라도 틀리면 오류 문구를 띄우고 return으로 끝냄
        if (name.isEmpty()) {
            binding.nameLayout.setError(getString(R.string.profile_setup_error_name));
            return;
        }
        if (!username.matches("^[a-z0-9_]{3,20}$")) {
            binding.usernameLayout.setError(getString(R.string.profile_setup_error_username));
            return;
        }
        if (intro.length() > 40) {
            binding.introLayout.setError(getString(R.string.profile_setup_error_intro));
            return;
        }

        // 4. 통과: 버튼을 막아 두 번 눌리지 않게
        binding.profileNextButton.setEnabled(false);

        // 5. 저장 (사진 있으면 업로드 먼저)
        if (selectedPhotoUri == null) {
            saveProfile(name, username, intro, "");
        } else {
            uploadPhoto(selectedPhotoUri, photoUrl -> saveProfile(name, username, intro, photoUrl));
        }
    }

    // 사진을 512×512 JPEG로 줄여 Storage profileImages/{uid}.jpg에 올리고, 받은 주소를 넘긴다.
    private void uploadPhoto(Uri uri, Consumer<String> onUploaded) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // 1. 사진을 512×512로 줄여서 Bitmap으로 받기
        Glide.with(this)
            .asBitmap()
            .load(uri)
            .override(512, 512)
            .centerCrop()
            .into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap bitmap,
                                            @Nullable Transition<? super Bitmap> transition) {
                    // 2. JPEG로 압축해서 바이트로 만들기
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                    byte[] data = out.toByteArray();

                    // 3. Storage에 올리기
                    StorageReference ref = FirebaseStorage.getInstance().getReference()
                        .child(PROFILE_IMAGES_DIR + "/" + uid + ".jpg");
                    StorageMetadata metadata = new StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build();

                    ref.putBytes(data, metadata)
                        // 4. 올리기가 끝나면 주소 요청
                        .continueWithTask(task -> {
                            if (!task.isSuccessful()) {
                                throw task.getException();
                            }
                            return ref.getDownloadUrl();
                        })
                        .addOnSuccessListener(downloadUri ->
                            onUploaded.accept(downloadUri.toString()))
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "profile photo upload failed", e);
                            Toast.makeText(ProfileSetupActivity.this,
                                R.string.profile_setup_error_upload, Toast.LENGTH_SHORT).show();
                            binding.profileNextButton.setEnabled(true);
                        });
                }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    // 고른 사진을 열지 못함
                    Toast.makeText(ProfileSetupActivity.this,
                        R.string.profile_setup_error_photo, Toast.LENGTH_SHORT).show();
                    binding.profileNextButton.setEnabled(true);
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                    // 쓸 일 없음
                }
            });
    }

    // 아이디 예약(usernames/{username})과 프로필(users/{uid})을 한 트랜잭션으로 만든다.
    // 둘 중 하나만 저장되는 일이 없고, 같은 아이디를 동시에 고른 두 사람 중 한 명만 성공한다.
    private void saveProfile(String name, String username, String intro, String photoUrl) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            openAndClear(GoogleSignInActivity.class);
            return;
        }
        String uid = currentUser.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference usernameRef = db.collection(FirestorePaths.USERNAMES).document(username);
        DocumentReference userRef = db.collection(FirestorePaths.USERS).document(uid);

        // 개수 필드는 0, 관심 카테고리는 빈 목록(3.3에서 채움), createdAt·updatedAt은 서버 시각(@ServerTimestamp)
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(name);
        user.setIntroduction(intro);
        user.setPhotoUrl(photoUrl);

        Map<String, Object> usernameDoc = new HashMap<>();
        usernameDoc.put("uid", uid);
        usernameDoc.put("createdAt", FieldValue.serverTimestamp());

        db.runTransaction(transaction -> {
                // 트랜잭션에서는 읽기를 쓰기보다 먼저 한다.
                DocumentSnapshot existing = transaction.get(usernameRef);
                // 다른 사람이 쓰는 아이디면 막는다. 내 uid면 재시도로 보고 통과시킨다.
                if (existing.exists() && !uid.equals(existing.getString("uid"))) {
                    throw new FirebaseFirestoreException("username already taken",
                        FirebaseFirestoreException.Code.ALREADY_EXISTS);
                }
                transaction.set(usernameRef, usernameDoc);
                transaction.set(userRef, user);
                return null;
            })
            .addOnSuccessListener(unused -> openAndClear(MainActivity.class))
            .addOnFailureListener(e -> {
                binding.profileNextButton.setEnabled(true);
                if (e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode()
                    == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                    binding.usernameLayout.setError(getString(R.string.profile_setup_error_username_taken));
                } else {
                    Log.w(TAG, "profile save failed", e);
                    Toast.makeText(this, R.string.profile_setup_error_save, Toast.LENGTH_SHORT).show();
                }
            });
    }

    // 다음 화면으로 가면서 뒤로가기 기록을 지운다. (프로필 화면으로 돌아오지 않게)
    private void openAndClear(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
