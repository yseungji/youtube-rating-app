package com.example.viewit;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.viewit.ui.category.CategoryFragment;
import com.example.viewit.ui.feed.FeedFragment;
import com.example.viewit.ui.profile.ProfileFragment;
import com.example.viewit.ui.rate.RateFragment;
import com.example.viewit.ui.search.SearchFragment;

// MainActivity 하단 5탭(피드·탐색·카테고리·평가하기·프로필)을 ViewPager2 스와이프로 연결하는 어댑터.
public class MainPagerAdapter extends FragmentStateAdapter {
    public static final int TAB_FEED = 0;
    public static final int TAB_SEARCH = 1;
    public static final int TAB_CATEGORY = 2;
    public static final int TAB_RATE = 3;
    public static final int TAB_PROFILE = 4;
    public static final int TAB_COUNT = 5;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == TAB_SEARCH) {
            return new SearchFragment();
        }
        if (position == TAB_CATEGORY) {
            return new CategoryFragment();
        }
        if (position == TAB_RATE) {
            return new RateFragment();
        }
        if (position == TAB_PROFILE) {
            return new ProfileFragment();
        }
        return new FeedFragment();
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }

    // 하단 메뉴 ID를 ViewPager2 페이지 인덱스로 변환한다.
    public static int positionForTab(int tabId) {
        if (tabId == R.id.nav_feed) {
            return TAB_FEED;
        }
        if (tabId == R.id.nav_search) {
            return TAB_SEARCH;
        }
        if (tabId == R.id.nav_category) {
            return TAB_CATEGORY;
        }
        if (tabId == R.id.nav_rate) {
            return TAB_RATE;
        }
        if (tabId == R.id.nav_profile) {
            return TAB_PROFILE;
        }
        return -1;
    }

    // ViewPager2 페이지 인덱스를 하단 메뉴 ID로 변환한다.
    public static int tabIdForPosition(int position) {
        if (position == TAB_FEED) {
            return R.id.nav_feed;
        }
        if (position == TAB_SEARCH) {
            return R.id.nav_search;
        }
        if (position == TAB_CATEGORY) {
            return R.id.nav_category;
        }
        if (position == TAB_RATE) {
            return R.id.nav_rate;
        }
        if (position == TAB_PROFILE) {
            return R.id.nav_profile;
        }
        return 0;
    }
}
