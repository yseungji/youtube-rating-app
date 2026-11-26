package com.example.viewit.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.viewit.R;
import com.example.viewit.SettingsActivity;
import com.example.viewit.data.BlockRepository;
import com.example.viewit.data.FollowRepository;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.FragmentProfileBinding;
import com.example.viewit.ui.common.SystemBarInsets;
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
    private final BlockRepository blockRepository = new BlockRepository();

    private String myUid;          // 로그인한 사람
    private String profileUid;     // 이 화면에 보여 주는 사람
    private boolean isMine;        // 내 프로필인지
    private User profile;          // 보여 주는 사람의 프로필 (아직 못 불러왔으면 null)
    private boolean following;     // (다른 사람 프로필) 내가 팔로우 중인지
    private boolean blocked;       // (다른 사람 프로필) 내가 차단했는지 (6.3)
    private int statusBarHeight;   // 상태바 높이. 배너를 그만큼 위로 늘려 상태바 뒤까지 깐다 (Figma 05-D)
    private boolean bannerDark;    // 배너 맨 위가 어두운지 → 상태바 글씨를 흰색으로
    private boolean headerCollapsed;   // 프로필 상단이 다 접혀서 상태바 뒤가 흰 가림막인지

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
        setupStatusBarArea();
        listenProfile(profileUid);
        loadRatingCount(profileUid);
        loadFollowCounts(profileUid);

        binding.profileShareButton.setOnClickListener(v -> shareProfile());
        binding.profileEditButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ProfileEditActivity.class);
            startActivity(intent);
        });
        binding.profileSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SettingsActivity.class);
            startActivity(intent);
        });
        binding.profileBannerEditButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());
        binding.profileBannerAddButton.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.profile_coming_soon, Toast.LENGTH_SHORT).show());

        // 평가 숫자를 누르면 이 프로필 주인의 평가한 영상 목록
        binding.profileRatingStat.setOnClickListener(v -> {
            if (blocked) return;   // 차단한 사람의 목록은 열지 않는다
            Intent intent = new Intent(requireContext(), RatedVideosActivity.class);
            intent.putExtra(RatedVideosActivity.EXTRA_UID, profileUid);
            startActivity(intent);
        });

        // 팔로워·팔로잉 숫자를 누르면 목록 화면
        binding.profileFollowerStat.setOnClickListener(v -> openFollowList(FollowListActivity.TAB_FOLLOWERS));
        binding.profileFollowingStat.setOnClickListener(v -> openFollowList(FollowListActivity.TAB_FOLLOWING));

        if (!isMine) {
            setupOtherProfile();
        }
    }

    // 팔로워·팔로잉 목록 열기. 목록 화면 위 제목은 이 프로필 주인 이름 (아직 못 불러왔으면 빈칸)
    private void openFollowList(int tab){
        if (blocked) return;
        Intent intent = new Intent(requireContext(), FollowListActivity.class);
        intent.putExtra(FollowListActivity.EXTRA_UID, profileUid);
        String title = profile == null ? "" : profile.getDisplayName();
        intent.putExtra(FollowListActivity.EXTRA_TITLE, title);
        intent.putExtra(FollowListActivity.EXTRA_TAB, tab);
        startActivity(intent);
    }

    // ---------- 다른 사람 프로필 (6.2) ----------

    // 배너 위 ← 보이기, 설정·편집·배너 버튼 숨기기, 팔로우 버튼 보이기
    private void setupOtherProfile() {
        binding.profileBack.setVisibility(View.VISIBLE);
        binding.profileBack.setOnClickListener(v -> requireActivity().finish());
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

        // ⋮ 메뉴와 차단 안내 (6.3)
        binding.profileMoreButton.setVisibility(View.VISIBLE);
        binding.profileMoreButton.setOnClickListener(v -> showMoreMenu());
        binding.profileBlockedNotice.emptyIcon.setImageResource(R.drawable.ic_block);
        binding.profileBlockedNotice.emptyTitle.setText(R.string.profile_blocked_title);
        binding.profileBlockedNotice.emptyMessage.setText(R.string.profile_blocked_message);
        binding.profileBlockedNotice.emptyAction.setText(R.string.block_menu_unblock);
        binding.profileBlockedNotice.emptyAction.setVisibility(View.VISIBLE);
        binding.profileBlockedNotice.emptyAction.setOnClickListener(v -> unblockUser());
        loadBlockedState();
    }

    // ---------- 차단 (6.3) ----------

    private void loadBlockedState() {
        blockRepository.isBlocked(myUid, profileUid, new BlockRepository.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (binding == null) return;
                blocked = result;
                showBlockedState(blocked);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "block state failed", error);
            }
        });
    }

    // ⋮ → 차단하기 또는 차단 해제 (지금 상태에 따라 한 가지만)
    private void showMoreMenu() {
        PopupMenu menu = new PopupMenu(requireContext(), binding.profileMoreButton);
        menu.getMenu().add(blocked ? R.string.block_menu_unblock : R.string.block_menu_block);
        menu.setOnMenuItemClickListener(item -> {
            if (blocked) {
                unblockUser();
            } else {
                confirmBlock();
            }
            return true;
        });
        menu.show();
    }

    // 차단 확인창. "차단"을 누르면 blockUser()
    private void confirmBlock() {
        String name = profile == null ? "" : profile.getDisplayName();
        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.block_dialog_title, name))
            .setMessage(R.string.block_dialog_message)
            .setPositiveButton(R.string.block_dialog_confirm, (diaglog, which)-> blockUser())
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void blockUser() {
        blockRepository.block(myUid, profileUid, new BlockRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (binding == null) return;
                blocked = true;
                following = false;        // 차단하면 서로의 팔로우가 끊긴다
                showFollowButton();
                showBlockedState(true);
                loadFollowCounts(profileUid);
                Toast.makeText(requireContext(), R.string.block_done, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding == null) return;
                Toast.makeText(requireContext(), R.string.block_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 차단 해제는 확인창 없이 바로 (끊긴 팔로우는 되돌리지 않음)
    private void unblockUser() {
        blockRepository.unblock(myUid, profileUid, new BlockRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (binding == null) return;
                blocked = false;
                showBlockedState(false);
                Toast.makeText(requireContext(), R.string.unblock_done, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding == null) return;
                Toast.makeText(requireContext(), R.string.unblock_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 차단했으면 탭·탭 화면·팔로우 버튼을 숨기고 차단 안내를 보인다. 해제하면 반대로.
    private void showBlockedState(boolean isBlocked) {
        if(isBlocked) {
            binding.profileTabs.setVisibility(View.GONE);
            binding.profilePager.setVisibility(View.GONE);
            binding.profileFollowButton.setVisibility(View.GONE);
            binding.profileBlocked.setVisibility(View.VISIBLE);
        }
        else {
            binding.profileTabs.setVisibility(View.VISIBLE);
            binding.profilePager.setVisibility(View.VISIBLE);
            binding.profileFollowButton.setVisibility(View.VISIBLE);
            binding.profileBlocked.setVisibility(View.GONE);
        }
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
        applyStatusBarIcons();
    }

    // 다른 탭·화면으로 가면 상태바 글씨를 앱 기본(검정)으로 돌려놓는다
    @Override
    public void onPause() {
        super.onPause();
        SystemBarInsets.setDarkStatusBarBackground(requireActivity(), false);
    }

    // ---------- 상태바 영역 (Figma 05-D: 배너가 상태바 뒤까지) ----------

    private void setupStatusBarArea() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.profileHeader, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            if (top != statusBarHeight) {
                statusBarHeight = top;
                layoutUnderStatusBar();
            }
            return insets;
        });
        SystemBarInsets.requestWhenAttached(binding.profileHeader);

        // 아래로 스크롤해 상단이 다 접히면 상태바 뒤를 흰 가림막으로 덮고 글씨는 검정으로
        binding.profileAppBar.addOnOffsetChangedListener((appBar, offset) -> {
            if (binding == null) return;
            int range = appBar.getTotalScrollRange();
            float fraction = range == 0 ? 0f : -offset / (float) range;
            binding.profileStatusScrim.setAlpha(Math.max(0f, (fraction - 0.85f) / 0.15f));
            boolean collapsed = fraction > 0.95f;
            if (collapsed != headerCollapsed) {
                headerCollapsed = collapsed;
                applyStatusBarIcons();
            }
        });
    }

    // 배너·흰 막 높이, 사진·이름 위 여백, 배너 위 버튼 위치에 상태바 높이를 더한다
    private void layoutUnderStatusBar() {
        int bannerHeight = getResources().getDimensionPixelSize(R.dimen.profile_banner_height) + statusBarHeight;
        setHeight(binding.profileBanner, bannerHeight);
        setHeight(binding.profileBannerVeil, bannerHeight);

        View content = binding.profileHeaderContent;
        content.setPadding(content.getPaddingLeft(),
            getResources().getDimensionPixelSize(R.dimen.profile_header_top_padding) + statusBarHeight,
            content.getPaddingRight(), content.getPaddingBottom());

        int buttonTop = getResources().getDimensionPixelSize(R.dimen.space_m) + statusBarHeight;
        View[] buttons = { binding.profileBack, binding.profileSettingsButton, binding.profileMoreButton,
            binding.profileBannerEditButton, binding.profileBannerAddButton };
        for (View button : buttons) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) button.getLayoutParams();
            lp.topMargin = buttonTop;
            button.setLayoutParams(lp);
        }

        // 다 접혀도 상태바 높이만큼은 남아서 탭 줄이 상태바 아래에 붙는다. 그 자리는 흰 가림막이 덮는다
        binding.profileHeader.setMinimumHeight(statusBarHeight);
        setHeight(binding.profileStatusScrim, statusBarHeight);
    }

    private static void setHeight(View view, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.height = height;
        view.setLayoutParams(lp);
    }

    // 지금 보이는 화면일 때만 상태바 글씨 색을 정한다: 배너가 어둡고 상단이 펼쳐져 있으면 흰색
    private void applyStatusBarIcons() {
        if (!isResumed() || binding == null) return;
        SystemBarInsets.setDarkStatusBarBackground(requireActivity(), bannerDark && !headerCollapsed);
    }

    // 배너 그림에서 상태바 뒤에 오는 맨 위 줄의 밝기. 흰 막(위쪽 흰색 45%)을 덮은 뒤의 밝기로 판단한다
    private boolean isTopDark(Bitmap bitmap) {
        int viewHeight = binding.profileBanner.getHeight() > 0
            ? binding.profileBanner.getHeight() : binding.profileBanner.getLayoutParams().height;
        int rows = bitmap.getHeight() * Math.max(statusBarHeight, 1) / Math.max(viewHeight, 1);
        rows = Math.max(1, Math.min(bitmap.getHeight(), rows));
        int stepX = Math.max(1, bitmap.getWidth() / 24);
        int stepY = Math.max(1, rows / 6);
        double sum = 0;
        int count = 0;
        for (int y = 0; y < rows; y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int c = bitmap.getPixel(x, y);
                sum += (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0;
                count++;
            }
        }
        double photo = count == 0 ? 1 : sum / count;
        double withVeil = 0.45 + 0.55 * photo;
        return withVeil < 0.55;
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
            .disallowHardwareConfig()   // 밝기를 재려면 픽셀을 읽을 수 있어야 한다
            .listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model,
                                            @NonNull Target<Drawable> target, boolean isFirstResource) {
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                               Target<Drawable> target, @NonNull DataSource dataSource,
                                               boolean isFirstResource) {
                    if (binding != null && resource instanceof BitmapDrawable) {
                        bannerDark = isTopDark(((BitmapDrawable) resource).getBitmap());
                        applyStatusBarIcons();
                    }
                    return false;
                }
            })
            .into(binding.profileBanner);
        binding.profileBannerVeil.setVisibility(View.VISIBLE);
        binding.profileBannerEditButton.setVisibility(isMine ? View.VISIBLE : View.GONE);
        binding.profileBannerAddButton.setVisibility(View.GONE);
    }

    private void showDefaultBanner() {
        Glide.with(this).clear(binding.profileBanner);   // 이전 썸네일 지우기 → 바탕 그라데이션이 보임
        bannerDark = false;                              // 기본 배너는 연주황이라 글씨는 검정
        applyStatusBarIcons();
        binding.profileBannerVeil.setVisibility(View.GONE);
        binding.profileBannerEditButton.setVisibility(View.GONE);
        binding.profileBannerAddButton.setVisibility(isMine ? View.VISIBLE : View.GONE);
    }
}
