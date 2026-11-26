package com.example.viewit.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.data.ProfileVideoLoader;
import com.example.viewit.data.model.RatedVideoItem;
import com.example.viewit.databinding.FragmentProfileListBinding;
import com.example.viewit.ui.videodetail.VideoDetailActivity;

import java.util.ArrayList;
import java.util.List;

// 프로필 "보관함"·"최근 본 영상" 탭 (6.1). 화면은 fragment_profile_list.xml
// 목록 한 줄은 평가한 영상 목록과 같은 RatedVideoAdapter(item_video_row)를 쓴다.
public class ProfileListFragment extends Fragment {
    private static final String TAG = "ProfileList";

    private FragmentProfileListBinding binding;
    private RatedVideoAdapter adapter;
    private final ProfileVideoLoader loader = new ProfileVideoLoader();
    private String type;   // TYPE_WATCH_LATER 또는 TYPE_HISTORY

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
        type = requireArguments().getString(ARG_TYPE);

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

        // 목록 연결: 줄을 누르면 영상 상세
        adapter = new RatedVideoAdapter(item -> openVideo(item));
        binding.profileList.setLayoutManager(
            new LinearLayoutManager(requireContext())
        );
        binding.profileList.setAdapter(adapter);
    }

    // 탭이 보일 때와 영상 상세에서 돌아올 때마다 새로 불러온다 (그사이 보관·시청했을 수 있음)
    @Override
    public void onResume() {
        super.onResume();
        loadItems();
    }

    // 줄을 누르면 영상 상세 열기
    private void openVideo(RatedVideoItem item) {
        // 돌아오면 onResume이 다시 불러온다
        Intent intent = new Intent(requireContext(), VideoDetailActivity.class);
        intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, item.getVideoId());
        intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, item.getCategoryId());
        startActivity(intent);
    }

    private void loadItems() {
        if (binding == null) {
            return;
        }
        binding.profileListLoading.setVisibility(View.VISIBLE);
        binding.profileListEmpty.getRoot().setVisibility(View.GONE);
        loader.load(requireContext(), TYPE_WATCH_LATER.equals(type), new ProfileVideoLoader.Callback() {
            @Override
            public void onLoaded(@NonNull List<RatedVideoItem> items) {
                showItems(items);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "load failed: " + type, error);
                showItems(new ArrayList<>());
            }
        });
    }

    private void showItems(List<RatedVideoItem> items) {
        if (binding == null) {
            return;   // 불러오는 사이 화면이 사라짐
        }
        binding.profileListLoading.setVisibility(View.GONE);
        if (adapter != null) {
            adapter.setItems(items);
        }
        // 비었으면 빈 화면, 있으면 목록
        if(items.isEmpty()) {
            binding.profileList.setVisibility(View.GONE);
            binding.profileListEmpty.getRoot().setVisibility(View.VISIBLE);
        }
        else{
            binding.profileList.setVisibility(View.VISIBLE);
            binding.profileListEmpty.getRoot().setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
