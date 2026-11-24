package com.example.viewit.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.RatedVideoItem;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
import com.example.viewit.databinding.ActivityRatedVideosBinding;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.example.viewit.ui.videodetail.VideoDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 평가한 영상 목록 (6.1). 여는 쪽: EXTRA_UID에 누구의 평가인지.
// 내 목록은 비공개 평가까지, 다른 사람 목록은 공개 평가만 보여 준다.
public class RatedVideosActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "uid";
    private static final String TAG = "RatedVideos";

    private ActivityRatedVideosBinding binding;
    private RatedVideoAdapter adapter;
    private final VideoRepository videoRepository = new VideoRepository();
    private String uid;          // 누구의 평가인지 (인텐트로 받음)
    private boolean isMine;
    private List<RatedVideoItem> items = new ArrayList<>();

    // 영상 상세에 갔다 돌아오면 다시 불러온다 (평가를 고치거나 지웠을 수 있음)
    private final ActivityResultLauncher<Intent> videoLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> loadRatings());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityRatedVideosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        uid = getIntent().getStringExtra(EXTRA_UID);   // 누구의 평가인지
        setupScreen();   // 평가 목록 불러오기

        binding.ratedBack.getRoot().setOnClickListener(v -> finish());
    }

    // 줄을 누르면 영상 상세를 런처로 연다 (돌아오면 목록을 다시 불러온다)
    private void openVideo(RatedVideoItem item) {
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, item.getVideoId());
        intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, item.getCategoryId());
        videoLauncher.launch(intent);
    }

    // ---------- 불러오기·정렬 ----------

    private void setupScreen() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || TextUtils.isEmpty(uid)) {
            finish();
            return;
        }
        isMine = uid.equals(user.getUid());

        adapter = new RatedVideoAdapter(this::openVideo);
        binding.ratedList.setLayoutManager(new LinearLayoutManager(this));
        binding.ratedList.setAdapter(adapter);
        binding.ratedSortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> showSorted());
        binding.ratedTitle.setText(getString(R.string.rated_title, 0));
        loadRatings();
    }

    // 평가 목록 → 영상 정보(제목·썸네일)를 한 번에 받아 합친다
    private void loadRatings() {
        if (binding == null || uid == null) return;
        binding.ratedLoading.setVisibility(View.VISIBLE);
        binding.ratedEmpty.setVisibility(View.GONE);
        Query query = FirebaseFirestore.getInstance()
            .collection(FirestorePaths.RATINGS)
            .whereEqualTo("userId", uid);
        if (!isMine) {
            query = query.whereEqualTo("visibility", Rating.VISIBILITY_PUBLIC);
        }
        query.orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(result -> {
                List<Rating> ratings = result.toObjects(Rating.class);
                if (ratings.isEmpty()) {
                    showItems(new ArrayList<>());
                    return;
                }
                List<String> videoIds = new ArrayList<>();
                for (Rating r : ratings) videoIds.add(r.getVideoId());
                videoRepository.getVideos(videoIds, new VideoRepository.Callback<List<Video>>() {
                    @Override
                    public void onSuccess(List<Video> videos) {
                        Map<String, Video> byId = new HashMap<>();
                        for (Video v : videos) byId.put(v.getVideoId(), v);
                        showRatings(ratings, byId);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        // 영상 정보를 못 받아도 평가는 보여 준다 (제목 대신 안내, 기본 썸네일)
                        Log.w(TAG, "video info failed", error);
                        showRatings(ratings, new HashMap<>());
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.w(TAG, "rating load failed", e);
                if (isFinishing()) return;
                binding.ratedLoading.setVisibility(View.GONE);
                Toast.makeText(this, R.string.rated_load_failed, Toast.LENGTH_SHORT).show();
            });
    }

    private void showRatings(List<Rating> ratings, Map<String, Video> videos) {
        Date now = new Date();
        List<RatedVideoItem> list = new ArrayList<>();
        for (Rating r : ratings) {
            Video video = videos.get(r.getVideoId());
            String title = video != null && !TextUtils.isEmpty(video.getTitle())
                ? video.getTitle() : getString(R.string.rated_unknown_video);
            String thumbnail = video != null && !TextUtils.isEmpty(video.getThumbnailUrl())
                ? video.getThumbnailUrl() : "https://i.ytimg.com/vi/" + r.getVideoId() + "/hqdefault.jpg";
            Date ratedAt = r.getCreatedAt() == null ? now : r.getCreatedAt().toDate();
            String when = TimeAgoFormat.format(this, ratedAt, now);
            int score = (int) r.getScore();
            String meta = isMine && Rating.VISIBILITY_PRIVATE.equals(r.getVisibility())
                ? getString(R.string.rated_meta_private, RatingFormat.stars(score), score, when)
                : getString(R.string.rated_meta, RatingFormat.stars(score), score, when);
            list.add(new RatedVideoItem(r.getVideoId(), r.getCategoryId(), title, thumbnail, meta, score, ratedAt));
        }
        showItems(list);
    }

    private void showItems(List<RatedVideoItem> list) {
        if (isFinishing()) return;
        binding.ratedLoading.setVisibility(View.GONE);
        items = list;
        binding.ratedTitle.setText(getString(R.string.rated_title, items.size()));
        binding.ratedEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        showSorted();
    }

    // 최신순은 받은 순서 그대로, 별점 높은순은 점수 → 최신 순으로 기기에서 정렬
    private void showSorted() {
        List<RatedVideoItem> sorted = new ArrayList<>(items);
        if (binding.ratedSortGroup.getCheckedChipId() == R.id.rated_sort_score) {
            sorted.sort((a, b) -> {
                int byScore = Long.compare(b.getScore(), a.getScore());
                return byScore != 0 ? byScore : b.getRatedAt().compareTo(a.getRatedAt());
            });
        }
        adapter.setItems(sorted);
    }
}
