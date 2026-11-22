package com.example.viewit;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.data.AccountRepository;
import com.example.viewit.databinding.ActivitySettingsBinding;
import com.example.viewit.databinding.ViewSettingsRowBinding;

// 설정 화면 (6.3, Figma 10 · 설정). 지금 동작하는 것은 로그아웃·회원 탈퇴이고, 나머지 줄은 "준비 중" 안내.
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private final AccountRepository account = new AccountRepository();   // 로그아웃·탈퇴 처리

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarPadding();   // 상태바·내비게이션 바만큼 여백
        setupRows();               // 설정 줄 아이콘·글자 채우기, 아직 없는 기능은 "준비 중"

        binding.settingsBack.getRoot().setOnClickListener(v -> finish());
        binding.settingsLogout.setOnClickListener(v -> showLogoutDialog());
        binding.settingsDeleteAccount.setOnClickListener(v -> showDeleteDialog());
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

        // 아직 만들지 않은 기능 (프로필 편집·YouTube 연동·내 활동·공개 범위·차단·약관은 6.3 후속)
        View.OnClickListener comingSoon = v ->
            Toast.makeText(this, R.string.profile_coming_soon, Toast.LENGTH_SHORT).show();
        binding.settingsProfileEdit.getRoot().setOnClickListener(comingSoon);
        binding.settingsYoutube.getRoot().setOnClickListener(comingSoon);
        binding.settingsMyActivity.getRoot().setOnClickListener(comingSoon);
        binding.settingsVisibility.getRoot().setOnClickListener(comingSoon);
        binding.settingsBlocked.getRoot().setOnClickListener(comingSoon);
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
