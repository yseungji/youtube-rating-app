package com.example.viewit.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.R;
import com.example.viewit.data.OnboardingRepository;
import com.example.viewit.databinding.ActivityTopicBinding;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;

// 온보딩 C · 관심 주제 고르기 (3.3). 3개 이상 고르면 users/{uid}.categoryIds에 저장하고 평가자 추천으로 간다.
public class TopicActivity extends AppCompatActivity {

    private ActivityTopicBinding binding;
    private final OnboardingRepository repository = new OnboardingRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityTopicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupScreen();   // 칩 14개와 여백

        binding.topicBack.getRoot().setOnClickListener(v -> finish());
        binding.topicNextButton.setOnClickListener(v -> saveTopics());
    }

    // 고른 개수가 바뀔 때마다: "다음 (N개 선택)" 글자, 3개 이상일 때만 누를 수 있게
    private void updateNextButton(int selectedCount) {
        binding.topicNextButton.setText(getString(R.string.topic_next_count, selectedCount));
        if(selectedCount < TopicCatalog.MIN_SELECTION)
            binding.topicNextButton.setEnabled(false);
        else
            binding.topicNextButton.setEnabled(true);
    }

    // 저장이 끝나면 고른 ID 목록을 들고 평가자 추천 화면으로
    private void openRecommend(ArrayList<String> categoryIds) {
        Intent intent = new Intent(this, RecommendFollowActivity.class);
        intent.putExtra(RecommendFollowActivity.EXTRA_CATEGORY_IDS, categoryIds);
        startActivity(intent);
        finish();
    }

    // ---------- 화면 채우기·저장 ----------

    private void setupScreen() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        for (int i = 0; i < TopicCatalog.IDS.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_topic_chip, binding.topicChips, false);
            chip.setText(TopicCatalog.NAMES[i]);
            chip.setTag(TopicCatalog.IDS[i]);
            chip.setOnCheckedChangeListener((button, checked) -> updateNextButton(selectedIds().size()));
            binding.topicChips.addView(chip);
        }
        updateNextButton(0);
    }

    // 지금 고른 카테고리 ID들 (칩 순서대로)
    private ArrayList<String> selectedIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (int i = 0; i < binding.topicChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.topicChips.getChildAt(i);
            if (chip.isChecked()) {
                ids.add((String) chip.getTag());
            }
        }
        return ids;
    }

    private void saveTopics() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        ArrayList<String> ids = selectedIds();
        if (user == null || ids.size() < TopicCatalog.MIN_SELECTION) {
            return;
        }
        binding.topicNextButton.setEnabled(false);   // 두 번 눌리지 않게
        repository.saveCategoryIds(user.getUid(), ids, new OnboardingRepository.Callback() {
            @Override
            public void onSuccess() {
                openRecommend(ids);
            }

            @Override
            public void onError(@NonNull Exception error) {
                binding.topicNextButton.setEnabled(true);
                Toast.makeText(TopicActivity.this, R.string.topic_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
