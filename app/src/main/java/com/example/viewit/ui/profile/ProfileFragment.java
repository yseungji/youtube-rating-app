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
import com.example.viewit.data.FollowRepository;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.FragmentProfileBinding;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

// 프로필 화면 (6.1 내 프로필 / 6.2 다른 사용자 프로필).
// MainActivity의 프로필 탭은 new ProfileFragment()로 내 프로필을, UserProfileActivity는 newInstance(uid)로 다른 사람 프로필을 연다.
public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final String ARG_UID = "uid";

    private FragmentProfileBinding binding;
    private ListenerRegistration userListener;
    private final FollowRepository followRepository = new FollowRepository();

    private String myUid;          // 로그인한 사람
    private String profileUid;     // 이 화면에 보여 주는 사람
    private boolean isMine;        // 내 프로필인지
    private User profile;          // 보여 주는 사람의 프로필 (아직 못 불러왔으면 null)
    private boolean following;     // (다른 사람 프로필) 내가 팔로우 중인지

    // 다른 사용자 프로필을 열 때
    public static ProfileFragment newInstance(String uid) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        fragment.setArguments(args);
        return fragment;
    }

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
        myUid = currentUser.getUid();
        String argUid = getArguments() == null ? null : getArguments().getString(ARG_UID);
        profileUid = TextUtils.isEmpty(argUid) ? myUid : argUid;
        isMine = profileUid.equals(myUid);

        binding.profilePager.setAdapter(new ProfilePagerAdapter(this, profileUid, isMine));
        new TabLayoutMediator(binding.profileTabs, binding.profilePager, (tab, position) -> {
            if(position == 0){ //평가 격자(기본 탭)
                tab.setIcon(R.drawable.ic_grid);
                tab.setContentDescription(R.string.profile_tab_ratings);
            }
            else if(position == 1){ //나중에 볼 영상
                tab.setIcon(R.drawable.ic_bookmark_outline);
                tab.setContentDescription(R.string.profile_tab_watch_later);
            }
            else {
                tab.setIcon(R.drawable.ic_history); //최근 본 영상
                tab.setContentDescription(R.string.profile_tab_history);
            }
        }).attach();
        listenProfile(profileUid);
        loadRatingCount(profileUid);
        loadFollowCounts(profileUid);

        binding.profileShareButton.setOnClickListener(v -> shareProfile());
        binding.profileEditButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
        binding.profileSettingsButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
        // TODO: 배너 고르기 화면 (내 공개 평가 영상에서 선택)
        binding.profileBannerEditButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
        binding.profileBannerAddButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());

        // 팔로워·팔로잉 숫자를 누르면 목록 화면
        binding.profileFollowerStat.setOnClickListener(v -> openFollowList(FollowListActivity.TAB_FOLLOWERS));
        binding.profileFollowingStat.setOnClickListener(v -> openFollowList(FollowListActivity.TAB_FOLLOWING));

        if (!isMine) {
            setupOtherProfile();
        }
    }

    // 팔로워·팔로잉 목록 열기. 목록 화면 위 제목은 이 프로필 주인 이름 (아직 못 불러왔으면 빈칸)
    private void openFollowList(int tab){
        Intent intent = new Intent(requireContext(), FollowListActivity.class);
        intent.putExtra(FollowListActivity.EXTRA_UID, profileUid);
        String title = profile == null ? "" : profile.getDisplayName();
        intent.putExtra(FollowListActivity.EXTRA_TITLE, title);
        intent.putExtra(FollowListActivity.EXTRA_TAB, tab);
        startActivity(intent);
    }

    // ---------- 다른 사람 프로필 (6.2) ----------

    // ← 보이기, 설정·편집·배너 버튼 숨기기, 팔로우 버튼 보이기
    private void setupOtherProfile() {
        binding.profileBack.getRoot().setVisibility(View.VISIBLE);
        binding.profileBack.getRoot().setOnClickListener(v -> requireActivity().finish());
        binding.profileSettingsButton.setVisibility(View.GONE);
        binding.profileEditButton.setVisibility(View.GONE);
        binding.profileBannerEditButton.setVisibility(View.GONE);
        binding.profileBannerAddButton.setVisibility(View.GONE);

        binding.profileFollowButton.setVisibility(View.VISIBLE);
        binding.profileFollowButton.setEnabled(false);   // 팔로우 여부를 확인할 때까지
        followRepository.isFollowing(myUid, profileUid, new FollowRepository.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (binding == null) return;
                following = result;
                showFollowButton();
                binding.profileFollowButton.setEnabled(true);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "follow state failed", error);
            }
        });
        binding.profileFollowButton.setOnClickListener(v -> toggleFollow());
    }

    private void showFollowButton() {
        FollowButtonStyle.apply(binding.profileFollowButton, following, R.string.follow);
    }

    // 팔로우 ↔ 언팔로우. 끝나면 버튼 모양과 팔로워 수를 다시 그린다.
    private void toggleFollow() {
        binding.profileFollowButton.setEnabled(false);
        boolean wasFollowing = following;
        FollowRepository.Callback<Void> done = new FollowRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (binding == null) return;
                following = !wasFollowing;
                showFollowButton();
                binding.profileFollowButton.setEnabled(true);
                loadFollowCounts(profileUid);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "toggle follow failed", error);
                if (binding == null) return;
                binding.profileFollowButton.setEnabled(true);
                Toast.makeText(requireContext(),
                    wasFollowing ? R.string.unfollow_failed : R.string.follow_failed, Toast.LENGTH_SHORT).show();
            }
        };
        if (wasFollowing) {
            followRepository.unfollow(myUid, profileUid, done);
        } else {
            followRepository.follow(myUid, profileUid, done);
        }
    }

    // ---------- 공통 ----------

    // MainActivity는 탭을 숨겼다 보여 주는 방식이라 onViewCreated가 다시 불리지 않는다.
    // 다른 탭에서 평가를 남기거나 지우고 돌아왔을 때 평가 수·팔로우 수·배너를 새로 정한다.
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden || profileUid == null) {
            return;
        }
        loadRatingCount(profileUid);
        loadFollowCounts(profileUid);
        loadBanner(profileUid, profile == null ? null : profile.getBannerVideoId());
    }

    // 목록 화면에서 팔로우를 바꾸고 돌아왔을 때 숫자를 맞춘다
    @Override
    public void onResume() {
        super.onResume();
        if (profileUid != null && !isHidden()) {
            loadFollowCounts(profileUid);
        }
    }

    private void listenProfile(String uid) {
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
                profile = snapshot.toObject(User.class);
                showProfile(profile);
                loadBanner(uid, profile.getBannerVideoId());
            });
    }

    // 내 프로필은 비공개 평가까지, 다른 사람 프로필은 공개 평가만 센다
    private void loadRatingCount(String uid){
        Query query = FirebaseFirestore.getInstance()
            .collection(FirestorePaths.RATINGS)
            .whereEqualTo("userId",uid);
        if (!isMine) {
            query = query.whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC);
        }
        query.count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener((result) -> {
                if (binding == null) {
                    return;                               // 화면이 이미 없어짐
                }
                binding.profileRatingCount.setText(String.valueOf(result.getCount()));
            })
            .addOnFailureListener(e -> Log.w(TAG,"rating count failed", e));
    }

    // 팔로워·팔로잉 수는 follows를 바로 센다 (users의 숫자 필드는 쓰지 않음)
    private void loadFollowCounts(String uid) {
        followRepository.countFollowers(uid, new FollowRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long count) {
                if (binding != null) binding.profileFollowerCount.setText(String.valueOf(count));
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "follower count failed", error);
            }
        });
        followRepository.countFollowing(uid, new FollowRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long count) {
                if (binding != null) binding.profileFollowingCount.setText(String.valueOf(count));
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "following count failed", error);
            }
        });
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
        binding.profileName.setText(user.getDisplayName());
        if (!isMine) {
            binding.profileTitle.setText(user.getDisplayName());   // 다른 사람 프로필은 앱바에 이름
        }
        String intro = user.getIntroduction();
        if (TextUtils.isEmpty(intro)) {
            binding.profileHandle.setText(getString(R.string.profile_handle, user.getUsername()));
        } else {
            binding.profileHandle.setText(getString(R.string.profile_handle_with_intro, user.getUsername(), intro));
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
       if(profile == null){
           return;
       }
       String text = getString(R.string.profile_share_text, profile.getUsername());

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

    // 배너 위 버튼(연필·배너 추가)은 내 프로필에서만 보인다
    private void showPhotoBanner(String videoId){
        String url = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        Glide.with(this)
            .load(url)
            .centerCrop()
            .into(binding.profileBanner);
        binding.profileBannerVeil.setVisibility(View.VISIBLE);
        binding.profileBannerEditButton.setVisibility(isMine ? View.VISIBLE : View.GONE);
        binding.profileBannerAddButton.setVisibility(View.GONE);
    }

    private void showDefaultBanner() {
        Glide.with(this).clear(binding.profileBanner);   // 이전 썸네일 지우기 → 바탕 그라데이션이 보임
        binding.profileBannerVeil.setVisibility(View.GONE);
        binding.profileBannerEditButton.setVisibility(View.GONE);
        binding.profileBannerAddButton.setVisibility(isMine ? View.VISIBLE : View.GONE);
    }
}
