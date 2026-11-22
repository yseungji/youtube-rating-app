package com.example.viewit.ui.watchlater;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.viewit.R;
import com.example.viewit.data.WatchLaterRepository;
import com.example.viewit.databinding.FragmentWatchLaterBinding;
import com.example.viewit.ui.common.ThumbnailLoader;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// 피드·영상 상세 위에 바텀시트로 띄우는 "나중에 보기" 저장 Fragment (5.4).
// 시트 안의 "나중에 보기" 항목을 클릭하면 users/{uid}/watchLater/{videoId}에 저장(또는 취소)한다.
// 화면 회전 시 onSaveInstanceState()로 저장하고 onViewStateRestored()로 복원한다.
public class WatchLaterBottomSheetFragment extends BottomSheetDialogFragment {
    private static final String TAG = "WatchLaterBottomSheet";

    public static final String REQUEST_KEY = "watch_later_result";
    public static final String RESULT_VIDEO_ID = "watchLater.videoId";
    public static final String RESULT_SAVED = "watchLater.saved";

    private static final String ARG_VIDEO_ID = "watchLater.videoId";
    private static final String ARG_VIDEO_TITLE = "watchLater.videoTitle";
    private static final String ARG_VIDEO_META = "watchLater.videoMeta";
    private static final String ARG_THUMBNAIL_URL = "watchLater.thumbnailUrl";
    private static final String ARG_SAVED = "watchLater.saved";

    private final WatchLaterRepository watchLaterRepository = new WatchLaterRepository();
    private FragmentWatchLaterBinding binding;
    private String videoId = "";
    private String videoTitle = "";
    private String videoMeta = "";
    private String thumbnailUrl = "";
    private boolean saved;
    private boolean writeInProgress;

    public static WatchLaterBottomSheetFragment newInstance(@Nullable String videoId,
                                                            @Nullable String videoTitle,
                                                            @Nullable String videoMeta,
                                                            @Nullable String thumbnailUrl,
                                                            boolean initialSaved) {
        WatchLaterBottomSheetFragment fragment = new WatchLaterBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_ID, videoId != null ? videoId : "");
        args.putString(ARG_VIDEO_TITLE, videoTitle != null ? videoTitle : "");
        args.putString(ARG_VIDEO_META, videoMeta != null ? videoMeta : "");
        args.putString(ARG_THUMBNAIL_URL, thumbnailUrl != null ? thumbnailUrl : "");
        args.putBoolean(ARG_SAVED, initialSaved);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle source = savedInstanceState != null ? savedInstanceState : getArguments();
        readSheetData(source);
        Log.d(TAG, "나중에 보기 바텀시트 생성: restored=" + (savedInstanceState != null)
                + ", videoId=" + videoId + ", saved=" + saved);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0.5f);
        }
        // BottomSheet 기본 배경을 투명하게 바꿔 위쪽 둥근 모서리(bg_share_sheet)가 그대로 보이게 한다.
        View root = getView();
        if (root != null && root.getParent() instanceof View) {
            ((View) root.getParent()).setBackgroundResource(android.R.color.transparent);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWatchLaterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindPreview();
        updateSavedUi();
        setupClickListeners();
        syncSavedStateFromServer();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_VIDEO_ID, videoId);
        outState.putString(ARG_VIDEO_TITLE, videoTitle);
        outState.putString(ARG_VIDEO_META, videoMeta);
        outState.putString(ARG_THUMBNAIL_URL, thumbnailUrl);
        outState.putBoolean(ARG_SAVED, saved);
        Log.d(TAG, "나중에 보기 바텀시트 상태 저장(onSaveInstanceState): saved=" + saved);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            readSheetData(savedInstanceState);
            bindPreview();
            updateSavedUi();
            Log.d(TAG, "나중에 보기 바텀시트 상태 복원(onViewStateRestored): saved=" + saved);
        }
    }

    private void readSheetData(@Nullable Bundle bundle) {
        if (bundle == null) {
            return;
        }
        videoId = bundle.getString(ARG_VIDEO_ID, "");
        videoTitle = bundle.getString(ARG_VIDEO_TITLE, "");
        videoMeta = bundle.getString(ARG_VIDEO_META, "");
        thumbnailUrl = bundle.getString(ARG_THUMBNAIL_URL, "");
        saved = bundle.getBoolean(ARG_SAVED, false);
    }

    private void bindPreview() {
        if (binding == null) {
            return;
        }
        String trimmedTitle = videoTitle == null ? "" : videoTitle.trim();
        binding.watchLaterVideoTitle.setText(
                trimmedTitle.isEmpty() ? getString(R.string.feed_video_unavailable) : trimmedTitle);

        String trimmedMeta = videoMeta == null ? "" : videoMeta.trim();
        if (trimmedMeta.isEmpty()) {
            binding.watchLaterVideoMeta.setVisibility(View.GONE);
        } else {
            binding.watchLaterVideoMeta.setVisibility(View.VISIBLE);
            binding.watchLaterVideoMeta.setText(trimmedMeta);
        }

        String resolvedThumb = thumbnailUrl;
        if (TextUtils.isEmpty(resolvedThumb) && !TextUtils.isEmpty(videoId)) {
            resolvedThumb = "https://i.ytimg.com/vi/" + videoId.trim() + "/hqdefault.jpg";
        }
        ThumbnailLoader.load(binding.watchLaterThumbnail, resolvedThumb);
    }

    private void updateSavedUi() {
        if (binding == null) {
            return;
        }
        if (saved) {
            binding.watchLaterTitle.setText(R.string.watch_later_sheet_title_saved);
            binding.watchLaterActionIcon.setImageResource(R.drawable.ic_bookmark);
            binding.watchLaterActionSubtitle.setText(R.string.watch_later_action_subtitle_saved);
        } else {
            binding.watchLaterTitle.setText(R.string.watch_later_sheet_title);
            binding.watchLaterActionIcon.setImageResource(R.drawable.ic_bookmark_add);
            binding.watchLaterActionSubtitle.setText(R.string.watch_later_action_subtitle);
        }
    }

    private void setupClickListeners() {
        binding.watchLaterActionRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onWatchLaterRowClicked();
            }
        });
    }

    private void syncSavedStateFromServer() {
        if (TextUtils.isEmpty(videoId)) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        watchLaterRepository.isSaved(videoId)
                .addOnSuccessListener(new OnSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean exists) {
                        if (binding == null || writeInProgress) {
                            return;
                        }
                        saved = Boolean.TRUE.equals(exists);
                        updateSavedUi();
                        deliverResult(saved);
                        Log.d(TAG, "나중에 보기 서버 상태 확인: videoId=" + videoId + ", saved=" + saved);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        Log.w(TAG, "나중에 보기 서버 상태 확인 실패: videoId=" + videoId, error);
                    }
                });
    }

    private void onWatchLaterRowClicked() {
        if (writeInProgress) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), R.string.reactions_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(videoId) || videoId.contains("/")) {
            Log.w(TAG, "유효한 영상 ID가 없어 나중에 보기 저장 불가");
            Toast.makeText(requireContext(), R.string.watch_later_save_error, Toast.LENGTH_SHORT).show();
            return;
        }

        writeInProgress = true;
        binding.watchLaterActionRow.setEnabled(false);

        if (saved) {
            watchLaterRepository.removeWatchLater(videoId)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            writeInProgress = false;
                            if (binding == null) {
                                return;
                            }
                            binding.watchLaterActionRow.setEnabled(true);
                            saved = false;
                            updateSavedUi();
                            deliverResult(false);
                            Toast.makeText(requireContext(),
                                    R.string.watch_later_removed_toast, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception error) {
                            writeInProgress = false;
                            if (binding == null) {
                                return;
                            }
                            binding.watchLaterActionRow.setEnabled(true);
                            Log.e(TAG, "나중에 보기 제거 실패: videoId=" + videoId, error);
                            Toast.makeText(requireContext(),
                                    R.string.watch_later_remove_error, Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            watchLaterRepository.saveWatchLater(videoId)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            writeInProgress = false;
                            if (binding == null) {
                                return;
                            }
                            binding.watchLaterActionRow.setEnabled(true);
                            saved = true;
                            updateSavedUi();
                            deliverResult(true);
                            Toast.makeText(requireContext(),
                                    R.string.watch_later_saved_toast, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception error) {
                            writeInProgress = false;
                            if (binding == null) {
                                return;
                            }
                            binding.watchLaterActionRow.setEnabled(true);
                            Log.e(TAG, "나중에 보기 저장 실패: videoId=" + videoId, error);
                            Toast.makeText(requireContext(),
                                    R.string.watch_later_save_error, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void deliverResult(boolean isSaved) {
        if (!isAdded()) {
            return;
        }
        Bundle result = new Bundle();
        result.putString(RESULT_VIDEO_ID, videoId);
        result.putBoolean(RESULT_SAVED, isSaved);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        Log.d(TAG, "Fragment 결과 전달(setFragmentResult): videoId=" + videoId + ", saved=" + isSaved);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
