package com.example.viewit.ui.profile;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.data.BlockRepository;
import com.example.viewit.data.FollowRepository;
import com.example.viewit.data.model.FollowListItem;
import com.example.viewit.databinding.ActivityFollowListBinding;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 팔로워·팔로잉 목록 (6.2, Figma "프로필 · 팔로워" / "프로필 · 팔로잉").
// 여는 쪽: intent에 EXTRA_UID(누구의 목록), EXTRA_TITLE(위에 보일 이름), EXTRA_TAB(TAB_FOLLOWERS 또는 TAB_FOLLOWING)
public class FollowListActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TAB = "tab";
    public static final int TAB_FOLLOWERS = 0;
    public static final int TAB_FOLLOWING = 1;

    private ActivityFollowListBinding binding;
    private final FollowRepository followRepository = new FollowRepository();
    private final BlockRepository blockRepository = new BlockRepository();
    private FollowListAdapter adapter;
    private String uid;      // 누구의 목록인지
    private String myUid;    // 로그인한 사람 (버튼 모양·팔로우 동작 기준)
    private List<FollowListItem> allItems = new ArrayList<>();   // 지금 탭에서 불러온 전체 목록 (검색 전)
    private boolean followerTab = true;                         // 지금 보고 있는 탭

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityFollowListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = getIntent().getStringExtra(EXTRA_UID);
        if (user == null || TextUtils.isEmpty(uid)) {
            finish();
            return;
        }
        myUid = user.getUid();

        binding.followBack.getRoot().setOnClickListener(v -> finish());
        binding.followTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));

        adapter = new FollowListAdapter(new FollowListAdapter.OnFollowRowListener() {
            @Override
            public void onRowClick(FollowListItem item) {
                openUserProfile(item.getUid());
            }

            @Override
            public void onFollowButtonClick(FollowListItem item, int position) {
                toggleFollow(item, position);
            }

            @Override
            public void onMoreClick(FollowListItem item, int position, View anchor) {
                showMoreMenu(item, position, anchor);
            }
        });
        adapter.setShowMore(uid.equals(myUid));   // ⋮ 메뉴는 내 팔로워·팔로잉 목록에서만
        binding.followList.setLayoutManager(new LinearLayoutManager(this));
        binding.followList.setAdapter(adapter);

        // 이름·아이디 검색: 글자를 바꿀 때마다 불러온 목록에서 바로 걸러 보여 준다 (서버 조회 없음)
        binding.followSearch.searchInput.setHint(R.string.follow_search_hint);
        binding.followSearch.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applySearch();
            }
        });

        binding.followTabs.addTab(binding.followTabs.newTab().setText(getString(R.string.follow_tab_followers, 0)));
        binding.followTabs.addTab(binding.followTabs.newTab().setText(getString(R.string.follow_tab_following, 0)));
        binding.followTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadList(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                loadList(tab.getPosition());
            }
        });

        int startTab = getIntent().getIntExtra(EXTRA_TAB, TAB_FOLLOWERS);
        TabLayout.Tab tab = binding.followTabs.getTabAt(startTab);
        if (tab != null) {
            tab.select();
        }
        if (startTab == TAB_FOLLOWERS) {
            loadList(TAB_FOLLOWERS);   // 첫 탭은 이미 선택돼 있어서 onTabSelected가 불리지 않는다
        }
        loadCounts();
    }

    // 프로필에서 평가를 보고 돌아오면 "새 평가" 표시가 바뀌었을 수 있어 지금 탭을 다시 불러온다
    @Override
    protected void onRestart() {
        super.onRestart();
        loadList(binding.followTabs.getSelectedTabPosition());
        loadCounts();
    }

    // 줄을 누르면 그 사람 프로필을 연다
    private void openUserProfile(String otherUid) {
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_UID, otherUid);
        startActivity(intent);
    }

    // ---------- 목록 조회·팔로우 (Viewit 전용) ----------

    private void loadList(int tab) {
        binding.followLoading.setVisibility(View.VISIBLE);
        binding.followEmpty.setVisibility(View.GONE);
        boolean loadingFollowerTab = tab == TAB_FOLLOWERS;
        FollowRepository.Callback<List<FollowListItem>> callback = new FollowRepository.Callback<List<FollowListItem>>() {
            @Override
            public void onSuccess(List<FollowListItem> result) {
                if (isFinishing() || binding.followTabs.getSelectedTabPosition() != tab) {
                    return;   // 그사이 다른 탭을 고름
                }
                binding.followLoading.setVisibility(View.GONE);
                allItems = result;
                followerTab = loadingFollowerTab;
                applySearch();   // 검색어가 있으면 새 탭 목록에도 그대로 적용
                if (!loadingFollowerTab && uid.equals(myUid)) {
                    loadNewRatingCounts(result);   // 내 팔로잉 목록: "새 평가 N개"
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (isFinishing()) return;
                binding.followLoading.setVisibility(View.GONE);
                Toast.makeText(FollowListActivity.this, R.string.follow_list_load_failed, Toast.LENGTH_SHORT).show();
            }
        };
        if (loadingFollowerTab) {
            followRepository.loadFollowers(uid, myUid, callback);
        } else {
            followRepository.loadFollowing(uid, myUid, callback);
        }
    }

    // 내가 팔로우하는 사람마다 새 평가 수를 받아 줄에 표시한다
    private void loadNewRatingCounts(List<FollowListItem> items) {
        List<String> uids = new ArrayList<>();
        for (FollowListItem item : items) {
            uids.add(item.getUid());
        }
        followRepository.loadNewRatingCounts(myUid, uids, new FollowRepository.Callback<Map<String, Long>>() {
            @Override
            public void onSuccess(Map<String, Long> counts) {
                if (isFinishing()) return;
                for (FollowListItem item : items) {
                    Long count = counts.get(item.getUid());
                    item.setNewRatingCount(count == null ? 0 : count);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(@NonNull Exception error) {
            }
        });
    }

    // ---------- ⋮ 메뉴 (6.2) ----------

    // 팔로워 탭: 팔로워 삭제·차단하기 / 팔로잉 탭: 팔로우 취소·차단하기
    private void showMoreMenu(FollowListItem item, int position, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        final int first = 1;
        final int block = 2;
        menu.getMenu().add(0, first, 0, followerTab ? R.string.follow_menu_remove : R.string.follow_menu_unfollow);
        menu.getMenu().add(0, block, 1, R.string.block_menu_block);
        menu.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == block) {
                confirmBlock(item);
            } else if (followerTab) {
                confirmRemoveFollower(item);
            } else if (item.isFollowedByMe()) {
                toggleFollow(item, position);   // 팔로우 취소는 확인창 없이 (버튼과 같다)
            }
            return true;
        });
        menu.show();
    }

    // 팔로워 삭제 확인창. "삭제"를 누르면 removeFollower(item)
    private void confirmRemoveFollower(FollowListItem item) {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_follower_dialog_title, item.getDisplayName()))
            .setMessage(R.string.remove_follower_dialog_message)
            .setPositiveButton(R.string.remove_follower_confirm, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removeFollower(item);
                }
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show();
    }

    // 차단 확인창 (프로필 ⋮ 차단과 같은 문구). "차단"을 누르면 blockUser(item)
    private void confirmBlock(FollowListItem item) {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.block_dialog_title, item.getDisplayName()))
            .setMessage(R.string.block_dialog_message)
            .setPositiveButton(R.string.block_dialog_confirm, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    blockUser(item);
                }
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show();
    }

    private void removeFollower(FollowListItem item) {
        followRepository.removeFollower(myUid, item.getUid(), new FollowRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                removeRow(item);
                Toast.makeText(FollowListActivity.this, R.string.remove_follower_done, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(FollowListActivity.this, R.string.remove_follower_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void blockUser(FollowListItem item) {
        blockRepository.block(myUid, item.getUid(), new BlockRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                removeRow(item);   // 차단하면 서로의 팔로우가 끊겨 두 탭 어디에도 없다
                Toast.makeText(FollowListActivity.this, R.string.block_done, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(FollowListActivity.this, R.string.block_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 목록에서 그 사람 줄을 빼고 탭 숫자를 맞춘다
    private void removeRow(FollowListItem item) {
        List<FollowListItem> rest = new ArrayList<>(allItems);
        rest.remove(item);
        allItems = rest;
        applySearch();
        loadCounts();
    }

    // 검색어가 이름이나 아이디에 들어 있는 사람만 보여 준다 (대소문자 구분 없음, 앞의 @는 무시)
    private void applySearch() {
        String query = binding.followSearch.searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (query.startsWith("@")) {
            query = query.substring(1);
        }
        List<FollowListItem> shown;
        if (query.isEmpty()) {
            shown = allItems;
        } else {
            shown = new ArrayList<>();
            for (FollowListItem item : allItems) {
                if (contains(item.getDisplayName(), query) || contains(item.getUsername(), query)) {
                    shown.add(item);
                }
            }
        }
        adapter.setItems(shown, followerTab);

        if (!shown.isEmpty() || binding.followLoading.getVisibility() == View.VISIBLE) {
            binding.followEmpty.setVisibility(View.GONE);
        } else if (query.isEmpty()) {
            binding.followEmpty.setText(followerTab ? R.string.follow_empty_followers : R.string.follow_empty_following);
            binding.followEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.followEmpty.setText(getString(R.string.follow_search_empty,
                binding.followSearch.searchInput.getText().toString().trim()));
            binding.followEmpty.setVisibility(View.VISIBLE);
        }
    }

    private static boolean contains(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    // 탭 이름의 숫자 ("팔로워 2" / "팔로잉 3")
    private void loadCounts() {
        followRepository.countFollowers(uid, new FollowRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long count) {
                setTabText(TAB_FOLLOWERS, getString(R.string.follow_tab_followers, count.intValue()));
            }

            @Override
            public void onError(@NonNull Exception error) {
            }
        });
        followRepository.countFollowing(uid, new FollowRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long count) {
                setTabText(TAB_FOLLOWING, getString(R.string.follow_tab_following, count.intValue()));
            }

            @Override
            public void onError(@NonNull Exception error) {
            }
        });
    }

    private void setTabText(int position, String text) {
        TabLayout.Tab tab = binding.followTabs.getTabAt(position);
        if (tab != null) {
            tab.setText(text);
        }
    }

    // 팔로우 ↔ 언팔로우 후 그 줄만 다시 그리고 탭 숫자를 맞춘다
    private void toggleFollow(FollowListItem item, int position) {
        if (item.getUid().equals(myUid)) {
            return;   // 자기 자신
        }
        boolean wasFollowing = item.isFollowedByMe();
        FollowRepository.Callback<Void> done = new FollowRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                item.setFollowedByMe(!wasFollowing);
                adapter.notifyItemChanged(position);
                loadCounts();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(FollowListActivity.this,
                    wasFollowing ? R.string.unfollow_failed : R.string.follow_failed, Toast.LENGTH_SHORT).show();
            }
        };
        if (wasFollowing) {
            followRepository.unfollow(myUid, item.getUid(), done);
        } else {
            followRepository.follow(myUid, item.getUid(), done);
        }
    }
}
