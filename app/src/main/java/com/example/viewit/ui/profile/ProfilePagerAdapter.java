package com.example.viewit.ui.profile;

import static com.example.viewit.ui.profile.ProfileListFragment.TYPE_WATCH_LATER;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ProfilePagerAdapter extends FragmentStateAdapter {
    private final String uid;

    public ProfilePagerAdapter(@NonNull Fragment fragment, String uid){
        super(fragment); //탭 화면을 ProfileFragment 자식으로 관리11
        this.uid = uid;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if(position == 0)
            return ProfileRatingsFragment.newInstance(uid);
        else if(position == 1)
            return  ProfileListFragment.newInstance(TYPE_WATCH_LATER);
        else
            return ProfileListFragment.newInstance(ProfileListFragment.TYPE_HISTORY);
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
