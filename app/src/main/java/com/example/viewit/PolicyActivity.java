package com.example.viewit;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.databinding.ActivityPolicyBinding;

// 설정 → 이용약관 / 개인정보처리방침 (6.3). 두 문서를 한 화면으로 보여 준다.
// 여는 쪽: intent에 EXTRA_TYPE(TYPE_TERMS 또는 TYPE_PRIVACY)
public class PolicyActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "type";
    public static final String TYPE_TERMS = "terms";
    public static final String TYPE_PRIVACY = "privacy";

    private ActivityPolicyBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPolicyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarPadding();

        binding.policyBack.getRoot().setOnClickListener(v -> finish());

        // 어느 문서인지 받아서 제목·본문 채우기 (Extra가 없으면 이용약관)
         String type = getIntent().getStringExtra(EXTRA_TYPE);
         if(TYPE_PRIVACY.equals(type)){
             binding.policyTitle.setText(R.string.policy_privacy_title);
             binding.policyBody.setText(R.string.policy_privacy_body);
         }
         else{
             binding.policyTitle.setText(R.string.policy_terms_title);
             binding.policyBody.setText(R.string.policy_terms_body);
         }

    }

    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
}
