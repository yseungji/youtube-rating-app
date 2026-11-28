package com.example.viewit;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.viewit.databinding.ActivityMainBinding;
import com.example.viewit.ui.common.SystemBarInsets;
import com.example.viewit.ui.common.YouTubeLinks;
import com.example.viewit.ui.category.CategoryFragment;
import com.example.viewit.ui.feed.FeedFragment;
import com.example.viewit.ui.rate.RateFragment;
import com.example.viewit.ui.search.SearchFragment;
import com.example.viewit.ui.videodetail.VideoDetailActivity;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// 로그인 후 메인 화면. 하단 5탭과 좌우 스와이프(ViewPager2)로 피드·탐색·카테고리·평가하기·프로필 화면을 전환한다.
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;

    // 스와이프로 페이지가 바뀌면 하단 5탭 선택 상태도 같이 맞춘다.
    // 피드·탐색·카테고리·평가하기 탭 화면이 만들어지면 상태바 높이만큼 위를 띄운다.
    // 프로필은 배너를 상태바 뒤까지 깔아서 제외하고, 바텀시트 같은 다른 Fragment도 건드리지 않는다.
    // 각 탭 Fragment 코드는 고치지 않는다.
    private final FragmentManager.FragmentLifecycleCallbacks tabTopInset =
        new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                              @NonNull View v, Bundle savedInstanceState) {
                if (f instanceof FeedFragment || f instanceof SearchFragment
                    || f instanceof CategoryFragment || f instanceof RateFragment) {
                    SystemBarInsets.padTop(v);
                }
            }
        };

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
            if (isTextShareIntent(getIntent())) {
                Log.d(TAG, "미로그인 상태에서 공유 인텐트 수신 -> 로그인 화면 이동");
                Toast.makeText(this, R.string.share_receive_login_required, Toast.LENGTH_SHORT).show();
            }
            openAndClear(GoogleSignInActivity.class);
            return;
        }

        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 아래쪽 여백은 BottomNavigationView가 직접 처리하므로 좌·우만 맞춘다.
        // 위쪽(상태바) 여백은 탭 화면마다 준다: 프로필은 배너를 상태바 뒤까지 깔기 때문 (Figma 05-D)
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            return insets;
        });
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(tabTopInset, false);

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

        // 외부 앱(YouTube 등)에서 영상 링크를 공유받아 처음 실행된 경우 바로 영상 상세를 연다.
        // 회전 등 재생성(savedInstanceState != null) 시에는 같은 공유 인텐트를 다시 열지 않는다.
        if (savedInstanceState == null) {
            handleShareIntent(getIntent());
        }
    }

    // 앱이 이미 실행 중인 상태에서 외부 앱의 공유(ACTION_SEND)를 다시 받았을 때 호출된다.
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (isTextShareIntent(intent)) {
                Log.d(TAG, "미로그인 상태에서 onNewIntent 공유 수신 -> 로그인 화면 이동");
                Toast.makeText(this, R.string.share_receive_login_required, Toast.LENGTH_SHORT).show();
            }
            openAndClear(GoogleSignInActivity.class);
            return;
        }
        handleShareIntent(intent);
    }

    private boolean isTextShareIntent(@Nullable Intent intent) {
        return intent != null
                && Intent.ACTION_SEND.equals(intent.getAction())
                && intent.getType() != null
                && intent.getType().startsWith("text/");
    }

    // 공유받은 텍스트에서 YouTube 영상 ID를 추출해 MainActivity 위에 VideoDetailActivity를 연다.
    // 뒤로가기를 누르면 아래에 깔린 MainActivity로 자연스럽게 돌아온다.
    private void handleShareIntent(@Nullable Intent intent) {
        if (!isTextShareIntent(intent)) {
            return;
        }
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        String videoId = YouTubeLinks.extractVideoId(sharedText);
        if (videoId == null) {
            String subjectText = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            videoId = YouTubeLinks.extractVideoId(subjectText);
        }
        // 처리 완료한 공유 인텐트는 비워 두어 이후 재생성 시 중복 실행되지 않게 한다.
        setIntent(new Intent(this, MainActivity.class));

        if (videoId == null) {
            Log.w(TAG, "공유 텍스트에서 유효한 YouTube 영상 ID를 찾지 못함");
            Toast.makeText(this, R.string.share_receive_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "공유받은 YouTube 영상 상세 열기: videoId=" + videoId);
        Intent detailIntent = new Intent(this, VideoDetailActivity.class);
        detailIntent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, videoId);
        startActivity(detailIntent);
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
