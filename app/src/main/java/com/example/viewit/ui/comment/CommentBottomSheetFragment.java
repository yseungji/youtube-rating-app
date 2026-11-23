package com.example.viewit.ui.comment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.data.model.Comment;
import com.example.viewit.data.model.FirestorePaths;
import com.example.viewit.data.model.User;
import com.example.viewit.databinding.FragmentCommentBottomSheetBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String TAG = "CommentBottomSheet";
    private static final String ARG_RATING_ID = "ratingId";

    private FragmentCommentBottomSheetBinding binding;
    private String ratingId;
    private CommentAdapter adapter;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 답글 모드 관리 변수
    private String targetParentId = null;        // 최상위 댓글 ID (null이면 일반 댓글)
    private String targetUsername = null;        // 답글 대상자 아이디 (@아이디 언급용)

    public static CommentBottomSheetFragment newInstance(String ratingId) {
        CommentBottomSheetFragment fragment = new CommentBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RATING_ID, ratingId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ratingId = getArguments().getString(ARG_RATING_ID);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            // 인스타그램처럼 바텀시트가 올라왔을 때 배경 화면을 회색빛(Dim)으로 어둡게 덮음
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

        // 리사이클러뷰 및 어댑터 연결
        binding.commentsList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CommentAdapter(new ArrayList<>(), new CommentAdapter.OnCommentActionListener() {
            @Override
            public void onReplyClick(Comment comment) {
                // 1단 답글 또는 최상위 댓글에 답글 달기 선택 시
                String parentId = comment.isReply() ? comment.getParentId() : comment.getId();
                String authorName = comment.getAuthorName() != null ? comment.getAuthorName() : "작성자";
                String username = comment.getAuthorUsername() != null ? comment.getAuthorUsername() : "";
                startReplyMode(parentId, authorName, username);
            }

            @Override
            public void onLikeClick(Comment comment) {
                if (ratingId == null || comment.getId() == null) return;
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) {
                    Toast.makeText(getContext(), R.string.reactions_login_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                final String uid = user.getUid();
                DocumentReference commentRef = db.collection(FirestorePaths.RATINGS).document(ratingId)
                        .collection("comments").document(comment.getId());
                DocumentReference likeRef = commentRef.collection("likes").document(uid);

                likeRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // 이미 좋아요 누른 상태 -> 좋아요 취소 (-1 및 빨간 하트 해제)
                            likeRef.delete();
                            commentRef.update("likeCount", FieldValue.increment(-1));
                            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
                            comment.setLikedByCurrentUser(false);
                        } else {
                            // 좋아요 안 누른 상태 -> 좋아요 등록 (+1 및 빨간 하트 표시)
                            Map<String, Object> data = new HashMap<>();
                            data.put("createdAt", FieldValue.serverTimestamp());
                            likeRef.set(data);

                            commentRef.update("likeCount", FieldValue.increment(1));
                            comment.setLikeCount(comment.getLikeCount() + 1);
                            comment.setLikedByCurrentUser(true);
                        }
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
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

        // 2) 입력창 상태에 따른 등록 버튼 불빛 활성화
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

        // 4) 내 프로필 아바타 띄우기 및 데이터 서버 조회
        setupMyAvatar();
        loadComments();
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
                                ? profile.getDisplayName() : (user.getDisplayName() != null ? user.getDisplayName() : "익명");
                        name = name.trim();
                        binding.commentsMyAvatar.setText(name.substring(0, name.offsetByCodePoints(0, 1)));
                    }
                });
    }

    // 답글 모드 시작
    public void startReplyMode(String parentId, String authorName, String username) {
        this.targetParentId = parentId;
        this.targetUsername = username;

        binding.replyTargetText.setText(authorName + "님에게 답글 남기는 중");
        binding.replyTargetBar.setVisibility(View.VISIBLE);

        // 댓글 입력창에 @아이디 (또는 @이름) 자동으로 채워주기
        String mentionTag = "@" + (!TextUtils.isEmpty(username) ? username : authorName) + " ";
        binding.commentsInput.setText(mentionTag);
        binding.commentsInput.setSelection(binding.commentsInput.getText().length());
        binding.commentsInput.requestFocus();
    }

    // 답글 취소 (X 버튼 또는 작성 성공 시)
    private void cancelReplyMode() {
        this.targetParentId = null;
        this.targetUsername = null;
        if (binding != null) {
            binding.replyTargetBar.setVisibility(View.GONE);
            binding.replyTargetText.setText("");
            binding.commentsInput.setText("");
            binding.commentsInput.setHint("회원님의 생각을 남겨주세요…");
        }
    }

    // 서버에서 댓글 목록 읽기
    private void loadComments() {
        if (ratingId == null || ratingId.isEmpty()) return;

        binding.commentsStatus.setVisibility(View.VISIBLE);
        binding.commentsStatus.setText(R.string.comments_loading);

        db.collection(FirestorePaths.RATINGS).document(ratingId)
                .collection("comments")
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
                            binding.commentsEmpty.setVisibility(View.VISIBLE);
                        } else {
                            binding.commentsEmpty.setVisibility(View.GONE);
                        }
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
                .collection("comments").document(comment.getId())
                .collection("likes").document(user.getUid()).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
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

        // 작성자 프로필 정보 읽어서 함께 저장
        db.collection(FirestorePaths.USERS).document(user.getUid()).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot userDoc) {
                        User profile = userDoc.exists() ? userDoc.toObject(User.class) : null;
                        String authorName = profile != null && !TextUtils.isEmpty(profile.getDisplayName())
                                ? profile.getDisplayName() : (user.getDisplayName() != null ? user.getDisplayName() : "익명");
                        String authorUsername = profile != null ? profile.getUsername() : "";

                        Comment comment = new Comment();
                        comment.setRatingId(ratingId);
                        comment.setUserId(user.getUid());
                        comment.setAuthorName(authorName);
                        comment.setAuthorUsername(authorUsername);
                        comment.setParentId(targetParentId);
                        comment.setTargetUsername(targetUsername);
                        comment.setContent(input);

                        // Firestore에 저장
                        DocumentReference ratingRef = db.collection(FirestorePaths.RATINGS).document(ratingId);
                        ratingRef.collection("comments").add(comment)
                                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        if (binding == null) return;
                                        binding.commentsSend.setEnabled(true);
                                        binding.commentsInput.setText("");

                                        // 평가 문서의 댓글 수 +1 증가
                                        ratingRef.update("commentCount", FieldValue.increment(1));

                                        // 답글 모드 초기화 후 다시 목록 새로고침
                                        cancelReplyMode();
                                        loadComments();
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        if (binding == null) return;
                                        binding.commentsSend.setEnabled(true);
                                        Toast.makeText(getContext(), R.string.reactions_save_error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
