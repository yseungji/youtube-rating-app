package com.example.viewit.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.MainActivity;
import com.example.viewit.R;
import com.example.viewit.data.FollowRepository;
import com.example.viewit.data.model.FollowListItem;
import com.example.viewit.databinding.ActivityRecommendFollowBinding;
import com.example.viewit.ui.profile.FollowListAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

// 온보딩 D · 평가자 추천 (3.4). 고른 주제의 공개 평가가 많은 사람을 보여 주고 팔로우하게 한다.
// 여는 쪽: EXTRA_CATEGORY_IDS에 고른 카테고리 ID 목록(ArrayList<String>)
public class RecommendFollowActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_IDS = "categoryIds";

    private ActivityRecommendFollowBinding binding;
    private final FollowRepository followRepository = new FollowRepository();
    private FollowListAdapter adapter;
    private List<FollowListItem> items = new ArrayList<>();
    private ArrayList<String> categoryIds;   // TopicActivity가 넘겨준 관심 카테고리
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityRecommendFollowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        categoryIds = getIntent().getStringArrayListExtra(EXTRA_CATEGORY_IDS);   // TopicActivity가 고른 주제
        setupScreen();   // 추천 목록 불러오기

        binding.recommendBack.getRoot().setOnClickListener(v -> finish());
        binding.recommendStartButton.setOnClickListener(v -> openMain());
        binding.recommendLater.setOnClickListener(v -> openMain());
    }

    // 피드로 이동. 온보딩 화면으로 돌아오지 않게 이전 화면을 모두 지운다
    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ---------- 추천 목록·팔로우 ----------

    private void setupScreen() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        myUid = user.getUid();

        adapter = new FollowListAdapter(new FollowListAdapter.OnFollowRowListener() {
            @Override
            public void onRowClick(FollowListItem item) {
                // 온보딩 중에는 프로필로 가지 않는다
            }

            @Override
            public void onFollowButtonClick(FollowListItem item, int position) {
                toggleFollow(item, position);
            }
        });
        binding.recommendList.setLayoutManager(new LinearLayoutManager(this));
        binding.recommendList.setAdapter(adapter);
        binding.recommendFollowAll.setOnClickListener(v -> followAll());
        showFollowingCount();
        loadRecommended();
    }

    private void loadRecommended() {
        binding.recommendLoading.setVisibility(View.VISIBLE);
        followRepository.loadRecommended(myUid, categoryIds == null ? new ArrayList<>() : categoryIds,
            new FollowRepository.Callback<List<FollowListItem>>() {
                @Override
                public void onSuccess(List<FollowListItem> result) {
                    if (isFinishing()) return;
                    binding.recommendLoading.setVisibility(View.GONE);
                    items = result;
                    adapter.setItems(items, false);
                    boolean empty = items.isEmpty();
                    binding.recommendEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    binding.recommendFollowAll.setVisibility(empty ? View.GONE : View.VISIBLE);
                    showFollowingCount();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    if (isFinishing()) return;
                    binding.recommendLoading.setVisibility(View.GONE);
                    binding.recommendEmpty.setVisibility(View.VISIBLE);
                    binding.recommendFollowAll.setVisibility(View.GONE);
                    Toast.makeText(RecommendFollowActivity.this, R.string.recommend_load_failed, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void toggleFollow(FollowListItem item, int position) {
        boolean wasFollowing = item.isFollowedByMe();
        FollowRepository.Callback<Void> done = new FollowRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                item.setFollowedByMe(!wasFollowing);
                adapter.notifyItemChanged(position);
                showFollowingCount();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(RecommendFollowActivity.this,
                    wasFollowing ? R.string.unfollow_failed : R.string.follow_failed, Toast.LENGTH_SHORT).show();
            }
        };
        if (wasFollowing) {
            followRepository.unfollow(myUid, item.getUid(), done);
        } else {
            followRepository.follow(myUid, item.getUid(), done);
        }
    }

    // 아직 팔로우하지 않은 사람만 팔로우한다
    private void followAll() {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isFollowedByMe()) {
                toggleFollow(items.get(i), i);
            }
        }
    }

    private void showFollowingCount() {
        int count = 0;
        for (FollowListItem item : items) {
            if (item.isFollowedByMe()) count++;
        }
        binding.recommendFollowingCount.setText(getString(R.string.recommend_following_count, count));
    }
}
