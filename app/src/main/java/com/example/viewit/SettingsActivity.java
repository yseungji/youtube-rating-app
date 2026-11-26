package com.example.viewit;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.data.AccountRepository;
import com.example.viewit.data.BlockRepository;
import com.example.viewit.data.youtube.YouTubeAccountRepository;
import com.example.viewit.databinding.ActivitySettingsBinding;
import com.example.viewit.databinding.ViewSettingsRowBinding;
import com.example.viewit.ui.profile.BlockedUsersActivity;
import com.example.viewit.ui.profile.ProfileEditActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// 설정 화면 (6.3, Figma 10 · 설정). 지금 동작하는 것은 프로필 편집·YouTube 연동·로그아웃·회원 탈퇴이고, 나머지 줄은 "준비 중" 안내.
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private ActivitySettingsBinding binding;
    private final AccountRepository account = new AccountRepository();   // 로그아웃·탈퇴 처리

    // ---------- YouTube 계정 연동 ----------
    private final YouTubeAccountRepository youtubeAccount = new YouTubeAccountRepository();
    private final BlockRepository blockRepository = new BlockRepository();   // 차단한 사용자 수 (6.3)
    private boolean youtubeConnected;   // 지금 연동되어 있는지 (화면 열 때 Firestore에서 읽음)

    private final YouTubeAccountRepository.ConnectCallback youtubeConnectCallback =
        new YouTubeAccountRepository.ConnectCallback() {
            @Override
            public void onConnected() {
                youtubeConnected = true;
                showYoutubeState();
                Toast.makeText(SettingsActivity.this, R.string.youtube_connect_done, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled() {
                Toast.makeText(SettingsActivity.this, R.string.youtube_connect_cancelled, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "YouTube 연동 실패", error);   // 원인은 Logcat에서 확인
                Toast.makeText(SettingsActivity.this, R.string.youtube_connect_failed, Toast.LENGTH_SHORT).show();
            }
        };

    // Google 권한 창. 닫히면 결과를 저장소에 넘기고, 결과는 youtubeConnectCallback으로 온다
    private final ActivityResultLauncher<IntentSenderRequest> youtubeLauncher =
        registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
            result -> youtubeAccount.handleConnectResult(this, result, youtubeConnectCallback));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarPadding();   // 상태바·내비게이션 바만큼 여백
        setupRows();               // 설정 줄 아이콘·글자 채우기, 아직 없는 기능은 "준비 중"
        loadYoutubeState();        // YouTube 연동 여부 읽어서 "연결됨/연결 안 됨" 표시

        binding.settingsBack.getRoot().setOnClickListener(v -> finish());
        binding.settingsLogout.setOnClickListener(v -> showLogoutDialog());
        binding.settingsDeleteAccount.setOnClickListener(v -> showDeleteDialog());
    }

    // 차단 목록에서 해제하고 돌아왔을 때 "N명"을 맞춘다
    @Override
    protected void onResume() {
        super.onResume();
        loadBlockedCount();
    }

    private void loadBlockedCount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        blockRepository.countBlocked(user.getUid(), new BlockRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long count) {
                binding.settingsBlocked.rowValue.setText(
                    getString(R.string.settings_blocked_count, count.intValue()));
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "blocked count failed", error);
            }
        });
    }

    // 로그아웃 확인창. 확인하면 로그아웃하고 로그인 화면으로 간다.
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.logout_dialog_title)
            .setMessage(R.string.logout_dialog_message)
            .setPositiveButton(R.string.settings_logout, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    account.signOut(SettingsActivity.this,() -> openLogin());
                }
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show();
    }

    // 회원 탈퇴 확인창. 성공하면 안내 후 로그인 화면으로, 실패하면 이유(messageRes)를 안내한다.
    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_dialog_title)
            .setMessage(R.string.delete_dialog_message)
            .setPositiveButton(R.string.delete_dialog_confirm, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    account.deleteAccount(SettingsActivity.this, ()-> {
                        Toast.makeText(SettingsActivity.this, R.string.delete_account_done, Toast.LENGTH_SHORT).show();
                        openLogin();
                    }, messageRes -> Toast.makeText(SettingsActivity.this, messageRes, Toast.LENGTH_SHORT).show());
                }
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show();
    }

    // 로그인 화면으로 가면서 이전 화면(탭·설정)을 모두 지운다. 뒤로가기로 돌아오지 않게.
    private void openLogin(){
        Intent intent = new Intent(this, GoogleSignInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ---------- YouTube 계정 연동 (6.3) ----------

    private void loadYoutubeState() {
        youtubeAccount.isConnected().addOnSuccessListener(connected -> {
            youtubeConnected = connected;
            showYoutubeState();
        });
    }

    // 연동 줄 오른쪽 값 글자 바꾸기
    private void showYoutubeState() {
        if(youtubeConnected)
            binding.settingsYoutube.rowValue.setText(R.string.settings_youtube_connected);
        else
            binding.settingsYoutube.rowValue.setText(R.string.settings_youtube_disconnected);
    }

    // 연동 줄을 눌렀을 때
    private void onYoutubeRowClick() {
        if(youtubeConnected)
            showYoutubeDisconnectDialog();
        else
            startYoutubeConnect();
        //   - 연동 안 됨 → startYoutubeConnect()
        //   - 연동됨 → showYoutubeDisconnectDialog()
    }

    // Google 권한 창 띄우기 (이미 허용했으면 창 없이 바로 youtubeConnectCallback.onConnected)
    private void startYoutubeConnect() {
        youtubeAccount.connect(this, youtubeLauncher, youtubeConnectCallback);
    }

    // 연동 해제 확인창
    private void showYoutubeDisconnectDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.youtube_disconnect_dialog_title)
            .setMessage(R.string.youtube_disconnect_dialog_message)
            .setPositiveButton(R.string.youtube_disconnect_confirm, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    disconnectYoutube();
                }
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show();
    }

    private void disconnectYoutube() {
        youtubeAccount.disconnect(this, () -> {
            youtubeConnected = false;
            showYoutubeState();
            Toast.makeText(this, R.string.youtube_disconnect_done, Toast.LENGTH_SHORT).show();
        }, error -> {
            Log.w(TAG, "YouTube 연동 해제 실패", error);
            Toast.makeText(this, R.string.youtube_disconnect_failed, Toast.LENGTH_SHORT).show();
        });
    }

    // ---------- 화면 채우기 ----------

    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void setupRows() {
        bindRow(binding.settingsProfileEdit, R.drawable.ic_person_outline,
            R.string.settings_profile_edit, R.string.settings_profile_edit_desc, null, true);
        bindRow(binding.settingsYoutube, R.drawable.ic_video_outline,
            R.string.settings_youtube, 0, getString(R.string.settings_youtube_disconnected), false);
        bindRow(binding.settingsMyActivity, R.drawable.ic_heart_outline,
            R.string.settings_my_activity, R.string.settings_my_activity_desc, null, true);
        bindRow(binding.settingsVisibility, R.drawable.ic_lock_outline,
            R.string.settings_visibility, R.string.settings_visibility_desc, null, true);
        bindRow(binding.settingsBlocked, R.drawable.ic_block,
            R.string.settings_blocked, 0, getString(R.string.settings_blocked_count, 0), true);
        bindRow(binding.settingsTerms, R.drawable.ic_description,
            R.string.settings_terms, 0, null, true);
        bindRow(binding.settingsPrivacyPolicy, R.drawable.ic_shield_check,
            R.string.settings_privacy_policy, 0, null, true);
        bindRow(binding.settingsAppVersion, R.drawable.ic_info_outline,
            R.string.settings_app_version, 0, BuildConfig.VERSION_NAME, false);

        // 아직 만들지 않은 기능 (내 활동·공개 범위·약관은 6.3 후속)
        View.OnClickListener comingSoon = v ->
            Toast.makeText(this, R.string.profile_coming_soon, Toast.LENGTH_SHORT).show();
        // 프로필 편집 화면 열기
        binding.settingsProfileEdit.getRoot().setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileEditActivity.class);
            startActivity(intent);
        });
        binding.settingsYoutube.getRoot().setOnClickListener(v -> onYoutubeRowClick());
        binding.settingsMyActivity.getRoot().setOnClickListener(comingSoon);
        binding.settingsVisibility.getRoot().setOnClickListener(comingSoon);
        // 차단한 사용자 목록 (6.3)
        binding.settingsBlocked.getRoot().setOnClickListener(v->{
            Intent intent = new Intent(this, BlockedUsersActivity.class);
            startActivity(intent);
        });
        binding.settingsTerms.getRoot().setOnClickListener(comingSoon);
        binding.settingsPrivacyPolicy.getRoot().setOnClickListener(comingSoon);
        binding.settingsAppVersion.getRoot().setClickable(false);   // 버전은 보기만
    }

    // 설정 한 줄 채우기. descriptionRes가 0이면 설명 줄을, value가 null이면 오른쪽 값을 숨긴다.
    private void bindRow(ViewSettingsRowBinding row, @DrawableRes int iconRes, @StringRes int labelRes,
                         @StringRes int descriptionRes, @Nullable String value, boolean showChevron) {
        row.rowIcon.setImageResource(iconRes);
        row.rowLabel.setText(labelRes);
        if (descriptionRes != 0) {
            row.rowDescription.setText(descriptionRes);
            row.rowDescription.setVisibility(View.VISIBLE);
        } else {
            row.rowDescription.setVisibility(View.GONE);
        }
        if (value != null) {
            row.rowValue.setText(value);
            row.rowValue.setVisibility(View.VISIBLE);
        } else {
            row.rowValue.setVisibility(View.GONE);
        }
        row.rowChevron.setVisibility(showChevron ? View.VISIBLE : View.GONE);
    }
}
