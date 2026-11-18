package com.example.viewit.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.viewit.MainActivity;
import com.example.viewit.R;
import com.example.viewit.VideoDetailActivity;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.FragmentProfileRatingsBinding;
import com.example.viewit.ui.common.GridSpacingItemDecoration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;

public class ProfileRatingsFragment extends Fragment {
    FragmentProfileRatingsBinding binding;
    RatingTileAdapter adapter;
    private static final String TAG = "ProfileRatings";

    private static final String ARG_UID = "uid";


    static public ProfileRatingsFragment newInstance(String uid) {
        Bundle args = new Bundle();
        ProfileRatingsFragment fragment = new ProfileRatingsFragment();
        args.putString(ARG_UID, uid);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileRatingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String uid = requireArguments().getString(ARG_UID);
        adapter = new RatingTileAdapter(rating -> openVideo(rating));
        binding.profileRatingsList.setLayoutManager(
            new GridLayoutManager(requireContext(),3));
        binding.profileRatingsList.setAdapter(adapter);
        // 칸 사이 2dp 간격 (Figma Rated video grid). dimens의 dp 값을 코드에서는 px로 바꿔 받는다
        int spacing = getResources().getDimensionPixelSize(R.dimen.profile_grid_spacing);
        binding.profileRatingsList.addItemDecoration(new GridSpacingItemDecoration(3, spacing));
        loadRatings(uid);
    }
    private void loadRatings(String uid){
        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.RATINGS)
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(result -> {
                if(binding == null)
                    return;
                List<Rating> list = result.toObjects(Rating.class);
                adapter.setItems(list);

                // 0개도 "성공"이다. 비었으면 빈 화면(Figma 05-C), 있으면 3열 격자를 보인다
                if (list.isEmpty()) {
                    binding.profileRatingsList.setVisibility(View.GONE);
                    binding.profileRatingsEmpty.getRoot().setVisibility(View.VISIBLE);
                    // 빈 화면 XML에는 tools:text(미리보기 전용)만 있어서 실제 글자는 여기서 넣는다. 아이콘은 XML 기본값(별)
                    binding.profileRatingsEmpty.emptyTitle.setText(R.string.profile_empty_ratings_title);
                    binding.profileRatingsEmpty.emptyMessage.setText(R.string.profile_empty_ratings_message);
                    // "영상 평가하기" 버튼: XML에서는 숨겨져 있으므로 평가 탭에서만 보이게 하고, 누르면 하단 평가하기 탭으로 이동
                    binding.profileRatingsEmpty.emptyAction.setText(R.string.profile_empty_ratings_action);
                    binding.profileRatingsEmpty.emptyAction.setVisibility(View.VISIBLE);
                    binding.profileRatingsEmpty.emptyAction.setOnClickListener(v -> openRateTab());
                } else {
                    binding.profileRatingsList.setVisibility(View.VISIBLE);
                    binding.profileRatingsEmpty.getRoot().setVisibility(View.GONE);
                }
            }).addOnFailureListener(e -> {
                if (binding == null) return; //기본으로 넣자
                // 인덱스가 아직 없으면 여기로 온다. Logcat의 링크로 인덱스를 만든다.
                Log.w(TAG, "rating load failed", e);
                Toast.makeText(requireContext() ,R.string.profile_ratings_load_failed, Toast.LENGTH_SHORT).show();
            });
    }

    // 이 Fragment는 MainActivity의 프로필 탭 안에 있다. 부모 Activity에게 하단 탭을 "평가하기"로 바꿔 달라고 부탁한다
    private void openRateTab() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).openTab(R.id.nav_rate);
        }
    }

    private void openVideo(Rating rating){
        Intent intent = new Intent(requireContext(), VideoDetailActivity.class);
        intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID,rating.getVideoId());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
