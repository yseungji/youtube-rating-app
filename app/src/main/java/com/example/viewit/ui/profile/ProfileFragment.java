package com.example.viewit.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.viewit.R;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.FragmentProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private FragmentProfileBinding binding;
    private ListenerRegistration userListener;
    private User myProfile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater,container,false);
        return binding.getRoot();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState){
        super.onViewCreated(view,savedInstanceState);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if(currentUser == null){
                return;
        }
        String uid = currentUser.getUid();
        listenMyProfile(uid);
        loadMyRatingCount(uid);

        binding.profileShareButton.setOnClickListener(v -> shareProfile());
        binding.profileEditButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
        binding.profileSettingsButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
        // TODO: 배너 고르기 화면 (내 공개 평가 영상에서 선택)
        binding.profileBannerEditButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
        binding.profileBannerAddButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
    }

    // MainActivity는 탭을 숨겼다 보여 주는 방식이라 onViewCreated가 다시 불리지 않는다.
    // 다른 탭에서 평가를 남기거나 지우고 돌아왔을 때 평가 수와 배너를 새로 정한다.
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            return;
        }
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            loadMyRatingCount(currentUser.getUid());
            loadBanner(currentUser.getUid(), myProfile == null ? null : myProfile.getBannerVideoId());
        }
    }
    private void listenMyProfile(String uid) {
        userListener = FirebaseFirestore.getInstance()
            .collection(FirestorePaths.USERS)
            .document(uid)
            .addSnapshotListener((snapshot, error) -> {
                if (binding == null) {
                    return;                               // 화면이 이미 없어짐
                }
                if (error != null) {
                    Log.w(TAG, "profile listen failed", error);
                    return;
                }
                if (snapshot == null || !snapshot.exists()) {
                    return;                               // 문서가 아직 없음
                }
                myProfile = snapshot.toObject(User.class);
                showProfile(myProfile);
                loadBanner(uid, myProfile.getBannerVideoId());
            });
    }
    private void loadMyRatingCount(String uid){
        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.RATINGS)
            .whereEqualTo("userId",uid)
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener((result) -> {
                if (binding == null) {
                    return;                               // 화면이 이미 없어짐
                }
                binding.profileRatingCount.setText(String.valueOf(result.getCount()));
            })
            .addOnFailureListener(e -> Log.w(TAG,"rating count failed", e));
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        binding = null;
    }

    private void showProfile(User user){
        binding.profileTitle.setText(user.getUsername());
        binding.profileName.setText(user.getDisplayName());
        binding.profileFollowerCount.setText(String.valueOf(user.getFollowerCount()));
        binding.profileFollowingCount.setText(String.valueOf(user.getFollowingCount()));
        String intro = user.getIntroduction();
        if (TextUtils.isEmpty(intro)) {
            binding.profileHandle.setVisibility(View.GONE);
        } else {
            binding.profileHandle.setText(intro);
            binding.profileHandle.setVisibility(View.VISIBLE);
        }
        Glide.with(this)
            .load(user.getPhotoUrl())
            .placeholder(R.drawable.avatar_default)
            .error(R.drawable.avatar_default)
            .fallback(R.drawable.avatar_default)
            .circleCrop()
            .into(binding.profileAvatar);
    }
    public void shareProfile(){
       if(myProfile == null){
           return;
       }
       String text = getString(R.string.profile_share_text, myProfile.getUsername());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent,null));
    }

    private void loadBanner(String uid, String bannerVideoId){
        if(TextUtils.isEmpty(bannerVideoId)){
            loadTopRatedBanner(uid); //고른적이 없음
            return;
        }
        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.RATINGS)
            .document(Rating.documentId(uid,bannerVideoId))
            .get()
            .addOnSuccessListener(doc -> {
                if(binding == null)
                    return;
                if(doc.exists() && Rating.VISIBILITY_PUBLIC.equals(doc.getString("visibility")))
                    showPhotoBanner(bannerVideoId);
                else
                    loadTopRatedBanner(uid); //평가를 삭제했거나 비공개로 전환한 경우
                }).addOnFailureListener(e -> {
        Log.w(TAG, "banner rating check failed", e);
        loadTopRatedBanner(uid);
            });
    }

    private void loadTopRatedBanner(String uid) {
        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.RATINGS)
            .whereEqualTo("userId", uid)
            .whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC)
            .orderBy("score", Query.Direction.DESCENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener(result -> {
                if(binding == null)
                    return;
                if(result.isEmpty())
                    showDefaultBanner(); //평가가 없는 경우
                else
                    showPhotoBanner(result.getDocuments().get(0).getString("videoId"));
            }).addOnFailureListener(e -> {
                // 인덱스가 아직 없으면 여기로 온다. Logcat의 링크로 인덱스를 만든다.
                Log.w(TAG, "top rated banner failed", e);
                if(binding != null)
                    showDefaultBanner();
            });
    }

    private void showPhotoBanner(String videoId){
        String url = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        Glide.with(this)
            .load(url)
            .centerCrop()
            .into(binding.profileBanner);
        binding.profileBannerVeil.setVisibility(View.VISIBLE);
        binding.profileBannerEditButton.setVisibility(View.VISIBLE);
        binding.profileBannerAddButton.setVisibility(View.GONE);
    }

    private void showDefaultBanner() {
        Glide.with(this).clear(binding.profileBanner);   // 이전 썸네일 지우기 → 바탕 그라데이션이 보임
        binding.profileBannerVeil.setVisibility(View.GONE);
        binding.profileBannerEditButton.setVisibility(View.GONE);
        binding.profileBannerAddButton.setVisibility(View.VISIBLE);
    }
}
