package com.example.viewit;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.viewit.databinding.ActivityMainBinding;
import com.example.viewit.ui.category.CategoryFragment;
import com.example.viewit.ui.feed.FeedFragment;
import com.example.viewit.ui.profile.ProfileFragment;
import com.example.viewit.ui.rate.RateFragment;
import com.example.viewit.ui.search.SearchFragment;

// 앱 첫 화면. 하단 5탭으로 피드·탐색·카테고리·평가하기·프로필 화면을 전환한다.
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 아래쪽 여백은 BottomNavigationView가 직접 처리하므로 위·좌·우만 맞춘다
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });



        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        // 이미 보고 있는 탭을 다시 누른 경우 (맨 위로 이동 등은 각 탭 작업에서 추가)
        binding.bottomNav.setOnItemReselectedListener(item -> { });

        // 화면 회전 등으로 다시 만들어질 때는 이전 탭 화면이 자동으로 복원된다
        if (savedInstanceState == null) {
            showTab(R.id.nav_feed);
        }
    }

    // 고른 탭의 화면만 보이고 나머지는 숨긴다. 숨긴 화면은 상태(스크롤 위치 등)가 유지된다.
    private void showTab(int tabId) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        String tag = String.valueOf(tabId);
        Fragment target = fragmentManager.findFragmentByTag(tag);

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setReorderingAllowed(true);
        for (Fragment fragment : fragmentManager.getFragments()) {
            if (fragment != target) {
                transaction.hide(fragment);
            }
        }
        if (target == null) {
            transaction.add(R.id.fragment_container, createTabFragment(tabId), tag);
        } else {
            transaction.show(target);
        }
        transaction.commit();
    }

    // R.id 값은 상수가 아니라서 switch 대신 if로 나눈다
    private Fragment createTabFragment(int tabId) {
        if (tabId == R.id.nav_search) {
            return new SearchFragment();
        } else if (tabId == R.id.nav_category) {
            return new CategoryFragment();
        } else if (tabId == R.id.nav_rate) {
            return new RateFragment();
        } else if (tabId == R.id.nav_profile) {
            return new ProfileFragment();
        }
        return new FeedFragment();
    }
}
