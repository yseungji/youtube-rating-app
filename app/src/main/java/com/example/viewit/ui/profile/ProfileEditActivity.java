package com.example.viewit.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.viewit.R;
import com.example.viewit.data.ProfileRepository;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.ActivityProfileSetupBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

// 프로필 편집 (6.3). 화면은 3.2 프로필 만들기(activity_profile_setup.xml)를 그대로 쓰고,
// 지금 값을 채운 뒤 바뀐 것만 저장한다.
public class ProfileEditActivity extends AppCompatActivity {

    private static final String TAG = "ProfileEdit";

    private ActivityProfileSetupBinding binding;
    private final ProfileRepository repository = new ProfileRepository();
    private String uid;
    private User original;          // 처음 불러온 프로필 (바뀌었는지 비교용)
    private Uri selectedPhotoUri;   // 새로 고른 사진 (안 골랐으면 null)

    // 앨범에서 사진 하나 고르기 (3.2와 같음)
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
        registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) {
                return;
            }
            selectedPhotoUri = uri;
            Glide.with(this).load(uri).circleCrop().into(binding.avatarImage);
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());   // 3.2 화면 재사용
        setContentView(binding.getRoot());
        setupScreen();   // 편집용 글자로 바꾸고 지금 프로필 불러오기

        binding.profileBackButton.getRoot().setOnClickListener(v -> finish());
        binding.profileNextButton.setOnClickListener(v -> onSaveClicked());
    }

    // 불러온 프로필로 입력칸 채우기 (사진은 setupScreen이 넣음)
    private void showCurrent(User user) {
        binding.nameInput.setText(user.getDisplayName());
        binding.usernameInput.setText(user.getUsername());
        binding.introInput.setText(user.getIntroduction());
    }

    // 저장 버튼: 3.2와 같은 입력 검사 후 저장
    private void onSaveClicked() {
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
        save(name, username, intro);
    }

    // 저장이 끝나면 안내하고 닫기 (프로필 탭은 실시간으로 바뀐다)
    private void onSaved() {
        Toast.makeText(this, R.string.profile_edit_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------- 화면 준비·저장 ----------

    private void setupScreen() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        // 온보딩 표시를 숨기고 편집용 글자로
        binding.onboardingProgress.setVisibility(View.GONE);
        binding.stepText.setVisibility(View.GONE);
        binding.setupTitle.setText(R.string.profile_edit_title);
        binding.setupSubtitle.setText(R.string.profile_edit_subtitle);
        binding.profileNextButton.setText(R.string.profile_edit_save);
        binding.avatarImage.setOnClickListener(v -> pickPhoto.launch(
            new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        uid = user.getUid();
        binding.profileNextButton.setEnabled(false);   // 불러오기 전에는 저장 막기
        repository.loadProfile(uid, new ProfileRepository.Callback<User>() {
            @Override
            public void onSuccess(User result) {
                original = result;
                Glide.with(ProfileEditActivity.this)
                    .load(result.getPhotoUrl())
                    .placeholder(R.drawable.avatar_default)
                    .error(R.drawable.avatar_default)
                    .fallback(R.drawable.avatar_default)
                    .circleCrop()
                    .into(binding.avatarImage);
                showCurrent(result);
                binding.profileNextButton.setEnabled(true);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "load profile failed", error);
                Toast.makeText(ProfileEditActivity.this, R.string.profile_edit_load_failed, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // 바뀐 게 없으면 그냥 닫고, 사진을 새로 골랐으면 먼저 올린 뒤 저장한다
    private void save(String name, String username, String intro) {
        if (original == null) {
            return;
        }
        boolean changed = selectedPhotoUri != null
            || !name.equals(original.getDisplayName())
            || !username.equals(original.getUsername())
            || !intro.equals(original.getIntroduction() == null ? "" : original.getIntroduction());
        if (!changed) {
            finish();
            return;
        }
        binding.profileNextButton.setEnabled(false);   // 두 번 눌리지 않게
        if (selectedPhotoUri == null) {
            saveFields(name, username, intro, null);
            return;
        }
        repository.uploadPhoto(this, uid, selectedPhotoUri, new ProfileRepository.Callback<String>() {
            @Override
            public void onSuccess(String photoUrl) {
                saveFields(name, username, intro, photoUrl);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "photo upload failed", error);
                binding.profileNextButton.setEnabled(true);
                Toast.makeText(ProfileEditActivity.this, R.string.profile_setup_error_upload, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveFields(String name, String username, String intro, String photoUrl) {
        repository.saveProfile(uid, original.getUsername(), name, username, intro, photoUrl,
            new ProfileRepository.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    onSaved();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    binding.profileNextButton.setEnabled(true);
                    if (ProfileRepository.isUsernameTaken(error)) {
                        binding.usernameLayout.setError(getString(R.string.profile_setup_error_username_taken));
                    } else {
                        Log.w(TAG, "save profile failed", error);
                        Toast.makeText(ProfileEditActivity.this, R.string.profile_setup_error_save, Toast.LENGTH_SHORT).show();
                    }
                }
            });
    }
}
