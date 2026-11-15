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
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.FragmentProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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
    }

    // MainActivity는 탭을 숨겼다 보여 주는 방식이라 onViewCreated가 다시 불리지 않는다.
    // 다른 탭에서 평가를 남기고 돌아왔을 때 평가 수를 새로 센다.
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            return;
        }
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            loadMyRatingCount(currentUser.getUid());
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
            .placeholder(R.drawable.ic_avatar_default)
            .error(R.drawable.ic_avatar_default)
            .fallback(R.drawable.ic_avatar_default)
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
}
