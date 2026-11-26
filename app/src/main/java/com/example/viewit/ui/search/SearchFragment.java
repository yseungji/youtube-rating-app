package com.example.viewit.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.viewit.R;
import com.example.viewit.data.FollowRepository;
import com.example.viewit.data.SearchRepository;
import com.example.viewit.data.VideoCachePolicy;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.FollowListItem;
import com.example.viewit.data.model.Video;
import com.example.viewit.data.youtube.YouTubeApi;
import com.example.viewit.data.youtube.YouTubeException;
import com.example.viewit.databinding.FragmentSearchBinding;
import com.example.viewit.databinding.PageSearchUsersBinding;
import com.example.viewit.databinding.PageSearchVideosBinding;
import com.example.viewit.ui.category.CategoryVideoAdapter;
import com.example.viewit.ui.common.YouTubeErrorMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 탐색 탭 (4.4).
// [검색 전] 최근 검색어 (기기에 저장, RecentSearchStore)
// [검색 후] 영상·사용자 칩
//   - 영상: 서비스에 등록된 영상(우리 앱에서 평가된 영상) → "YouTube에서 더 찾기" 버튼을 누르면 YouTube 검색(100유닛)
//   - 사용자: 이름·아이디에 검색어가 들어 있는 사람 + 팔로우 버튼
// 검색어는 제목·이름 "중간 글자"로도 찾는다 (SearchRepository 참고)
public class SearchFragment extends Fragment
        implements SearchHistoryAdapter.Listener, UserResultAdapter.Listener {

    private FragmentSearchBinding binding;
    // 검색 결과 두 페이지 (search_pager 0번 = 영상, 1번 = 사용자)
    private PageSearchVideosBinding videoPage;
    private PageSearchUsersBinding userPage;

    private RecentSearchStore recentSearchStore;
    private final List<String> histories = new ArrayList<>();
    private SearchHistoryAdapter historyAdapter;

    private final List<Video> registeredVideos = new ArrayList<>();
    private final List<Video> youtubeVideos = new ArrayList<>();
    private final List<FollowListItem> users = new ArrayList<>();
    private CategoryVideoAdapter registeredAdapter;
    private CategoryVideoAdapter youtubeAdapter;
    private UserResultAdapter userAdapter;

    private final SearchRepository searchRepository = new SearchRepository();
    private final VideoRepository videoRepository = new VideoRepository();
    private final FollowRepository followRepository = new FollowRepository();

    private String currentQuery = "";
    // 검색을 빨리 다시 해도 늦게 도착한 예전 결과로 화면을 덮지 않도록 검색마다 번호를 붙인다
    private int searchId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        videoPage = PageSearchVideosBinding.inflate(inflater);
        userPage = PageSearchUsersBinding.inflate(inflater);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recentSearchStore = new RecentSearchStore(requireContext());

        historyAdapter = new SearchHistoryAdapter(histories, this);
        binding.searchHistoryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchHistoryList.setAdapter(historyAdapter);

        registeredAdapter = new CategoryVideoAdapter(registeredVideos);
        videoPage.searchVideoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        videoPage.searchVideoList.setAdapter(registeredAdapter);

        youtubeAdapter = new CategoryVideoAdapter(youtubeVideos);
        videoPage.searchYoutubeList.setLayoutManager(new LinearLayoutManager(requireContext()));
        videoPage.searchYoutubeList.setAdapter(youtubeAdapter);

        userAdapter = new UserResultAdapter(users, this);
        userPage.searchUserList.setLayoutManager(new LinearLayoutManager(requireContext()));
        userPage.searchUserList.setAdapter(userAdapter);

        setupSearchField();

        // 최근 검색어 전체 삭제
        binding.searchHistoryClear.setOnClickListener(v -> {
            recentSearchStore.clear();
            refreshHistory();
        });

        setupResultPages();

        videoPage.searchYoutubeButton.setOnClickListener(v -> searchYouTube());

        refreshHistory();
    }

    // 영상·사용자 결과 페이지: 칩을 누르면 그 페이지로, 좌우로 밀면 칩도 따라 바뀐다
    private void setupResultPages() {
        binding.searchPager.setAdapter(new StaticPagesAdapter(videoPage.getRoot(), userPage.getRoot()));
        binding.searchPager.setOffscreenPageLimit(1);
        binding.searchTabGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int page = checkedIds.get(0) == R.id.search_tab_video ? 0 : 1;
            if (binding.searchPager.getCurrentItem() != page) {
                binding.searchPager.setCurrentItem(page);
            }
        });
        binding.searchPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (binding != null) {
                    binding.searchTabGroup.check(position == 0 ? R.id.search_tab_video : R.id.search_tab_user);
                }
            }
        });
    }

    // ViewPager2에 미리 만들어 둔 페이지 두 개를 그대로 보여 주는 어댑터
    private static class StaticPagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View[] pages;

        StaticPagesAdapter(View... pages) {
            this.pages = pages;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View page = pages[viewType];
            if (page.getParent() instanceof ViewGroup) {
                ((ViewGroup) page.getParent()).removeView(page);
            }
            page.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new RecyclerView.ViewHolder(page) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return pages.length;
        }
    }

    // 키보드의 검색 버튼(엔터)으로 검색하고, 검색창을 비우면(X 버튼 포함) 최근 검색어 화면으로 돌아간다
    private void setupSearchField() {
        EditText input = binding.searchField.searchInput;
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                search(input.getText().toString());
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean empty = s.toString().trim().isEmpty();
                binding.searchField.searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (empty) {
                    showHistory();
                }
            }
        });

        // X: 검색어를 비우고(→ 최근 검색어 화면) 바로 다시 입력할 수 있게 키보드를 띄운다
        binding.searchField.searchClear.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    // ---------- 최근 검색어 ----------

    // 저장된 최근 검색어를 다시 읽어 목록을 새로 채운다
    private void refreshHistory() {
        histories.clear();
        histories.addAll(recentSearchStore.load());
        historyAdapter.notifyDataSetChanged();
        binding.searchHistoryEmpty.setVisibility(histories.isEmpty() ? View.VISIBLE : View.GONE);
        binding.searchHistoryClear.setVisibility(histories.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showHistory() {
        searchId++; // 진행 중인 검색 결과는 버린다
        currentQuery = "";
        binding.searchResultSection.setVisibility(View.GONE);
        binding.searchHistorySection.setVisibility(View.VISIBLE);
        refreshHistory();
    }

    @Override
    public void onHistoryClick(String query) {
        EditText input = binding.searchField.searchInput;
        input.setText(query);
        input.setSelection(query.length());
        search(query);
    }

    @Override
    public void onHistoryDelete(String query) {
        recentSearchStore.remove(query);
        refreshHistory();
    }

    // ---------- 검색 ----------

    private void search(String rawQuery) {
        String query = rawQuery.trim();
        if (query.isEmpty()) {
            return;
        }
        currentQuery = query;
        recentSearchStore.add(query);
        hideKeyboard();

        binding.searchHistorySection.setVisibility(View.GONE);
        binding.searchResultSection.setVisibility(View.VISIBLE);
        binding.searchLoading.setVisibility(View.VISIBLE);
        videoPage.searchVideoEmpty.setVisibility(View.GONE);
        userPage.searchUserEmpty.setVisibility(View.GONE);
        registeredVideos.clear();
        users.clear();
        registeredAdapter.notifyDataSetChanged();
        userAdapter.notifyDataSetChanged();
        resetYouTube();
        videoPage.getRoot().scrollTo(0, 0);

        int id = ++searchId;
        searchRegisteredVideos(query, id);
        searchUsers(query, id);
    }

    // 서비스에 등록된 영상 (우리 앱에서 평가된 영상 중 제목·채널에 검색어가 있는 것)
    private void searchRegisteredVideos(String query, int id) {
        searchRepository.searchRegisteredVideos(query, new SearchRepository.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> result) {
                if (binding == null || id != searchId) {
                    return;
                }
                binding.searchLoading.setVisibility(View.GONE);
                registeredVideos.clear();
                registeredVideos.addAll(result);
                registeredAdapter.notifyDataSetChanged();
                showEmpty(videoPage.searchVideoEmpty, result.isEmpty(),
                        getString(R.string.search_registered_empty, query));
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding == null || id != searchId) {
                    return;
                }
                binding.searchLoading.setVisibility(View.GONE);
                showEmpty(videoPage.searchVideoEmpty, true, YouTubeErrorMessage.of(requireContext(), error));
            }
        });
    }

    // 이름·아이디에 검색어가 있는 사람
    private void searchUsers(String query, int id) {
        String myUid = myUid();
        if (myUid == null) {
            return;
        }
        searchRepository.searchUsers(query, myUid, new SearchRepository.Callback<List<FollowListItem>>() {
            @Override
            public void onSuccess(List<FollowListItem> result) {
                if (binding == null || id != searchId) {
                    return;
                }
                users.clear();
                users.addAll(result);
                userAdapter.notifyDataSetChanged();
                showEmpty(userPage.searchUserEmpty, result.isEmpty(), getString(R.string.search_user_empty, query));
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding == null || id != searchId) {
                    return;
                }
                showEmpty(userPage.searchUserEmpty, true, getString(R.string.search_failed));
            }
        });
    }

    // ---------- YouTube에서 더 찾기 (버튼을 눌렀을 때만, 100유닛) ----------

    private void resetYouTube() {
        youtubeVideos.clear();
        youtubeAdapter.notifyDataSetChanged();
        videoPage.searchYoutubeTitle.setVisibility(View.GONE);
        videoPage.searchYoutubeList.setVisibility(View.GONE);
        videoPage.searchYoutubeMessage.setVisibility(View.GONE);
        videoPage.searchYoutubeLoading.setVisibility(View.GONE);
        videoPage.searchYoutubeButton.setEnabled(true);
    }

    private void searchYouTube() {
        if (currentQuery.isEmpty()) {
            return;
        }
        String query = currentQuery;
        int id = searchId;
        videoPage.searchYoutubeButton.setEnabled(false); // 같은 검색어로 두 번 누르지 않게 (100유닛)
        videoPage.searchYoutubeTitle.setVisibility(View.VISIBLE);
        videoPage.searchYoutubeLoading.setVisibility(View.VISIBLE);
        videoPage.searchYoutubeMessage.setVisibility(View.GONE);

        YouTubeApi.getInstance().searchVideos(query, new YouTubeApi.Callback<List<Video>>() {
            @Override
            public void onSuccess(List<Video> result) {
                if (binding == null || id != searchId) {
                    return;
                }
                videoPage.searchYoutubeLoading.setVisibility(View.GONE);
                youtubeVideos.clear();
                youtubeVideos.addAll(result);
                youtubeAdapter.notifyDataSetChanged();
                videoPage.searchYoutubeList.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
                showEmpty(videoPage.searchYoutubeMessage, result.isEmpty(), getString(R.string.search_youtube_empty));
                attachRatings(result, id);
            }

            @Override
            public void onError(YouTubeException error) {
                if (binding == null || id != searchId) {
                    return;
                }
                videoPage.searchYoutubeLoading.setVisibility(View.GONE);
                videoPage.searchYoutubeButton.setEnabled(true);
                showEmpty(videoPage.searchYoutubeMessage, true, YouTubeErrorMessage.of(requireContext(), error));
            }
        });
    }

    // YouTube 검색 결과 중 우리 앱에서 평가된 영상에 평균 별점·평가 수를 붙인다 (Firestore 읽기만)
    private void attachRatings(List<Video> videos, int id) {
        List<String> ids = new ArrayList<>();
        for (Video video : videos) {
            ids.add(video.getVideoId());
        }
        videoRepository.getSavedVideos(ids, new VideoRepository.Callback<Map<String, Video>>() {
            @Override
            public void onSuccess(Map<String, Video> saved) {
                if (binding == null || id != searchId) {
                    return;
                }
                for (Video video : videos) {
                    VideoCachePolicy.copyStats(saved.get(video.getVideoId()), video);
                }
                youtubeAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(@NonNull Exception error) {
                // 별점을 못 붙여도 목록은 그대로 둔다
            }
        });
    }

    // ---------- 팔로우 ----------

    @Override
    public void onFollowClick(FollowListItem item, int position) {
        String myUid = myUid();
        if (myUid == null) {
            return;
        }
        boolean wasFollowing = item.isFollowedByMe();
        FollowRepository.Callback<Void> done = new FollowRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                item.setFollowedByMe(!wasFollowing);
                if (binding != null) {
                    userAdapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), wasFollowing ? R.string.unfollow_failed : R.string.follow_failed,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
        if (wasFollowing) {
            followRepository.unfollow(myUid, item.getUid(), done);
        } else {
            followRepository.follow(myUid, item.getUid(), done);
        }
    }

    // ---------- 도우미 ----------

    // 안내 문구(빈 결과·오류)를 보이거나 숨긴다
    private void showEmpty(TextView messageView, boolean show, String message) {
        messageView.setText(message);
        messageView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Nullable
    private static String myUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.searchField.searchInput.getWindowToken(), 0);
        }
        binding.searchField.searchInput.clearFocus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        videoPage = null;
        userPage = null;
    }
}
