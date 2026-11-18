package com.example.viewit;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

//로그인 후 메인 화면. 하단 5탭으로 피드·탐색·카테고리·평가하기·프로필 화면을 전환한다.
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user == null) {
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

        // 첫 화면을 feedtab으로 설정함과 동시에 화면 회전시 feed로 초기화되지 않도록 해준다.
        if (savedInstanceState == null) {
            showTab(R.id.nav_feed);
        }

        //setOnClickListener와 같은 원리이다. 구글 머티리얼 디자인 라이브러리를 사용해서 5개의 메뉴 아이콘을 하나의 세트로 묶는다.
        binding.bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                //menuItem이 어떤 메뉴가 선택됐는지를 가리킨다.
                int menu = menuItem.getItemId();
                //조건 분기 5번을 해야해서 코드가 길어지므로, 메서드를 따로 만들어서 가독성을 높였다.
                showTab(menu);

                return true;
            }
        });
    }


    private void showTab(int tabId) {
        //다형성을 이용해서, 로직 중복을 없애기
        Fragment fragment = null;
        if(tabId == R.id.nav_feed) fragment = new FeedFragment();
        else if(tabId == R.id.nav_rate) fragment = new RateFragment();
        else if(tabId == R.id.nav_profile) fragment = new ProfileFragment();
        else if(tabId == R.id.nav_search) fragment = new SearchFragment();
        else if(tabId == R.id.nav_category) fragment = new CategoryFragment();

        if(fragment != null) {
            //addToBackStack(null)는 설계상 사용하지 않는다.
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        }


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
