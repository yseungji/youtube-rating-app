package com.example.viewit.ui.profile;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.databinding.ActivityUserProfileBinding;

// 다른 사용자 프로필 (6.2). 화면 내용은 ProfileFragment.newInstance(uid)가 그린다.
// 여는 쪽: intent.putExtra(UserProfileActivity.EXTRA_UID, 상대 uid)
public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "uid";

    private ActivityUserProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 누구 프로필인지 받지 못했으면 열 수 없다
        String uid = getIntent().getStringExtra(EXTRA_UID);
        if(uid == null){
            finish();
            return;
        }
        applySystemBarPadding();
        showProfile(uid, savedInstanceState);
    }

    // ---------- 화면 채우기 ----------

    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    // ProfileFragment를 넣는다. 화면 회전으로 다시 만들어질 때는 이미 들어 있으니 다시 넣지 않는다.
    private void showProfile(String uid, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return;
        }
        getSupportFragmentManager().beginTransaction()
            .replace(binding.userProfileContainer.getId(), ProfileFragment.newInstance(uid))
            .commit();
    }
}
