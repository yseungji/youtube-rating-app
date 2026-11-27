package com.example.viewit.ui.profile;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.R;
import com.example.viewit.data.PrivacyRepository;
import com.example.viewit.databinding.ActivityPrivacySettingsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// 설정 → 공개 범위 (6.3). 보관함·최근 본 영상마다 전체 공개 / 팔로워만 / 나만 보기를 고르면 바로 저장한다.
public class PrivacySettingsActivity extends AppCompatActivity {

    private ActivityPrivacySettingsBinding binding;
    private final PrivacyRepository privacyRepository = new PrivacyRepository();
    private String myUid;
    private boolean showingSaved;   // 저장된 값을 화면에 채우는 중이면 true (그때는 다시 저장하지 않는다)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPrivacySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        myUid = user.getUid();
        applySystemBarPadding();

        // ← 닫기, 고르면 바로 저장
        binding.privacyBack.getRoot().setOnClickListener(v -> finish());
        binding.privacyWatchLaterGroup.setOnCheckedChangeListener((group,checkedId)->onWatchLaterChanged(checkedId));
        binding.privacyHistoryGroup.setOnCheckedChangeListener((group,checkedId)->onHistoryChanged(checkedId));
        loadSaved();
    }

    // 보관함 공개 범위를 골랐을 때
    private void onWatchLaterChanged(@IdRes int checkedId) {
        if (showingSaved) return;
        String value = checkedId == R.id.privacy_watch_later_followers ? PrivacyRepository.FOLLOWERS
            : checkedId == R.id.privacy_watch_later_private ? PrivacyRepository.PRIVATE
            : PrivacyRepository.PUBLIC;
        save(PrivacyRepository.FIELD_WATCH_LATER, value);
    }

    // 최근 본 영상 공개 범위를 골랐을 때
    private void onHistoryChanged(@IdRes int checkedId) {
        if (showingSaved) return;
        String value = checkedId == R.id.privacy_history_followers ? PrivacyRepository.FOLLOWERS
            : checkedId == R.id.privacy_history_private ? PrivacyRepository.PRIVATE
            : PrivacyRepository.PUBLIC;
        save(PrivacyRepository.FIELD_WATCH_HISTORY, value);
    }

    private void save(String field, String value) {
        privacyRepository.save(myUid, field, value, new PrivacyRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(PrivacySettingsActivity.this, R.string.privacy_saved, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(PrivacySettingsActivity.this, R.string.privacy_save_failed, Toast.LENGTH_SHORT).show();
                loadSaved();   // 화면을 저장된 값으로 되돌린다
            }
        });
    }

    // 저장된 값(없으면 전체 공개)을 라디오 버튼에 채운다
    private void loadSaved() {
        privacyRepository.load(myUid, new PrivacyRepository.Callback<PrivacyRepository.Settings>() {
            @Override
            public void onSuccess(PrivacyRepository.Settings settings) {
                if (isDestroyed()) return;
                showingSaved = true;
                binding.privacyWatchLaterGroup.check(watchLaterButton(settings.watchLater));
                binding.privacyHistoryGroup.check(historyButton(settings.watchHistory));
                showingSaved = false;
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (isDestroyed()) return;
                Toast.makeText(PrivacySettingsActivity.this, R.string.privacy_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static int watchLaterButton(String value) {
        if (PrivacyRepository.FOLLOWERS.equals(value)) return R.id.privacy_watch_later_followers;
        if (PrivacyRepository.PRIVATE.equals(value)) return R.id.privacy_watch_later_private;
        return R.id.privacy_watch_later_public;
    }

    private static int historyButton(String value) {
        if (PrivacyRepository.FOLLOWERS.equals(value)) return R.id.privacy_history_followers;
        if (PrivacyRepository.PRIVATE.equals(value)) return R.id.privacy_history_private;
        return R.id.privacy_history_public;
    }

    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
}
