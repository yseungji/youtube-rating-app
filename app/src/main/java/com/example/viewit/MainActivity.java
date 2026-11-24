package com.example.viewit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.viewit.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// 로그인 후 메인 화면. 하단 5탭과 좌우 스와이프(ViewPager2)로 피드·탐색·카테고리·평가하기·프로필 화면을 전환한다.
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;

    // 스와이프로 페이지가 바뀌면 하단 5탭 선택 상태도 같이 맞춘다.
    private final ViewPager2.OnPageChangeCallback pageChangeCallback =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    int tabId = MainPagerAdapter.tabIdForPosition(position);
                    if (tabId != 0 && binding.bottomNav.getSelectedItemId() != tabId) {
                        Log.d(TAG, "스와이프 페이지 전환 -> 하단 탭 동기화: position=" + position);
                        binding.bottomNav.setSelectedItemId(tabId);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            openAndClear(GoogleSignInActivity.class);
            return;
        }

        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 아래쪽 여백은 BottomNavigationView가 직접 처리하므로 위·좌·우만 맞춘다
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // 5탭 화면을 ViewPager2에 연결해 좌우 스와이프로 전환할 수 있게 한다.
        binding.mainPager.setAdapter(new MainPagerAdapter(this));
        binding.mainPager.registerOnPageChangeCallback(pageChangeCallback);

        // 첫 실행에서는 피드 탭(0번)을 보여 주고, 화면 회전 시에는 ViewPager2가 마지막 탭을 복원한다.
        if (savedInstanceState == null) {
            binding.mainPager.setCurrentItem(MainPagerAdapter.TAB_FEED, false);
            binding.bottomNav.setSelectedItemId(R.id.nav_feed);
        }

        // 하단 5탭 클릭 시 해당하는 ViewPager2 페이지로 이동한다.
        binding.bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                int position = MainPagerAdapter.positionForTab(menuItem.getItemId());
                if (position == -1) {
                    return false;
                }
                if (binding.mainPager.getCurrentItem() != position) {
                    Log.d(TAG, "하단 탭 선택 -> 페이지 이동: position=" + position);
                    binding.mainPager.setCurrentItem(position, true);
                }
                return true;
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (binding != null) {
            binding.mainPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        super.onDestroy();
    }

    // 다른 화면(예: 프로필 빈 화면의 "영상 평가하기" 버튼)에서 하단 탭을 바꿀 때 쓴다.
    // setSelectedItemId는 하단 탭 선택 표시를 옮기고, 위의 setOnItemSelectedListener를 거쳐 ViewPager2 페이지까지 바꾼다.
    public void openTab(int tabId) {
        binding.bottomNav.setSelectedItemId(tabId);
    }

    //Intent Flag 활용 : 보안상의 문제를 위한 함수 구현 (백스택 지우는 함수)
    //로그인이 풀렸을 때,  로그인 액티비티로 넘어가면서 모든 스택을 비우는 함수
    private void openAndClear(Class<?> target) {
        //FLAG_ACTIVITY_CLEAR_TASK : 현재 실행 중인 모든 액티비티 스택을 비운다.
        //FLAG_ACTIVITY_NEW_TASK : 기존 화면들을 다 날려버리고 새로운 태스크로 로그인 화면을 시작합니다.
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}
