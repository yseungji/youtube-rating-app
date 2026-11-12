package com.example.viewit;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.Intent;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.viewit.databinding.DialogRatingDeleteBinding;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.data.RatingRepository;
import com.example.viewit.data.VideoInfoLoader;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
import com.example.viewit.data.model.User;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.example.viewit.databinding.ItemVideoDetailHeaderBinding;
import com.example.viewit.databinding.ItemVideoDetailStatusBinding;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.android.gms.tasks.Task;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.paging.PagingConfig;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.databinding.ItemReviewDetailBinding;
import com.firebase.ui.firestore.paging.FirestorePagingAdapter;
import com.firebase.ui.firestore.paging.FirestorePagingOptions;
import com.google.firebase.firestore.FieldPath;
import com.example.viewit.databinding.ActivityVideoDetailBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Figma 06 영상 상세: 공통 평가 패널을 표시하는 화면.
public class VideoDetailActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_ID = "videoId";
    public static final String EXTRA_CATEGORY_ID = "categoryId";
    private final ExecutorService videoExecutor = Executors.newSingleThreadExecutor();
    private ActivityVideoDetailBinding binding;
    private ItemVideoDetailHeaderBinding headerBinding;
    private ItemVideoDetailStatusBinding statusBinding;
    private String videoId;
    private String categoryId;
    private boolean ratingWriteInProgress;
    private AlertDialog ratingDeleteDialog;
    private MyAdapter publicRatingsAdapter;
    // 나중에 보낸 요청보다 먼저 보낸 요청이 늦게 도착해 화면을 덮는 것을 막는다.
    private int publicStatisticsRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityVideoDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // 패널은 한 번 생성한다. 행이 화면 밖으로 나가도 입력과 기존 리스너를 유지한다.
        // 실제 부모는 DetailSectionViewHolder의 FrameLayout이다.
        // 아직 LayoutManager가 없는 RecyclerView를 inflate의 부모로 전달하지 않는다.
        headerBinding = ItemVideoDetailHeaderBinding.inflate(getLayoutInflater());
        statusBinding = ItemVideoDetailStatusBinding.inflate(getLayoutInflater());

        binding.videoBack.getRoot().setContentDescription(
                getString(R.string.video_detail_back_to_feed));
        binding.videoBack.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VideoDetailActivity.this, MainActivity.class);
                // 기존 메인 위의 화면을 정리하고, 메인을 다시 열어 기본 피드 탭을 표시한다.
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });

        // 평가 탭에서 전달한 영상이 있으면 우선 사용한다.
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        // 디버그 화면을 직접 실행할 때만 '봤다고 가정한' 테스트 영상을 선택한다.
        // 실제 시청 기록이나 평가는 여기서 저장하지 않는다.
        if ((videoId == null || videoId.trim().isEmpty()) && BuildConfig.DEBUG) {
            videoId = "cUgYiPrtAA0";
            categoryId = null;
        }
        if (videoId == null || videoId.trim().isEmpty() || videoId.contains("/")) {
            makeToast(R.string.video_detail_missing_ids);
            finish();
            return;
        }
        videoId = videoId.trim();
        headerBinding.videoId.setText(getString(R.string.video_detail_video_id, videoId));
        headerBinding.videoOpenYoutube.setOnClickListener(v -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + videoId))));
        loadVideoInfo();
        RatingRepository repository = new RatingRepository();

        // 화면 진입 시 내 평가를 조회하도록 여기서 loadMyRating(); 호출.
        loadMyRating();
        // 화면의 Query/Adapter를 먼저 연결한 뒤 통계와 목록을 조회한다.
        setupPublicRatingsList();
        refreshPublicRatings();
        statusBinding.publicRatingRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (publicRatingsAdapter != null) {
                    publicRatingsAdapter.retry();
                }
            }
        });
        headerBinding.publicRatingRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshPublicRatings();
            }
        });

        // 패널 전체가 아니라 패널 안의 '평가 저장' 버튼에 리스너를 연결한다.
        headerBinding.ratingPanel.setOnSaveClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ratingWriteInProgress) {
                    return;
                }
                if (videoId == null || videoId.trim().isEmpty() || videoId.contains("/")
                        || categoryId == null || categoryId.trim().isEmpty()) {
                    makeToast(R.string.video_detail_missing_ids);
                    return;
                }

                // 현재 화면의 입력값으로 저장할 평가 객체를 만든다.
                Rating rating = new Rating();
                rating.setVideoId(videoId.trim());
                rating.setCategoryId(categoryId.trim());
                rating.setScore(headerBinding.ratingPanel.getScore());
                rating.setComment(headerBinding.ratingPanel.getComment());
                rating.setVisibility(headerBinding.ratingPanel.getRatingVisibility());

                // 작성자 UID는 Repository가 로그인 정보에서 채운다.
                // 같은 사용자·영상의 평가는 Repository가 기존 문서를 수정한다.
                ratingWriteInProgress = true;
                repository.saveRating(rating)
                        .addOnSuccessListener(unused -> {
                            ratingWriteInProgress = false;
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            // 서버 저장·수정 성공 후에만 공개 목록과 통계를 다시 읽는다.
                            refreshPublicRatings();
                            headerBinding.ratingPanel.setSaved(true);
                            headerBinding.myRatingDeleteArea.setVisibility(View.VISIBLE);

                            Toast.makeText(VideoDetailActivity.this,
                                    R.string.video_detail_save_success, LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(error -> {
                            ratingWriteInProgress = false;
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Log.e("VideoDetailActivity", "Rating save failed", error);
                            Toast.makeText(VideoDetailActivity.this,
                                    R.string.video_detail_save_error, Toast.LENGTH_LONG).show();
                        });
            }
        });
    }

    // 로그인한 사용자의 평가를 조회한다. 이 메서드는 문서를 저장하거나 수정하지 않는다.
    private void loadMyRating() {
        if (ratingWriteInProgress) {
            return;
        }
        setupDeleteActions();
        headerBinding.myRatingDeleteArea.setVisibility(View.GONE);

        headerBinding.ratingPanel.setVisibility(View.GONE);
        headerBinding.myRatingStatus.setVisibility(View.VISIBLE);
        headerBinding.myRatingRetry.setVisibility(View.GONE);
        headerBinding.myRatingRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMyRating();
            }
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        //유저가 없다면
        if (user == null) {
            headerBinding.myRatingStatus.setText(R.string.video_detail_rating_login_required);
            headerBinding.myRatingRetry.setVisibility(View.VISIBLE);
            return;
        }

        headerBinding.myRatingStatus.setText(R.string.video_detail_rating_loading);
        final String uid = user.getUid();
        RatingRepository repository = new RatingRepository();
        repository.getRating(uid, videoId)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser == null || !uid.equals(currentUser.getUid())) {
                            showMyRatingLoadError();
                            return;
                        }

                        try {
                            if (snapshot.exists()) {
                                // 평가 있음: 서버의 데이터를 입력 패널에 채운다.
                                Rating rating = snapshot.toObject(Rating.class);
                                if (rating == null || !Rating.isValidScore(rating.getScore())) {
                                    showMyRatingLoadError();
                                    return;
                                }
                                headerBinding.ratingPanel.setScore((int) rating.getScore());
                                headerBinding.ratingPanel.setComment(rating.getComment());
                                headerBinding.ratingPanel.setRatingVisibility(rating.getVisibility());
                                headerBinding.ratingPanel.setSaved(true);
                                headerBinding.myRatingDeleteArea.setVisibility(View.VISIBLE);

                                // 영상 정보 조회에 실패해도 저장된 평가의 카테고리를 사용할 수 있다.
                                if ((categoryId == null || categoryId.trim().isEmpty())
                                        && rating.getCategoryId() != null) {
                                    categoryId = rating.getCategoryId().trim();
                                    headerBinding.videoCategory.setText(getString(
                                            R.string.video_detail_category_id, categoryId));
                                }
                            } else {
                                // 평가 없음: 새로운 평가를 입력하는 상태로 만든다.
                                headerBinding.ratingPanel.setScore(0);
                                headerBinding.ratingPanel.setComment("");
                                headerBinding.ratingPanel.setRatingVisibility(Rating.VISIBILITY_PUBLIC);
                                headerBinding.ratingPanel.setSaved(false);
                            }

                            headerBinding.myRatingStatus.setVisibility(View.GONE);
                            headerBinding.myRatingRetry.setVisibility(View.GONE);
                            headerBinding.ratingPanel.setVisibility(View.VISIBLE);
                        } catch (RuntimeException error) {
                            Log.e("VideoDetailActivity", "Rating data conversion failed", error);
                            showMyRatingLoadError();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        Log.e("VideoDetailActivity", "Rating load failed", error);
                        // 실패를 '평가 없음'으로 처리하지 않는다.
                        showMyRatingLoadError();
                    }
                });
    }

    private void showMyRatingLoadError() {
        headerBinding.myRatingDeleteArea.setVisibility(View.GONE);
        headerBinding.ratingPanel.setVisibility(View.GONE);
        headerBinding.myRatingStatus.setText(R.string.video_detail_rating_load_error);
        headerBinding.myRatingStatus.setVisibility(View.VISIBLE);
        headerBinding.myRatingRetry.setVisibility(View.VISIBLE);
    }

    private void setupDeleteActions() {
        headerBinding.myRatingDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteConfirmationDialog();
            }
        });
    }

    /**
     * Figma 06-D: 화면 가운데 뜨는 삭제 확인창.
     * MaterialAlertDialogBuilder가 별도의 창과 어두운 배경을 만들고,
     * ViewBinding으로 만든 XML이 제목·설명·버튼의 모양을 담당한다.
     * 이 메서드 자체는 서버 데이터를 지우지 않는다.
     */
    private void showDeleteConfirmationDialog() {
        if (ratingWriteInProgress || isFinishing() || isDestroyed()
                || (ratingDeleteDialog != null && ratingDeleteDialog.isShowing())) {
            return;
        }
        DialogRatingDeleteBinding dialogBinding =
                DialogRatingDeleteBinding.inflate(getLayoutInflater());
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_rating_delete_dialog))
                // Builder 기본 여백 대신 Figma의 바깥 여백과 XML 내부 24dp 여백을 적용한다.
                .setBackgroundInsetStart(0)
                .setBackgroundInsetEnd(0)
                .setBackgroundInsetTop(0)
                .setBackgroundInsetBottom(0)
                .setCancelable(true)
                .create();
        ratingDeleteDialog = dialog;
        dialog.setCanceledOnTouchOutside(true);
        dialogBinding.ratingDeleteCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 취소·바깥 터치·시스템 뒤로가기는 창만 닫는다.
                dialog.dismiss();
            }
        });
        dialogBinding.ratingDeleteConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ratingWriteInProgress) {
                    return;
                }
                dialog.dismiss();
                // 삭제 확인 버튼에서만 기존 Repository 삭제 로직으로 들어간다.
                // 성공/실패 처리와 성공 후 통계·목록 새로고침은 그대로 재사용한다.
                deleteMyRating();
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignored) {
                if (ratingDeleteDialog == dialog) {
                    ratingDeleteDialog = null;
                }
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
            int margin = getResources().getDimensionPixelSize(R.dimen.space_xl);
            int availableWidth = binding.getRoot().getWidth() - margin * 2;
            int maxWidth = getResources().getDimensionPixelSize(
                    R.dimen.video_detail_delete_dialog_max_width);
            window.setLayout(Math.min(maxWidth, Math.max(1, availableWidth)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.32f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setTitle(getString(R.string.video_detail_delete_dialog_title));
        }
    }

    private void deleteMyRating() {
        if (ratingWriteInProgress) {
            return;
        }
        ratingWriteInProgress = true;
        headerBinding.ratingPanel.setVisibility(View.GONE);
        headerBinding.myRatingDeleteArea.setVisibility(View.GONE);
        headerBinding.myRatingRetry.setVisibility(View.GONE);
        headerBinding.myRatingStatus.setText(R.string.video_detail_delete_loading);
        headerBinding.myRatingStatus.setVisibility(View.VISIBLE);

        new RatingRepository().deleteRating(videoId)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        ratingWriteInProgress = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        headerBinding.ratingPanel.setScore(0);
                        headerBinding.ratingPanel.setComment("");
                        headerBinding.ratingPanel.setRatingVisibility(Rating.VISIBILITY_PUBLIC);
                        headerBinding.ratingPanel.setSaved(false);

                        headerBinding.myRatingStatus.setVisibility(View.GONE);
                        headerBinding.ratingPanel.setVisibility(View.VISIBLE);
                        // 삭제가 서버에 반영된 뒤에만 목록·통계를 새로 읽는다.
                        refreshPublicRatings();
                        makeToast(R.string.video_detail_delete_success);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        ratingWriteInProgress = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        // 실패하면 입력값은 그대로 두고 다시 시도할 수 있게 한다.
                        Log.e("VideoDetailActivity", "Rating delete failed", error);

                        headerBinding.myRatingStatus.setVisibility(View.GONE);
                        headerBinding.ratingPanel.setVisibility(View.VISIBLE);
                        headerBinding.myRatingDeleteArea.setVisibility(View.VISIBLE);
                        makeToast(R.string.video_detail_delete_error);
                    }
                });
    }

    //화면 설정 → ViewHolder → Adapter.
    private void setupPublicRatingsList() {
        binding.publicRatingList.setLayoutManager(new LinearLayoutManager(this));
        publicRatingsAdapter = new MyAdapter(publicRatingOptions());
        // ConcatAdapter는 순서만 연결하며, 평가의 조회·페이지 관리는 FirebaseUI가 담당한다.
        // 기본 viewType 분리로 정보용 Holder와 평가용 Holder가 섞이지 않는다.
        ConcatAdapter detailAdapter = new ConcatAdapter(
                new DetailSectionAdapter(headerBinding.getRoot()),
                publicRatingsAdapter,
                new DetailSectionAdapter(statusBinding.getRoot()));
        binding.publicRatingList.setAdapter(detailAdapter);
        // FirebaseUI가 로딩·실패·빈 목록 상태를 전달한다. 실패 원인은 Logcat에도 남긴다.
        publicRatingsAdapter.addLoadStateListener(states -> {
            renderPublicRatingsLoadState(states);
            return null;
        });
    }

    // 정보·입력 패널과 상태 안내는 각각 한 번만 나타나는 화면이다.
    // 입력 패널 자체를 유지하고 RecyclerView의 FrameLayout 틀에 연결하므로,
    // 위로 돌아왔을 때 별점·한줄평과 기존 리스너가 초기화되지 않는다.
    //viewHolder
    private static class DetailSectionViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout container;

        DetailSectionViewHolder(FrameLayout container) {
            super(container);
            this.container = container;
        }

        void bind(View section) {
            if (section.getParent() == container) {
                return;
            }
            if (section.getParent() instanceof ViewGroup) {
                ((ViewGroup) section.getParent()).removeView(section);
            }
            container.removeAllViews();
            container.addView(section, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    //Adapter
    private static class DetailSectionAdapter extends RecyclerView.Adapter<DetailSectionViewHolder> {
        private final View section;

        DetailSectionAdapter(View section) {
            this.section = section;
        }

        @NonNull
        @Override
        public DetailSectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new DetailSectionViewHolder(container);
        }

        @Override
        public void onBindViewHolder(@NonNull DetailSectionViewHolder holder, int position) {
            holder.bind(section);
        }

        @Override
        public int getItemCount() {
            // Firebase 평가 개수와 별개로 정보/상태 화면은 각각 한 항목이다.
            return 1;
        }

        @Override
        public void onViewRecycled(@NonNull DetailSectionViewHolder holder) {
            holder.container.removeAllViews();
            super.onViewRecycled(holder);
        }
    }

    // static이므로 바깥 Activity 인스턴스를 보관하지 않는다.
    // 전체 평가 화면에서도 같은 ViewHolder/Adapter를 재사용해 행 표시를 통일한다.
    public static class MyViewHolder extends RecyclerView.ViewHolder {
        private final ItemReviewDetailBinding itemBinding;
        private String boundUid;
        private String timeText;
        private int bindingGeneration;

        public MyViewHolder(@NonNull ItemReviewDetailBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;
        }

        public void bind(Rating rating) {
            bindingGeneration++;
            boundUid = rating.getUserId();
            android.content.Context context = itemBinding.getRoot().getContext();
            timeText = rating.getCreatedAt() == null
                    ? context.getString(R.string.video_detail_review_unknown_time)
                    : TimeAgoFormat.format(context, rating.getCreatedAt().toDate(), new Date());
            itemBinding.reviewRating.setText(context.getString(
                    R.string.video_detail_review_score, RatingFormat.stars(rating.getScore()), rating.getScore()));
            String comment = rating.getComment();
            itemBinding.reviewComment.setText(comment == null || comment.trim().isEmpty()
                    ? context.getString(R.string.video_detail_review_no_comment) : comment);
            itemBinding.reviewActions.reviewActionLike.setText(context.getString(
                    R.string.review_like_count, Math.max(0, rating.getLikeCount())));
            itemBinding.reviewActions.reviewActionComment.setText(context.getString(
                    R.string.video_detail_review_comment_count, Math.max(0, rating.getCommentCount())));
            // 반응 버튼의 모양과 저장된 개수만 표시한다. 실제 동작은 5.4에서 연결한다.
            showProfile(null);
        }

        private void showProfile(User profile) {
            android.content.Context context = itemBinding.getRoot().getContext();
            String name = boundUid;
            // 공개 프로필이 아직 없는 본인 계정은 실제 인증 정보의 표시 이름을 사용할 수 있다.
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current != null && current.getUid().equals(boundUid)
                    && current.getDisplayName() != null) {
                name = current.getDisplayName();
            }
            if (profile != null && profile.getDisplayName() != null
                    && !profile.getDisplayName().trim().isEmpty()) {
                name = profile.getDisplayName().trim();
            }
            if (name == null || name.trim().isEmpty()) {
                name = context.getString(R.string.video_detail_review_unknown_author);
            }
            itemBinding.reviewAuthor.authorName.setText(name);
            // Figma 공통 Author는 사진 대신 실제 이름의 첫 글자 아바타를 사용한다.
            itemBinding.reviewAuthor.authorAvatar.setText(name.substring(0, name.offsetByCodePoints(0, 1)));
            String username = profile == null ? null : profile.getUsername();
            itemBinding.reviewAuthor.authorMeta.setText(username == null || username.trim().isEmpty()
                    ? timeText : context.getString(R.string.author_meta, username, timeText));
        }
    }

    public static class MyAdapter extends FirestorePagingAdapter<Rating, MyViewHolder> {
        // 같은 작성자의 프로필을 행마다 중복 요청하지 않는다. 새로고침하면 캐시도 비운다.
        private final Map<String, Task<DocumentSnapshot>> profiles = new HashMap<>();

        public MyAdapter(@NonNull FirestorePagingOptions<Rating> options) {
            super(options);
        }

        public void clearProfiles() {
            profiles.clear();
        }

        @Override
        protected void onBindViewHolder(@NonNull MyViewHolder holder, int position, @NonNull Rating model) {
            holder.bind(model);
            final String uid = model.getUserId();
            if (uid == null || uid.isEmpty() || uid.contains("/")) {
                return;
            }
            final int generation = holder.bindingGeneration;
            Task<DocumentSnapshot> profile = profiles.get(uid);
            if (profile == null) {
                profile = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS)
                        .document(uid).get(Source.SERVER);
                profiles.put(uid, profile);
            }
            profile.addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot snapshot) {
                    // RecyclerView가 이 Holder를 다른 행에 재사용했다면 오래된 응답은 무시한다.
                    if (generation != holder.bindingGeneration || !uid.equals(holder.boundUid)) {
                        return;
                    }
                    try {
                        holder.showProfile(snapshot.exists() ? snapshot.toObject(User.class) : null);
                    } catch (RuntimeException error) {
                        Log.w("VideoDetailActivity", "Author profile conversion failed", error);
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception error) {
                    // 프로필 오류 때문에 평가 행 전체를 숨기지 않는다. UID/인증 이름은 이미 표시돼 있다.
                    Log.w("VideoDetailActivity", "Author profile load failed", error);
                }
            });
        }

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemReviewDetailBinding itemBinding = ItemReviewDetailBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new MyViewHolder(itemBinding);
        }

        @Override
        public void onViewRecycled(@NonNull MyViewHolder holder) {
            holder.bindingGeneration++;
            holder.boundUid = null;
            super.onViewRecycled(holder);
        }
    }

    // FirebaseUI가 사용할 조회 설정만 준비한다. 생명주기에 맞춘 시작·정지는 FirebaseUI가 관리한다.
    // setLifecycleOwner(this)는 Activity 생명주기와 연결하며 실시간 데이터 구독을 뜻하지 않는다.
    private FirestorePagingOptions<Rating> publicRatingOptions() {
        return new FirestorePagingOptions.Builder<Rating>()
                .setLifecycleOwner(this)
                .setQuery(new RatingRepository().publicVideoRatingsQuery(videoId)
                                .orderBy("score").orderBy(FieldPath.documentId()),
                        new PagingConfig(10, 5, false), Rating.class)
                .build();
    }

    /**
     * 통계와 목록을 다시 읽는 공통 진입점이다.
     * 화면 진입/새로고침/저장·수정·삭제 성공 리스너에서 호출한다.
     * 내 평가 입력 패널을 다시 읽지 않으므로 작성 중인 별점과 한줄평을 덮어쓰지 않는다.
     */
    private void refreshPublicRatings() {
        if (videoId == null || ratingWriteInProgress) {
            return;
        }
        final int request = ++publicStatisticsRequest;
        headerBinding.publicRatingLoading.setVisibility(View.VISIBLE);
        headerBinding.publicRatingStatistics.setVisibility(View.GONE);
        headerBinding.publicRatingStatus.setText(R.string.video_detail_public_loading);
        headerBinding.publicRatingStatus.setVisibility(View.VISIBLE);

        if (publicRatingsAdapter != null) {
            publicRatingsAdapter.clearProfiles();
            publicRatingsAdapter.refresh();
        }
        // 목록과 별도로 전체 공개 평가 통계를 조회한다.
        new RatingRepository().loadPublicVideoStatistics(videoId,
                // 통계 조회 성공: 목록 페이지가 아닌 전체 공개 평가를 기준으로 표시한다.
                new OnSuccessListener<Video>() {
                    @Override
                    public void onSuccess(Video statistics) {
                        if (isFinishing() || isDestroyed() || request != publicStatisticsRequest) {
                            return;
                        }
                        Log.d("VideoDetailActivity", "Public statistics loaded: count="
                                + statistics.getPublicRatingCount() + ", average=" + statistics.getRatingAverage());
                        headerBinding.publicRatingLoading.setVisibility(View.GONE);
                        headerBinding.publicRatingCount.setText(getString(
                                R.string.video_detail_public_count, statistics.getPublicRatingCount()));
                        headerBinding.publicRatingStatistics.setStats(statistics);
                        headerBinding.publicRatingStatistics.setVisibility(View.VISIBLE);
                        if (statistics.getPublicRatingCount() == 0) {
                            headerBinding.publicRatingStatus.setText(R.string.video_detail_public_empty);
                        } else {
                            headerBinding.publicRatingStatus.setVisibility(View.GONE);
                        }
                    }
                },
                // 통계 조회 실패 시
                new OnFailureListener() {
                    @Override
                    public void onFailure(Exception error) {
                        if (isFinishing() || isDestroyed() || request != publicStatisticsRequest) {
                            return;
                        }
                        Log.e("VideoDetailActivity", "Public statistics load failed", error);
                        headerBinding.publicRatingLoading.setVisibility(View.GONE);
                        headerBinding.publicRatingStatus.setText(R.string.video_detail_public_error);
                        headerBinding.publicRatingStatus.setVisibility(View.VISIBLE);
                        // 실패를 통계 0개로 바꾸지 않는다. 새로고침으로 다시 조회할 수 있다.
                    }
                });
    }

    // 목록 오류와 집계 오류는 독립적으로 표시한다. 통계 성공이 목록 성공을 의미하지는 않는다.
    private void renderPublicRatingsLoadState(CombinedLoadStates states) {
        if (publicRatingsAdapter == null || isDestroyed()) {
            return;
        }
        LoadState refresh = states.getRefresh();
        LoadState append = states.getAppend();
        boolean loading = refresh instanceof LoadState.Loading || append instanceof LoadState.Loading;
        statusBinding.publicRatingListLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusBinding.publicRatingRetry.setVisibility(
                refresh instanceof LoadState.Error || append instanceof LoadState.Error ? View.VISIBLE : View.GONE);
        if (refresh instanceof LoadState.Error || append instanceof LoadState.Error) {
            LoadState.Error failure = refresh instanceof LoadState.Error
                    ? (LoadState.Error) refresh : (LoadState.Error) append;
            Log.e("VideoDetailActivity", "Public rating list load failed", failure.getError());
            statusBinding.publicRatingListStatus.setText(R.string.video_detail_public_list_error);
            statusBinding.publicRatingListStatus.setVisibility(View.VISIBLE);
        } else if (refresh instanceof LoadState.NotLoading
                && publicRatingsAdapter.getItemCount() == 0) {
            statusBinding.publicRatingListStatus.setText(R.string.video_detail_public_empty);
            statusBinding.publicRatingListStatus.setVisibility(View.VISIBLE);
        } else {
            if (refresh instanceof LoadState.NotLoading) {
                Log.d("VideoDetailActivity", "Public rating list loaded: rows=" + publicRatingsAdapter.getItemCount());
            }
            statusBinding.publicRatingListStatus.setVisibility(View.GONE);
        }
    }

    private void loadVideoInfo() {
        if (BuildConfig.YOUTUBE_API_KEY.trim().isEmpty()) {
            headerBinding.videoTitle.setText(R.string.video_detail_api_key_missing);
            return;
        }
        final String requestedVideoId = videoId;
        videoExecutor.execute(() -> {
            try {
                VideoInfoLoader.Result result = new VideoInfoLoader().load(requestedVideoId);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    categoryId = result.video.getCategoryId();
                    headerBinding.videoTitle.setText(result.video.getTitle());
                    headerBinding.videoChannel.setText(result.video.getChannelTitle());
                    headerBinding.videoCategory.setText(getString(
                            R.string.video_detail_category_id, categoryId));
                    if (result.thumbnail != null) {
                        binding.videoThumbnail.setImageBitmap(result.thumbnail);
                    }
                });
            } catch (Exception error) {
                // 요청 URL에 API 키가 들어 있으므로 예외 원문은 로그에 남기지 않는다.
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        headerBinding.videoTitle.setText(R.string.video_detail_load_error);
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        // Activity가 종료될 때 열려 있는 창도 닫아 Window 누수를 방지한다.
        if (ratingDeleteDialog != null) {
            ratingDeleteDialog.dismiss();
            ratingDeleteDialog = null;
        }
        videoExecutor.shutdownNow();
        // FirebaseUI는 LifecycleOwner를 따라 중지한다. 화면과 Adapter의 연결도 해제한다.
        if (binding != null) {
            binding.publicRatingList.setAdapter(null);
        }
        super.onDestroy();
    }

    private void makeToast(@StringRes int messageResId) {
        Toast.makeText(this, messageResId, LENGTH_SHORT).show();
    }
}
