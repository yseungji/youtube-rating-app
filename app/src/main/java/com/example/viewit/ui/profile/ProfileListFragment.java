package com.example.viewit.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.viewit.R;
import com.example.viewit.databinding.FragmentProfileListBinding;

// 프로필 "보관함"·"최근 본 영상" 탭 (6.1). 화면은 fragment_profile_list.xml
public class ProfileListFragment extends Fragment {
    private FragmentProfileListBinding binding;

    private static final String ARG_TYPE = "type";
    public static final String TYPE_WATCH_LATER = "watch_later";
    public static final String TYPE_HISTORY = "history";

    public static ProfileListFragment newInstance(String type){
        ProfileListFragment fragment = new ProfileListFragment(); //Fragment 생성
        Bundle args = new Bundle(); //상자 만들기
        args.putString(ARG_TYPE, type); //"type"이라는 이름표로 값 넣기
        fragment.setArguments(args); //fragment에 상자 넣기
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String type = requireArguments().getString(ARG_TYPE);

        if(TYPE_WATCH_LATER.equals(type)){
            binding.profileListEmpty.emptyIcon.setImageResource(R.drawable.ic_bookmark_outline);
            binding.profileListEmpty.emptyTitle.setText(getString(R.string.profile_empty_watch_later_title));
            binding.profileListEmpty.emptyMessage.setText(getString(R.string.profile_empty_watch_later_message));
        }
        else{
            binding.profileListEmpty.emptyIcon.setImageResource(R.drawable.ic_history);
            binding.profileListEmpty.emptyTitle.setText(getString(R.string.profile_empty_history_title));
            binding.profileListEmpty.emptyMessage.setText(getString(R.string.profile_empty_history_message));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
