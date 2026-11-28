package com.example.viewit.ui.comment;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.data.model.Comment;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.FragmentCommentBottomSheetBinding;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.TimeAgoFormat;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 리뷰 댓글·답글 바텀시트 (5.4).
// 맨 위에 원본 리뷰(작성자·별점·한줄평)를 보여 주고, 아래에 댓글과 1단 답글(댓글의 댓글) 목록을 표시한다.
public class CommentBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String TAG = "CommentBottomSheet";
    public static final String REQUEST_KEY = "comment.result";
    public static final String RESULT_RATING_ID = "ratingId";
    public static final String RESULT_COMMENT_ADDED = "commentAdded";

    private static final String ARG_RATING_ID = "ratingId";
    private static final String ARG_AUTHOR_NAME = "authorName";
    private static final String ARG_AUTHOR_META = "authorMeta";
    private static final String ARG_REVIEW_SCORE = "reviewScore";
    private static final String ARG_REVIEW_COMMENT = "reviewComment";

    private static final String STATE_TARGET_PARENT_ID = "comment.targetParentId";
    private static final String STATE_TARGET_AUTHOR_NAME = "comment.targetAuthorName";
    private static final String STATE_TARGET_USERNAME = "comment.targetUsername";

    private FragmentCommentBottomSheetBinding binding;
    private String ratingId;
    private String initialAuthorName;
    private String initialAuthorMeta;
    private long initialReviewScore;
    private String initialReviewComment;

    private CommentAdapter adapter;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Set<String> commentLikeInFlight = new HashSet<>();

    // 답글 모드 관리 변수
    private String targetParentId = null;        // 최상위 댓글 ID (null이면 일반 댓글)
    private String targetAuthorName = null;      // 답글 대상자 표시 이름
    private String targetUsername = null;        // 답글 대상자 아이디 (@아이디 언급용)

    // 키보드·시스템 바 인셋 중복 차감을 막기 위해 마지막 인셋과 부모 레이아웃 변경을 추적한다.
    private int basePaddingLeft;
    private int basePaddingTop;
    private int basePaddingRight;
    private int basePaddingBottom;
    private int lastBarsTop;
    private int lastRawBottomInset;
    private boolean lastImeVisible;
    private int lastObservedSheetPaddingBottom = -1;
    @Nullable
    private View observedSheetParent;
    @Nullable
    private View.OnLayoutChangeListener sheetParentLayoutListener;
    @Nullable
    private View observedBottomSheet;
    @Nullable
    private View.OnLayoutChangeListener bottomSheetLayoutListener;

    public static CommentBottomSheetFragment newInstance(String ratingId) {
        CommentBottomSheetFragment fragment = new CommentBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RATING_ID, ratingId);
        fragment.setArguments(args);
        return fragment;
    }

    // 영상 상세·피드에서 이미 화면에 표시 중인 원본 리뷰 정보를 바로 넘겨 깜빡임 없이 상단에 표시한다.
    public static CommentBottomSheetFragment newInstance(@NonNull String ratingId,
                                                         @Nullable String authorName,
                                                         @Nullable String authorMeta,
                                                         long score,
                                                         @Nullable String comment) {
        CommentBottomSheetFragment fragment = new CommentBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RATING_ID, ratingId);
        args.putString(ARG_AUTHOR_NAME, authorName);
        args.putString(ARG_AUTHOR_META, authorMeta);
        args.putLong(ARG_REVIEW_SCORE, score);
        args.putString(ARG_REVIEW_COMMENT, comment);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ratingId = getArguments().getString(ARG_RATING_ID);
            initialAuthorName = getArguments().getString(ARG_AUTHOR_NAME);
            initialAuthorMeta = getArguments().getString(ARG_AUTHOR_META);
            initialReviewScore = getArguments().getLong(ARG_REVIEW_SCORE, 0L);
            initialReviewComment = getArguments().getString(ARG_REVIEW_COMMENT);
        }
        if (savedInstanceState != null) {
            targetParentId = savedInstanceState.getString(STATE_TARGET_PARENT_ID);
            targetAuthorName = savedInstanceState.getString(STATE_TARGET_AUTHOR_NAME);
            targetUsername = savedInstanceState.getString(STATE_TARGET_USERNAME);
        }
    }

    @NonNull
    @Override
    @SuppressWarnings("deprecation")
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            // API 29 이하에서도 키보드가 올라올 때 바텀시트가 리사이즈되어 하단 입력창이 가려지지 않게 한다.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                attachParentLayoutObserver(bottomSheet);
                updateSheetLayout();
            }
        });
        return dialog;
    }

    private void attachParentLayoutObserver(@NonNull FrameLayout bottomSheet) {
        if (bottomSheet.getParent() instanceof View) {
            View parent = (View) bottomSheet.getParent();
            if (observedSheetParent != parent || sheetParentLayoutListener == null) {
                if (observedSheetParent != null && sheetParentLayoutListener != null) {
                    observedSheetParent.removeOnLayoutChangeListener(sheetParentLayoutListener);
                }
                observedSheetParent = parent;
                sheetParentLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (bottom != oldBottom || top != oldTop) {
                        v.post(this::updateSheetLayout);
                    }
                };
                parent.addOnLayoutChangeListener(sheetParentLayoutListener);
            }
        }
        if (observedBottomSheet != bottomSheet || bottomSheetLayoutListener == null) {
            if (observedBottomSheet != null && bottomSheetLayoutListener != null) {
                observedBottomSheet.removeOnLayoutChangeListener(bottomSheetLayoutListener);
            }
            observedBottomSheet = bottomSheet;
            bottomSheetLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int currentPadBottom = v.getPaddingBottom();
                if (currentPadBottom != lastObservedSheetPaddingBottom) {
                    lastObservedSheetPaddingBottom = currentPadBottom;
                    v.post(this::updateSheetLayout);
                }
            };
            bottomSheet.addOnLayoutChangeListener(bottomSheetLayoutListener);
        }
    }

    private void detachParentLayoutObserver() {
        if (observedSheetParent != null && sheetParentLayoutListener != null) {
            observedSheetParent.removeOnLayoutChangeListener(sheetParentLayoutListener);
        }
        if (observedBottomSheet != null && bottomSheetLayoutListener != null) {
            observedBottomSheet.removeOnLayoutChangeListener(bottomSheetLayoutListener);
        }
        observedSheetParent = null;
        sheetParentLayoutListener = null;
        observedBottomSheet = null;
        bottomSheetLayoutListener = null;
        lastObservedSheetPaddingBottom = -1;
    }

    // 기기·Android 버전마다 ADJUST_RESIZE나 Material BottomSheetDialog가 부모(CoordinatorLayout) 또는
    // design_bottom_sheet의 paddingBottom으로 이미 키보드·내비게이션 바 높이를 반영하는 경우가 다르다.
    // 실제 화면 좌표에서 남은 겹침량만 rootView 패딩으로 보정해 이중 패딩을 막는다.
    private void updateSheetLayout() {
        if (binding == null || getContext() == null) {
            return;
        }
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null || !(bottomSheet.getParent() instanceof View)) {
            return;
        }
        View parent = (View) bottomSheet.getParent();
        int parentHeight = parent.getHeight();
        if (parentHeight <= 0) {
            parentHeight = getResources().getDisplayMetrics().heightPixels;
            if (parentHeight <= 0) {
                return;
            }
        }

        int parentOverlapTop = lastBarsTop;
        int parentOverlapBottom = lastRawBottomInset;

        Window dialogWindow = dialog.getWindow();
        View dialogDecor = dialogWindow != null ? dialogWindow.getDecorView() : null;
        View activityDecor = getActivity() != null && getActivity().getWindow() != null
                ? getActivity().getWindow().getDecorView()
                : null;

        if (parent.getHeight() > 0 && dialogDecor != null && dialogDecor.getHeight() > 0) {
            int[] parentLoc = new int[2];
            int[] dialogLoc = new int[2];
            parent.getLocationOnScreen(parentLoc);
            dialogDecor.getLocationOnScreen(dialogLoc);

            int windowTop = dialogLoc[1];
            int windowBottom = dialogLoc[1] + dialogDecor.getHeight();
            if (activityDecor != null && activityDecor.getHeight() > 0) {
                int[] activityLoc = new int[2];
                activityDecor.getLocationOnScreen(activityLoc);
                windowTop = Math.min(windowTop, activityLoc[1]);
                windowBottom = Math.max(windowBottom, activityLoc[1] + activityDecor.getHeight());
            }

            int safeTop = windowTop + lastBarsTop;
            int safeBottom = windowBottom - lastRawBottomInset;
            int parentTop = parentLoc[1];
            int parentBottom = parentLoc[1] + parent.getHeight();

            parentOverlapTop = Math.max(0, safeTop - parentTop);
            parentOverlapBottom = Math.max(0, parentBottom - safeBottom);
        }

        // design_bottom_sheet 자체가 이미 적용한 paddingBottom만큼은 빼고, 진짜 남은 겹침량만 rootView에 더한다.
        int sheetPaddingBottom = bottomSheet.getPaddingBottom();
        lastObservedSheetPaddingBottom = sheetPaddingBottom;
        int rootOverlapBottom = Math.max(0, parentOverlapBottom - sheetPaddingBottom);

        View rootView = binding.getRoot();
        int nextPaddingBottom = basePaddingBottom + rootOverlapBottom;
        if (rootView.getPaddingBottom() != nextPaddingBottom) {
            rootView.setPadding(basePaddingLeft, basePaddingTop, basePaddingRight, nextPaddingBottom);
        }

        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int usableHeight = Math.max(0, parentHeight - parentOverlapTop - parentOverlapBottom);
        int computedHeight;
        if (lastImeVisible || isLandscape) {
            int topGap = Math.round(12 * getResources().getDisplayMetrics().density);
            computedHeight = Math.max(0, usableHeight - topGap) + parentOverlapBottom;
        } else {
            // 세로 기본 상태에서는 사용 가능 높이의 85%를 차지해 상단 영상이 살짝 보이면서도
            // 하단 입력창이 스크롤 없이 바로 화면 바닥에 표시되도록 한다.
            computedHeight = Math.round(usableHeight * 0.85f) + parentOverlapBottom;
        }
        int maxAllowedHeight = Math.max(0, parentHeight - parentOverlapTop);
        int targetHeight = maxAllowedHeight > 0
                ? Math.min(computedHeight, maxAllowedHeight)
                : computedHeight;

        ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
        if (lp != null) {
            boolean changed = lp.height != targetHeight;
            lp.height = targetHeight;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                if (mlp.topMargin != 0) {
                    mlp.topMargin = 0;
                    changed = true;
                }
            }
            if (changed) {
                bottomSheet.setLayoutParams(lp);
            }
        }

        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        if (behavior.getPeekHeight() != targetHeight) {
            behavior.setPeekHeight(targetHeight);
        }
        if (behavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0.5f);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCommentBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        basePaddingLeft = view.getPaddingLeft();
        basePaddingTop = view.getPaddingTop();
        basePaddingRight = view.getPaddingRight();
        basePaddingBottom = view.getPaddingBottom();

        // 시스템 하단 내비게이션 바·키보드 인셋을 실제 부모 겹침량 기준으로 반영한다.
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            lastBarsTop = bars.top;
            lastRawBottomInset = Math.max(bars.bottom, ime.bottom);
            if (lastImeVisible != imeVisible) {
                Log.d(TAG, "댓글 바텀시트 키보드 상태 변경: imeVisible=" + imeVisible
                        + ", imeBottom=" + ime.bottom + ", barsBottom=" + bars.bottom);
            }
            lastImeVisible = imeVisible;

            updateSheetLayout();
            return insets;
        });
        ViewCompat.requestApplyInsets(view);

        // 0) 맨 위 원본 리뷰 영역 채우기 (전달값이 있으면 즉시 표시, 없으면 Firestore에서 조회)
        setupOriginalReviewHeader();

        // 리사이클러뷰 및 어댑터 연결
        binding.commentsList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CommentAdapter(new ArrayList<>(), new CommentAdapter.OnCommentActionListener() {
            @Override
            public void onReplyClick(Comment comment) {
                if (comment == null) {
                    return;
                }
                // 최상위 댓글이면 그 댓글에, 1단 답글이면 같은 최상위 댓글 아래에 답글을 단다.
                String parentId = comment.isReply() ? comment.getParentId() : comment.getId();
                if (TextUtils.isEmpty(parentId)) {
                    return;
                }
                String authorName = !TextUtils.isEmpty(comment.getAuthorName())
                        ? comment.getAuthorName().trim()
                        : getString(R.string.comments_unknown_author);
                String mention = !TextUtils.isEmpty(comment.getAuthorUsername())
                        ? comment.getAuthorUsername().trim()
                        : authorName;
                startReplyMode(parentId, authorName, mention);
            }

            @Override
            public void onLikeClick(Comment comment) {
                toggleCommentLike(comment);
            }
        });
        binding.commentsList.setAdapter(adapter);

        // 1) 답글 취소 (X 버튼 클릭 리스너)
        binding.replyTargetCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelReplyMode();
            }
        });

        // 2) 입력창 상태에 따른 등록 버튼 활성화
        binding.commentsSend.setEnabled(false);
        binding.commentsSend.setAlpha(0.3f);
        binding.commentsInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasContent = s != null && s.toString().trim().length() > 0;
                binding.commentsSend.setEnabled(hasContent);
                binding.commentsSend.setAlpha(hasContent ? 1.0f : 0.3f);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 3) 댓글/답글 등록 버튼 클릭 리스너
        binding.commentsSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendComment();
            }
        });

        // 회전으로 복원된 답글 대상이 있으면 안내 바를 다시 보여 준다.
        if (!TextUtils.isEmpty(targetParentId) && !TextUtils.isEmpty(targetAuthorName)) {
            renderReplyTargetBar(targetAuthorName);
        }

        // 4) 내 프로필 아바타 띄우기 및 댓글 목록 서버 조회
        setupMyAvatar();
        loadComments();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_TARGET_PARENT_ID, targetParentId);
        outState.putString(STATE_TARGET_AUTHOR_NAME, targetAuthorName);
        outState.putString(STATE_TARGET_USERNAME, targetUsername);
        super.onSaveInstanceState(outState);
    }

    // 바텀시트 맨 위에 원본 리뷰(작성자·별점·한줄평)를 표시한다.
    private void setupOriginalReviewHeader() {
        if (initialReviewScore > 0 && !TextUtils.isEmpty(initialAuthorName)) {
            bindOriginalReview(initialAuthorName, initialAuthorMeta, initialReviewScore, initialReviewComment);
            return;
        }
        if (TextUtils.isEmpty(ratingId)) {
            return;
        }
        db.collection(FirestorePaths.RATINGS).document(ratingId).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot ratingDoc) {
                        if (binding == null || !ratingDoc.exists()) {
                            return;
                        }
                        Rating rating = ratingDoc.toObject(Rating.class);
                        if (rating == null) {
                            return;
                        }
                        Context context = getContext();
                        if (context == null) {
                            return;
                        }
                        final String timeText = rating.getCreatedAt() == null
                                ? context.getString(R.string.comments_unknown_time)
                                : TimeAgoFormat.format(context, rating.getCreatedAt().toDate(), new Date());
                        final long score = rating.getScore();
                        final String reviewComment = rating.getComment();
                        final String authorUid = rating.getUserId();

                        // 작성자 프로필 조회 전에 별점·한줄평을 먼저 보여 준다.
                        bindOriginalReview(
                                context.getString(R.string.comments_unknown_author),
                                timeText,
                                score,
                                reviewComment);

                        if (TextUtils.isEmpty(authorUid) || authorUid.contains("/")) {
                            return;
                        }
                        db.collection(FirestorePaths.USERS).document(authorUid).get()
                                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                    @Override
                                    public void onSuccess(DocumentSnapshot userDoc) {
                                        Context currentContext = getContext();
                                        if (binding == null || currentContext == null) {
                                            return;
                                        }
                                        User user = userDoc.exists() ? userDoc.toObject(User.class) : null;
                                        String name = user != null && !TextUtils.isEmpty(user.getDisplayName())
                                                ? user.getDisplayName().trim()
                                                : currentContext.getString(R.string.comments_unknown_author);
                                        String username = user != null ? user.getUsername() : null;
                                        String meta = TextUtils.isEmpty(username)
                                                ? timeText
                                                : currentContext.getString(R.string.author_meta, username.trim(), timeText);
                                        bindOriginalReview(name, meta, score, reviewComment);
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        Log.w(TAG, "원본 리뷰 조회 실패", error);
                    }
                });
    }

    private void bindOriginalReview(@NonNull String authorName,
                                    @Nullable String authorMeta,
                                    long score,
                                    @Nullable String comment) {
        Context context = getContext();
        if (binding == null || context == null) {
            return;
        }
        String safeName = TextUtils.isEmpty(authorName)
                ? context.getString(R.string.comments_unknown_author)
                : authorName.trim();
        binding.commentsOriginalAuthor.authorName.setText(safeName);
        binding.commentsOriginalAuthor.authorAvatar.setText(
                safeName.substring(0, safeName.offsetByCodePoints(0, 1)));
        binding.commentsOriginalAuthor.authorMeta.setText(authorMeta == null ? "" : authorMeta);
        binding.commentsOriginalRating.setText(context.getString(
                R.string.video_detail_review_score, RatingFormat.stars(score), score));
        String trimmedComment = comment == null ? "" : comment.trim();
        binding.commentsOriginalComment.setText(trimmedComment.isEmpty()
                ? context.getString(R.string.comments_original_review_no_comment)
                : trimmedComment);
        binding.commentsOriginalReview.setVisibility(View.VISIBLE);
        binding.commentsOriginalDivider.setVisibility(View.VISIBLE);
    }

    private void toggleCommentLike(@Nullable Comment comment) {
        if (comment == null || TextUtils.isEmpty(ratingId) || TextUtils.isEmpty(comment.getId())) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), R.string.reactions_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        final String commentId = comment.getId();
        if (!commentLikeInFlight.add(commentId)) {
            return;
        }
        final String uid = user.getUid();
        DocumentReference commentRef = db.collection(FirestorePaths.RATINGS).document(ratingId)
                .collection(FirestorePaths.COMMENTS).document(commentId);
        DocumentReference likeRef = commentRef.collection(FirestorePaths.LIKES).document(uid);

        likeRef.get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        final boolean currentlyLiked = snapshot.exists();
                        WriteBatch batch = db.batch();
                        if (currentlyLiked) {
                            batch.delete(likeRef);
                            batch.update(commentRef, "likeCount", FieldValue.increment(-1));
                        } else {
                            Map<String, Object> data = new HashMap<>();
                            data.put("createdAt", FieldValue.serverTimestamp());
                            batch.set(likeRef, data);
                            batch.update(commentRef, "likeCount", FieldValue.increment(1));
                        }
                        batch.commit()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        commentLikeInFlight.remove(commentId);
                                        if (binding == null) {
                                            return;
                                        }
                                        if (currentlyLiked) {
                                            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
                                            comment.setLikedByCurrentUser(false);
                                        } else {
                                            comment.setLikeCount(comment.getLikeCount() + 1);
                                            comment.setLikedByCurrentUser(true);
                                        }
                                        if (adapter != null) {
                                            adapter.notifyDataSetChanged();
                                        }
                                        Log.d(TAG, "댓글 좋아요 토글 성공: commentId=" + commentId
                                                + ", liked=" + !currentlyLiked);
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception error) {
                                        commentLikeInFlight.remove(commentId);
                                        Log.e(TAG, "댓글 좋아요 저장 실패: commentId=" + commentId, error);
                                        if (getContext() != null) {
                                            Toast.makeText(getContext(), R.string.reactions_save_error,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        commentLikeInFlight.remove(commentId);
                        Log.e(TAG, "댓글 좋아요 상태 조회 실패: commentId=" + commentId, error);
                    }
                });
    }

    // 내 프로필 아바타 읽어서 띄우기
    private void setupMyAvatar() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || binding == null) return;

        String uid = user.getUid();
        db.collection(FirestorePaths.USERS).document(uid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (binding == null) return;
                        User profile = snapshot.exists() ? snapshot.toObject(User.class) : null;
                        String name = profile != null && !TextUtils.isEmpty(profile.getDisplayName())
                                ? profile.getDisplayName()
                                : (user.getDisplayName() != null ? user.getDisplayName() : "익명");
                        name = name.trim();
                        binding.commentsMyAvatar.setText(name.substring(0, name.offsetByCodePoints(0, 1)));
                    }
                });
    }

    // 답글 모드 시작 (최상위 댓글·답글 모두에서 호출 가능)
    public void startReplyMode(String parentId, String authorName, String username) {
        this.targetParentId = parentId;
        this.targetAuthorName = authorName;
        this.targetUsername = username;

        renderReplyTargetBar(authorName);
        binding.commentsInput.requestFocus();
    }

    private void renderReplyTargetBar(@NonNull String authorName) {
        if (binding == null || getContext() == null) {
            return;
        }
        binding.replyTargetText.setText(getString(R.string.comments_reply_target, authorName));
        binding.replyTargetBar.setVisibility(View.VISIBLE);
        binding.commentsInput.setHint(getString(R.string.comments_reply_hint, authorName));
    }

    // 답글 취소 (X 버튼 또는 작성 성공 시)
    private void cancelReplyMode() {
        this.targetParentId = null;
        this.targetAuthorName = null;
        this.targetUsername = null;
        if (binding != null) {
            binding.replyTargetBar.setVisibility(View.GONE);
            binding.replyTargetText.setText("");
            binding.commentsInput.setHint(R.string.comments_input_hint);
        }
    }

    // 서버에서 댓글 목록 읽기
    private void loadComments() {
        if (ratingId == null || ratingId.isEmpty()) return;

        binding.commentsStatus.setVisibility(View.VISIBLE);
        binding.commentsStatus.setText(R.string.comments_loading);

        db.collection(FirestorePaths.RATINGS).document(ratingId)
                .collection(FirestorePaths.COMMENTS)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot snapshot) {
                        if (binding == null) return;
                        binding.commentsStatus.setVisibility(View.GONE);

                        List<Comment> rawComments = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Comment c = doc.toObject(Comment.class);
                            if (c != null) {
                                c.setId(doc.getId());
                                rawComments.add(c);
                            }
                        }

                        // 최상위 댓글과 1단 답글 순서 정렬 (부모 댓글 직후에 해당 답글들 배치)
                        List<Comment> sorted = sortCommentsWithReplies(rawComments);
                        adapter.setComments(sorted);

                        // 각 댓글/답글에 대해 현재 유저의 좋아요 누름 상태 확인
                        for (Comment comment : sorted) {
                            checkUserLikeStatus(comment);
                        }

                        if (sorted.isEmpty()) {
                            binding.commentsList.setVisibility(View.GONE);
                            binding.commentsEmpty.setVisibility(View.VISIBLE);
                        } else {
                            binding.commentsEmpty.setVisibility(View.GONE);
                            binding.commentsList.setVisibility(View.VISIBLE);
                        }
                        Log.d(TAG, "댓글 목록 로드 성공: ratingId=" + ratingId + ", count=" + sorted.size());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        if (binding == null) return;
                        binding.commentsStatus.setVisibility(View.VISIBLE);
                        binding.commentsStatus.setText(R.string.comments_load_error);
                        Log.e(TAG, "댓글 목록 로드 실패", error);
                    }
                });
    }

    private void checkUserLikeStatus(Comment comment) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || ratingId == null || comment.getId() == null) return;
        db.collection(FirestorePaths.RATINGS).document(ratingId)
                .collection(FirestorePaths.COMMENTS).document(comment.getId())
                .collection(FirestorePaths.LIKES).document(user.getUid()).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (binding == null) {
                            return;
                        }
                        if (snapshot.exists()) {
                            comment.setLikedByCurrentUser(true);
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                });
    }

    // 최상위 댓글과 답글을 묶어서 정렬하는 도우미 메서드
    private List<Comment> sortCommentsWithReplies(List<Comment> raw) {
        List<Comment> result = new ArrayList<>();
        Map<String, List<Comment>> repliesMap = new HashMap<>();

        // 1) 1단 답글들을 parentId별로 수집
        for (Comment c : raw) {
            if (c.isReply()) {
                List<Comment> list = repliesMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>());
                list.add(c);
            }
        }

        // 2) 최상위 댓글 추가 후 그 직후에 해당하는 답글들 추가
        for (Comment c : raw) {
            if (!c.isReply()) {
                result.add(c);
                List<Comment> replies = repliesMap.get(c.getId());
                if (replies != null) {
                    result.addAll(replies);
                }
            }
        }
        return result;
    }

    // 댓글/답글 작성 등록
    private void sendComment() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), R.string.reactions_login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String input = binding.commentsInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            return;
        }

        binding.commentsSend.setEnabled(false);

        final String replyParentId = targetParentId;
        final String replyTargetUsername = targetUsername;

        // 작성자 프로필 정보 읽어서 함께 저장
        db.collection(FirestorePaths.USERS).document(user.getUid()).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot userDoc) {
                        if (binding == null) {
                            return;
                        }
                        User profile = userDoc.exists() ? userDoc.toObject(User.class) : null;
                        String authorName = profile != null && !TextUtils.isEmpty(profile.getDisplayName())
                                ? profile.getDisplayName().trim()
                                : (user.getDisplayName() != null ? user.getDisplayName() : "익명");
                        String authorUsername = profile != null && profile.getUsername() != null
                                ? profile.getUsername() : "";

                        Comment comment = new Comment();
                        comment.setRatingId(ratingId);
                        comment.setUserId(user.getUid());
                        comment.setAuthorName(authorName);
                        comment.setAuthorUsername(authorUsername);
                        comment.setParentId(replyParentId);
                        comment.setTargetUsername(replyTargetUsername);
                        comment.setContent(input);

                        // Firestore에 댓글 저장 + 평가 문서의 commentCount +1 을 함께 커밋
                        DocumentReference ratingRef = db.collection(FirestorePaths.RATINGS).document(ratingId);
                        DocumentReference newCommentRef = ratingRef.collection(FirestorePaths.COMMENTS).document();
                        WriteBatch batch = db.batch();
                        batch.set(newCommentRef, comment);
                        batch.update(ratingRef, "commentCount", FieldValue.increment(1));
                        batch.commit()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        if (binding == null) return;
                                        binding.commentsInput.setText("");
                                        binding.commentsSend.setEnabled(false);
                                        binding.commentsSend.setAlpha(0.3f);

                                        Log.d(TAG, "댓글 등록 성공: ratingId=" + ratingId
                                                + ", isReply=" + !TextUtils.isEmpty(replyParentId));

                                        // 부모 화면(VideoDetailActivity / FeedFragment)에 댓글 수 +1 알림
                                        Bundle result = new Bundle();
                                        result.putString(RESULT_RATING_ID, ratingId);
                                        result.putBoolean(RESULT_COMMENT_ADDED, true);
                                        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);

                                        // 답글 모드 초기화 후 목록 새로고침
                                        cancelReplyMode();
                                        loadComments();
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        if (binding == null) return;
                                        binding.commentsSend.setEnabled(true);
                                        Log.e(TAG, "댓글 등록 실패: ratingId=" + ratingId, e);
                                        Toast.makeText(getContext(), R.string.reactions_save_error,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (binding == null) return;
                        binding.commentsSend.setEnabled(true);
                        Log.e(TAG, "댓글 작성자 프로필 조회 실패", e);
                        Toast.makeText(getContext(), R.string.reactions_save_error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        detachParentLayoutObserver();
        super.onDestroyView();
        binding = null;
    }
}
