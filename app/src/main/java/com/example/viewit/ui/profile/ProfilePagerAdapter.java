package com.example.viewit.ui.profile;

import static com.example.viewit.ui.profile.ProfileListFragment.TYPE_HISTORY;
import static com.example.viewit.ui.profile.ProfileListFragment.TYPE_WATCH_LATER;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 프로필 탭 화면. 내 프로필은 평가·보관함·최근 본 영상 3개.
// 다른 사람 프로필은 공개 평가에, 공개 범위(6.3)가 허용한 보관함·최근 본 영상만 더한다 (setTabs).
public class ProfilePagerAdapter extends FragmentStateAdapter {
    public static final String TYPE_RATINGS = "ratings";

    private final String uid;
    private final boolean isMine;
    private final List<String> tabs = new ArrayList<>();

    public ProfilePagerAdapter(@NonNull Fragment fragment, String uid, boolean isMine){
        super(fragment); //탭 화면을 ProfileFragment 자식으로 관리
        this.uid = uid;
        this.isMine = isMine;
        if (isMine) {
            tabs.addAll(Arrays.asList(TYPE_RATINGS, TYPE_WATCH_LATER, TYPE_HISTORY));
        } else {
            tabs.add(TYPE_RATINGS);   // 공개 범위를 확인하면 setTabs로 늘어난다
        }
    }

    // 보여 줄 탭 바꾸기 (다른 사람 프로필). 탭 줄(TabLayoutMediator)도 따라 바뀐다
    public void setTabs(List<String> newTabs) {
        if (tabs.equals(newTabs)) {
            return;
        }
        tabs.clear();
        tabs.addAll(newTabs);
        notifyDataSetChanged();
    }

    public String getTabType(int position) {
        return tabs.get(position);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        String type = tabs.get(position);
        if (TYPE_RATINGS.equals(type))
            return ProfileRatingsFragment.newInstance(uid, !isMine);
        else
            return ProfileListFragment.newInstance(type, isMine ? null : uid);
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    // 탭이 늘거나 줄어도 같은 종류의 탭 화면은 그대로 두도록 종류마다 고정 ID를 준다
    @Override
    public long getItemId(int position) {
        return idOf(tabs.get(position));
    }

    @Override
    public boolean containsItem(long itemId) {
        for (String type : tabs) {
            if (idOf(type) == itemId) return true;
        }
        return false;
    }

    private static long idOf(String type) {
        if (TYPE_RATINGS.equals(type)) return 0;
        if (TYPE_WATCH_LATER.equals(type)) return 1;
        return 2;
    }
}
