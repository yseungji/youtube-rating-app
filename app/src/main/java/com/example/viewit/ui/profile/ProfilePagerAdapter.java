package com.example.viewit.ui.profile;

import static com.example.viewit.ui.profile.ProfileListFragment.TYPE_WATCH_LATER;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

// 프로필 탭 화면. 내 프로필은 평가·보관함·최근 본 영상 3개, 다른 사람 프로필은 공개 평가 1개만
// (보관함·최근 본 영상의 공개 범위 설정은 6.3에서 만든다).
public class ProfilePagerAdapter extends FragmentStateAdapter {
    private final String uid;
    private final boolean isMine;

    public ProfilePagerAdapter(@NonNull Fragment fragment, String uid, boolean isMine){
        super(fragment); //탭 화면을 ProfileFragment 자식으로 관리
        this.uid = uid;
        this.isMine = isMine;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if(position == 0)
            return ProfileRatingsFragment.newInstance(uid, !isMine);
        else if(position == 1)
            return  ProfileListFragment.newInstance(TYPE_WATCH_LATER);
        else
            return ProfileListFragment.newInstance(ProfileListFragment.TYPE_HISTORY);
    }

    @Override
    public int getItemCount() {
        return isMine ? 3 : 1;
    }
}
