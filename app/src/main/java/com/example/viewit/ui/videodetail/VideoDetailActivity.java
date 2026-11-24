package com.example.viewit.ui.videodetail;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.paging.PagingConfig;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.viewit.BuildConfig;
import com.example.viewit.MainActivity;
import com.example.viewit.R;
import com.example.viewit.data.RatingRepository;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.WatchHistoryRepository;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
import com.example.viewit.databinding.ActivityVideoDetailBinding;
import com.example.viewit.databinding.DialogRatingDeleteBinding;
import com.example.viewit.databinding.ItemVideoDetailHeaderBinding;
import com.example.viewit.databinding.ItemVideoDetailStatusBinding;
import com.example.viewit.ui.common.DurationFormat;
import com.example.viewit.ui.common.VideoPlayerView;
import com.example.viewit.ui.common.YouTubeErrorMessage;
import com.example.viewit.ui.common.YouTubeLinks;
import com.example.viewit.ui.watchlater.WatchLaterBottomSheetFragment;
import com.firebase.ui.firestore.paging.FirestorePagingOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;

// Figma 06 영상 상세: 공통 평가 패널을 표시하는 화면.
public class VideoDetailActivity extends AppCompatActivity {
    private static final String TAG = "VideoDetailActivity";
    public static final String EXTRA_VIDEO_ID = "videoId";
    public static final String EXTRA_CATEGORY_ID = "categoryId";
    public static final String EXTRA_RATING_CHANGED = "ratingChanged";
    private static final String STATE_RATING_CHANGED = "detail.ratingChanged";
    private static final String STATE_RATING_DRAFT = "detail.ratingDraft";
    private static final String STATE_CATEGORY_ID = "detail.categoryId";
    private boolean ratingChanged;
    private boolean myRatingLoaded;
    private Bundle restoredRatingDraft;
    private ActivityVideoDetailBinding binding;
    private ItemVideoDetailHeaderBinding headerBinding;
    private ItemVideoDetailStatusBinding statusBinding;
    private String videoId;
    private String categoryId;
    private Video loadedVideo;
    private boolean ratingWriteInProgress;
    // 통계와 첫 페이지는 별도 요청이다. 둘 다 끝나야 당기기 로딩을 종료한다.
    private boolean publicStatisticsLoading;
    private boolean publicListRefreshing;
    private AlertDialog ratingDeleteDialog;
    private VideoDetailAdapter videoDetailAdapter;
    // 나중에 보낸 요청보다 먼저 보낸 요청이 늦게 도착해 화면을 덮는 것을 막는다.
    private int publicStatisticsRequest;
    private boolean fullscreenMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: savedState=" + (savedInstanceState != null)
                + ", launchedForResult=" + (getCallingActivity() != null));
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
                //결과를 받으려고 이 화면을 연 원래 화면이 있으면, 현재 상세 화면을 닫는 코드.
                if (getCallingActivity() != null) {
                    Log.d(TAG, "상세 종료: finish, ratingChanged=" + ratingChanged);
                    // launcher로 연 경우 원래 화면의 결과 콜백으로 돌아간다.
                    finish();
                    return;
                }
                Intent intent = new Intent(VideoDetailActivity.this, MainActivity.class);
                // 기존 메인 위의 화면을 정리하고, 메인을 다시 열어 기본 피드 탭을 표시한다.
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            if (fullscreenMode) {
                view.setPadding(0, 0, 0, 0);
                return insets;
            }
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });

        // 평가 탭에서 전달한 영상이 있으면 우선 사용한다.
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        if (savedInstanceState != null) {
            categoryId = savedInstanceState.getString(STATE_CATEGORY_ID, categoryId);
            ratingChanged = savedInstanceState.getBoolean(STATE_RATING_CHANGED, false);
            restoredRatingDraft = savedInstanceState.getBundle(STATE_RATING_DRAFT);
            if (ratingChanged) {
                returnRatingChangeResult();
            }
        }
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
        headerBinding.videoOpenYoutube.setOnClickListener(v -> YouTubeLinks.open(this, videoId));
        // 앱 안 플레이어(4.2): 재생 시작 시 앱 시청 기록(5.1)을 users/{uid}/watchHistory/{videoId}에 기록한다.
        final WatchHistoryRepository watchHistoryRepository = new WatchHistoryRepository();
        binding.videoPlayer.setOnPlaybackStartedListener(new VideoPlayerView.OnPlaybackStartedListener() {
            @Override
            public void onPlaybackStarted(@NonNull String startedVideoId) {
                Log.d(TAG, "재생 시작 감지: 시청 기록 저장 요청, videoId=" + startedVideoId);
                watchHistoryRepository.recordWatch(startedVideoId);
            }
        });
        binding.videoPlayer.load(this, videoId);
        // 가로 상태로 진입했을 때도 즉시 전체화면 레이아웃을 적용한다.
        applyFullscreenForOrientation(getResources().getConfiguration().orientation);
        loadVideoInfo();
        RatingRepository repository = new RatingRepository();

        // 화면 진입 시 내 평가를 조회하도록 여기서 loadMyRating(); 호출.
        loadMyRating();
        // 화면의 Query/Adapter를 먼저 연결한 뒤 통계와 목록을 조회한다.
        setupPublicRatingsList();
        // 영상은 이 View 바깥에 있으므로 당겨도 고정되어 있다.
        binding.publicRatingSwipeRefresh.setOnChildScrollUpCallback(
                new SwipeRefreshLayout.OnChildScrollUpCallback() {
                    @Override
                    public boolean canChildScrollUp(@NonNull SwipeRefreshLayout parent, View child) {
                        return binding.publicRatingList.canScrollVertically(-1);
                    }
                });
        binding.publicRatingSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshPublicRatingsFromUser();
            }
        });
        refreshPublicRatings();
        statusBinding.publicRatingRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoDetailAdapter != null) {
                    videoDetailAdapter.retry();
                }
            }
        });
        // 평가 패널의 '보관함에 담기' 버튼도 피드와 같은 나중에 보기 바텀시트를 연다.
        headerBinding.ratingPanel.setOnWatchLaterClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    //토스트는 재사용되니까, 함수로 사용한다.
                    makeToast(R.string.reactions_login_required);
                    return;
                }
                String title = loadedVideo != null && loadedVideo.getTitle() != null
                        ? loadedVideo.getTitle()
                        : headerBinding.videoTitle.getText().toString();
                String meta = "";
                String thumbUrl = "";
                if (loadedVideo != null) {
                    String duration = DurationFormat.format(loadedVideo.getDurationSeconds());
                    String channel = loadedVideo.getChannelTitle();
                    meta = channel == null || channel.isEmpty()
                            ? duration
                            : getString(R.string.video_meta, channel, duration);
                    thumbUrl = loadedVideo.getThumbnailUrl() != null ? loadedVideo.getThumbnailUrl() : "";
                }
                Log.d(TAG, "영상 상세 나중에 보기 바텀시트 열기: videoId=" + videoId);
                WatchLaterBottomSheetFragment sheet = WatchLaterBottomSheetFragment.newInstance(
                        videoId, title, meta, thumbUrl, false);
                sheet.show(getSupportFragmentManager(), "WatchLaterBottomSheet");
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
                    Log.e(TAG, "평가 저장 중단: 영상 또는 카테고리 정보 없음");
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
                Log.d(TAG, "평가 저장 요청");
                repository.saveRating(rating)
                        .addOnSuccessListener(unused -> {
                            ratingWriteInProgress = false;
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Log.d(TAG, "평가 저장 성공");
                            ratingChanged = true;
                            returnRatingChangeResult();
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
                            Log.e(TAG, "Rating save failed", error);
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
        myRatingLoaded = false;
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
                            myRatingLoaded = true;
                            // 서버 조회가 회전 전 입력값을 덮지 않도록 마지막에 복원한다.
                            restoreRatingDraft();
                        } catch (RuntimeException error) {
                            Log.e(TAG, "Rating data conversion failed", error);
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
                        Log.e(TAG, "Rating load failed", error);
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

        Log.d(TAG, "평가 삭제 요청");
        new RatingRepository().deleteRating(videoId)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        ratingWriteInProgress = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        ratingChanged = true;
                        Log.d(TAG, "평가 삭제 성공");
                        returnRatingChangeResult();
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
                        Log.e(TAG, "Rating delete failed", error);

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
        videoDetailAdapter = new VideoDetailAdapter(publicRatingOptions());
        // ConcatAdapter는 순서만 연결하며, 평가의 조회·페이지 관리는 FirebaseUI가 담당한다.
        // 기본 viewType 분리로 정보용 Holder와 평가용 Holder가 섞이지 않는다.
        ConcatAdapter detailAdapter = new ConcatAdapter(
                new DetailSectionAdapter(headerBinding.getRoot()),
                videoDetailAdapter,
                new DetailSectionAdapter(statusBinding.getRoot()));
        binding.publicRatingList.setAdapter(detailAdapter);
        // FirebaseUI가 로딩·실패·빈 목록 상태를 전달한다. 실패 원인은 Logcat에도 남긴다.
        videoDetailAdapter.addLoadStateListener(states -> {
            renderPublicRatingsLoadState(states);
            return null;
        });
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
    // 목록 맨 위에서 당길 때 호출한다. 손가락을 여러 번 움직여도 중복 조회하지 않는다.
    private void refreshPublicRatingsFromUser() {
        if (ratingWriteInProgress || publicStatisticsLoading || publicListRefreshing) {
            updatePublicRefreshIndicator();
            return;
        }
        refreshPublicRatings();
    }

    // 다음 페이지 append 로딩은 새로고침과 별개이므로 위쪽 당기기 표시에는 포함하지 않는다.
    private void updatePublicRefreshIndicator() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        boolean refreshing = publicStatisticsLoading || publicListRefreshing;
        binding.publicRatingSwipeRefresh.setRefreshing(refreshing);
    }

    private void refreshPublicRatings() {
        if (videoId == null || ratingWriteInProgress) {
            return;
        }
        final int request = ++publicStatisticsRequest;
        publicStatisticsLoading = true;
        publicListRefreshing = videoDetailAdapter != null;
        updatePublicRefreshIndicator();
        headerBinding.publicRatingLoading.setVisibility(View.VISIBLE);
        headerBinding.publicRatingStatistics.setVisibility(View.GONE);
        headerBinding.publicRatingStatus.setText(R.string.video_detail_public_loading);
        headerBinding.publicRatingStatus.setVisibility(View.VISIBLE);

        if (videoDetailAdapter != null) {
            videoDetailAdapter.clearProfiles();
            videoDetailAdapter.refresh();
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
                        Log.d(TAG, "Public statistics loaded: count="
                                + statistics.getPublicRatingCount() + ", average=" + statistics.getRatingAverage());
                        publicStatisticsLoading = false;
                        updatePublicRefreshIndicator();
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
                        Log.e(TAG, "Public statistics load failed", error);
                        publicStatisticsLoading = false;
                        updatePublicRefreshIndicator();
                        headerBinding.publicRatingLoading.setVisibility(View.GONE);
                        headerBinding.publicRatingStatus.setText(R.string.video_detail_public_error);
                        headerBinding.publicRatingStatus.setVisibility(View.VISIBLE);
                        // 실패를 통계 0개로 바꾸지 않는다. 새로고침으로 다시 조회할 수 있다.
                    }
                });
    }

    // 목록 오류와 집계 오류는 독립적으로 표시한다. 통계 성공이 목록 성공을 의미하지는 않는다.
    private void renderPublicRatingsLoadState(CombinedLoadStates states) {
        if (videoDetailAdapter == null || isDestroyed()) {
            return;
        }
        LoadState refresh = states.getRefresh();
        LoadState append = states.getAppend();
        // 성공·빈 목록·오류는 모두 NotLoading/Error로 종료되어 로딩 표시가 남지 않는다.
        publicListRefreshing = refresh instanceof LoadState.Loading;
        updatePublicRefreshIndicator();
        boolean loading = refresh instanceof LoadState.Loading || append instanceof LoadState.Loading;
        statusBinding.publicRatingListLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusBinding.publicRatingRetry.setVisibility(
                refresh instanceof LoadState.Error || append instanceof LoadState.Error ? View.VISIBLE : View.GONE);
        if (refresh instanceof LoadState.Error || append instanceof LoadState.Error) {
            LoadState.Error failure = refresh instanceof LoadState.Error
                    ? (LoadState.Error) refresh : (LoadState.Error) append;
            Log.e(TAG, "Public rating list load failed", failure.getError());
            statusBinding.publicRatingListStatus.setText(R.string.video_detail_public_list_error);
            statusBinding.publicRatingListStatus.setVisibility(View.VISIBLE);
        } else if (refresh instanceof LoadState.NotLoading
                && videoDetailAdapter.getItemCount() == 0) {
            statusBinding.publicRatingListStatus.setText(R.string.video_detail_public_empty);
            statusBinding.publicRatingListStatus.setVisibility(View.VISIBLE);
        } else {
            if (refresh instanceof LoadState.NotLoading) {
                Log.d(TAG, "Public rating list loaded: rows=" + videoDetailAdapter.getItemCount());
            }
            statusBinding.publicRatingListStatus.setVisibility(View.GONE);
        }
    }

    // 영상 정보는 VideoRepository로 받는다: Firestore 저장본이 30일 안이면 그대로, 아니면 YouTube에서 받아 저장(4.1).
    // 결과는 메인 스레드로 오므로 따로 스레드를 만들지 않는다.
    private void loadVideoInfo() {
        new VideoRepository().getVideo(videoId, new VideoRepository.Callback<Video>() {
            @Override
            public void onSuccess(Video video) {
                if (isDestroyed()) {
                    return;
                }
                loadedVideo = video;
                categoryId = video.getCategoryId();
                headerBinding.videoTitle.setText(video.getTitle());
                headerBinding.videoChannel.setText(video.getChannelTitle());
                headerBinding.videoCategory.setText(getString(
                        R.string.video_detail_category_id, categoryId));
                binding.videoPlayer.setThumbnailUrl(video.getThumbnailUrl());
            }

            @Override
            public void onError(@NonNull Exception error) {
                // 오류 안내에는 API 키가 들어 있지 않다. 이유별 문구(키 없음·사용량·인터넷 등)를 보여 준다.
                if (!isDestroyed()) {
                    Log.e(TAG, "영상 정보 조회 실패: " + error.getClass().getSimpleName());
                    headerBinding.videoTitle.setText(
                            YouTubeErrorMessage.of(VideoDetailActivity.this, error));
                }
            }
        });
    }

    // setResult()는 결과를 준비하고, 종료될 때 원래 화면에 전달한다.
    private void returnRatingChangeResult() {
        Intent result = new Intent();
        result.putExtra(EXTRA_VIDEO_ID, videoId);
        result.putExtra(EXTRA_RATING_CHANGED, true);
        setResult(RESULT_OK, result);
        Log.d(TAG, "결과 준비: setResult(RESULT_OK), ratingChanged=true");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_RATING_CHANGED, ratingChanged);
        outState.putString(STATE_CATEGORY_ID, categoryId);
        if (restoredRatingDraft != null) {
            outState.putBundle(STATE_RATING_DRAFT, restoredRatingDraft);
        } else if (myRatingLoaded && !ratingWriteInProgress) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                Bundle draft = new Bundle();
                draft.putString("userId", user.getUid());
                draft.putInt("score", headerBinding.ratingPanel.getScore());
                draft.putString("comment", headerBinding.ratingPanel.getComment());
                draft.putString("visibility", headerBinding.ratingPanel.getRatingVisibility());
                outState.putBundle(STATE_RATING_DRAFT, draft);
            }
        }
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState: 평가 입력 상태 저장");
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 조회 중이면 조회 성공 콜백에서 복원한다.
        restoreRatingDraft();
        Log.d(TAG, "onRestoreInstanceState: 복원 요청, ratingLoaded=" + myRatingLoaded);
    }

    private void restoreRatingDraft() {
        if (!myRatingLoaded || restoredRatingDraft == null) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getUid().equals(restoredRatingDraft.getString("userId"))) {
            headerBinding.ratingPanel.setScore(restoredRatingDraft.getInt("score", 0));
            headerBinding.ratingPanel.setComment(restoredRatingDraft.getString("comment", ""));
            headerBinding.ratingPanel.setRatingVisibility(
                    restoredRatingDraft.getString("visibility", Rating.VISIBILITY_PUBLIC));
            Log.d(TAG, "평가 입력 복원 완료");
        }
        restoredRatingDraft = null;
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: changingConfigurations=" + isChangingConfigurations()
                + ", finishing=" + isFinishing());
        // Activity가 종료될 때 열려 있는 창도 닫아 Window 누수를 방지한다.
        if (ratingDeleteDialog != null) {
            ratingDeleteDialog.dismiss();
            ratingDeleteDialog = null;
        }
        // FirebaseUI는 LifecycleOwner를 따라 중지한다. 화면과 Adapter의 연결도 해제한다.
        if (binding != null) {
            binding.publicRatingList.setAdapter(null);
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 가로 회전 시 영상 전체화면 모드로 전환하고, 세로로 돌리면 앱바와 평가 목록을 다시 보여 준다.
        applyFullscreenForOrientation(newConfig.orientation);
        Log.d(TAG, "onConfigurationChanged: orientation=" + newConfig.orientation
                + ", fullscreenMode=" + fullscreenMode);
    }

    // 가로 모드에서는 상단 앱바·목록·시스템 바를 숨기고 영상을 화면에 꽉 채운다.
    private void applyFullscreenForOrientation(int orientation) {
        if (binding == null) {
            return;
        }
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        fullscreenMode = landscape;

        int chromeVisibility = landscape ? View.GONE : View.VISIBLE;
        binding.videoAppBar.setVisibility(chromeVisibility);
        binding.videoDivider.setVisibility(chromeVisibility);
        binding.publicRatingSwipeRefresh.setVisibility(chromeVisibility);

        ViewGroup.LayoutParams playerParams = binding.videoPlayer.getLayoutParams();
        playerParams.height = landscape
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.videoPlayer.setLayoutParams(playerParams);
        binding.videoPlayer.setFullscreen(landscape);

        binding.getRoot().setBackgroundColor(ContextCompat.getColor(
                this, landscape ? R.color.accent_deep : R.color.bg_surface));

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (landscape) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void makeToast(@StringRes int messageResId) {
        Toast.makeText(this, messageResId, LENGTH_SHORT).show();
    }
}
