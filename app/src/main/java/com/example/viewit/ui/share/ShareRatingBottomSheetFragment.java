package com.example.viewit.ui.share;

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
import com.example.viewit.databinding.FragmentShareRatingBinding;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.YouTubeLinks;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

// MainActivity를 수정하지 않고 피드 위에 바텀시트 오버레이로 띄우는 공유 Fragment.
// 회전 시 onSaveInstanceState()로 저장하고 onViewStateRestored()로 복원한다.
public class ShareRatingBottomSheetFragment extends BottomSheetDialogFragment {
    private static final String TAG = "ShareRatingBottomSheet";

    public static final String REQUEST_KEY = "share_rating_result";
    public static final String RESULT_ACTION = "share.action";

    public static final String ACTION_KAKAOTALK = "kakaotalk";
    public static final String ACTION_SMS = "sms";
    public static final String ACTION_COPY_LINK = "copy_link";
    public static final String ACTION_MORE = "more";

    private static final String ARG_AUTHOR_NAME = "share.authorName";
    private static final String ARG_SCORE = "share.score";
    private static final String ARG_COMMENT = "share.comment";
    private static final String ARG_VIDEO_TITLE = "share.videoTitle";
    private static final String ARG_VIDEO_ID = "share.videoId";

    private static final String KAKAOTALK_PACKAGE = "com.kakao.talk";

    private FragmentShareRatingBinding binding;
    private String authorName = "";
    private int score;
    private String comment = "";
    private String videoTitle = "";
    private String videoId = "";


    //newInstance 함수 Fragment 클래스를 생성하면 자동으로 만들어진다.
    public static ShareRatingBottomSheetFragment newInstance(@Nullable String authorName,
                                                             int score,
                                                             @Nullable String comment,
                                                             @Nullable String videoTitle,
                                                             @Nullable String videoId) {
        //필요한 데이터를 넣어서 리턴한다.
        ShareRatingBottomSheetFragment fragment = new ShareRatingBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_AUTHOR_NAME, authorName != null ? authorName : "");
        args.putInt(ARG_SCORE, score);
        args.putString(ARG_COMMENT, comment != null ? comment : "");
        args.putString(ARG_VIDEO_TITLE, videoTitle != null ? videoTitle : "");
        args.putString(ARG_VIDEO_ID, videoId != null ? videoId : "");
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 처음 열릴 때는 arguments에서, 화면 회전 등으로 재생성될 때는 savedInstanceState에서 읽는다.
        //번들을 가져온다.
        Bundle source = savedInstanceState != null ? savedInstanceState : getArguments();
        //읽는다.
        readShareData(source);
        Log.d(TAG, "공유 바텀시트 생성: restored=" + (savedInstanceState != null)
                + ", score=" + score + ", hasVideoId=" + !TextUtils.isEmpty(videoId));
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
        // bindPreview()·setupClickListeners()에서 binding을 사용하므로 ViewBinding으로 inflate해 반환한다.
        binding = FragmentShareRatingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindPreview();
        setupClickListeners();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_AUTHOR_NAME, authorName);
        outState.putInt(ARG_SCORE, score);
        outState.putString(ARG_COMMENT, comment);
        outState.putString(ARG_VIDEO_TITLE, videoTitle);
        outState.putString(ARG_VIDEO_ID, videoId);
        Log.d(TAG, "공유 바텀시트 상태 저장(onSaveInstanceState): score=" + score);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            readShareData(savedInstanceState);
            bindPreview();
            Log.d(TAG, "공유 바텀시트 상태 복원(onViewStateRestored): score=" + score);
        }
    }

    private void readShareData(@Nullable Bundle bundle) {
        if (bundle == null) {
            return;
        }
        authorName = bundle.getString(ARG_AUTHOR_NAME, "");
        score = bundle.getInt(ARG_SCORE, 0);
        comment = bundle.getString(ARG_COMMENT, "");
        videoTitle = bundle.getString(ARG_VIDEO_TITLE, "");
        videoId = bundle.getString(ARG_VIDEO_ID, "");
    }

    private void bindPreview() {
        if (binding == null) {
            return;
        }
        binding.sharePreviewAuthor.setText(buildAuthorHeading());
        binding.sharePreviewRating.setText(
                RatingFormat.stars(score) + " " + getString(R.string.rating_score, score));

        String trimmedComment = comment == null ? "" : comment.trim();
        if (trimmedComment.isEmpty()) {
            binding.sharePreviewComment.setVisibility(View.GONE);
        } else {
            binding.sharePreviewComment.setVisibility(View.VISIBLE);
            binding.sharePreviewComment.setText(getString(R.string.share_quote_format, trimmedComment));
        }

        String trimmedTitle = videoTitle == null ? "" : videoTitle.trim();
        if (trimmedTitle.isEmpty()) {
            binding.sharePreviewVideoTitle.setVisibility(View.GONE);
        } else {
            binding.sharePreviewVideoTitle.setVisibility(View.VISIBLE);
            binding.sharePreviewVideoTitle.setText(trimmedTitle);
        }

        String previewLink = buildPreviewLink();
        if (previewLink.isEmpty()) {
            binding.sharePreviewLink.setVisibility(View.GONE);
        } else {
            binding.sharePreviewLink.setVisibility(View.VISIBLE);
            binding.sharePreviewLink.setText(previewLink);
        }
    }

    private void setupClickListeners() {
        // 1. 메신저 (카카오톡 고정)
        binding.shareActionMessenger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareViaKakaoTalk();
            }
        });

        // 2. 문자 (SMS)
        binding.shareActionSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareViaSms();
            }
        });

        // 3. 링크 복사 (YouTube URL 복사)
        binding.shareActionCopyLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyYouTubeLink();
            }
        });

        // 4. 더보기 (Android 시스템 공유 창 + BroadcastReceiver)
        binding.shareActionMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareViaSystemChooser();
            }
        });
    }

    private void shareViaKakaoTalk() {
        String shareText = buildShareText();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        intent.setPackage(KAKAOTALK_PACKAGE);
        try {
            startActivity(intent);
            Log.d(TAG, "카카오톡 공유 실행 성공");
            deliverResultAndDismiss(ACTION_KAKAOTALK);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "카카오톡 미설치로 공유 실행 불가");
            Toast.makeText(requireContext(), R.string.share_kakaotalk_not_installed, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareViaSms() {
        String shareText = buildShareText();
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
        intent.putExtra("sms_body", shareText);
        try {
            startActivity(intent);
            Log.d(TAG, "문자 공유 실행 성공");
            deliverResultAndDismiss(ACTION_SMS);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "문자 앱 없음으로 공유 실행 불가");
            Toast.makeText(requireContext(), R.string.share_sms_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyYouTubeLink() {
        String url = buildYouTubeUrl();
        if (url.isEmpty()) {
            Log.w(TAG, "영상 ID가 없어 YouTube 링크 복사 불가");
            Toast.makeText(requireContext(), R.string.share_no_video_link, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Log.e(TAG, "ClipboardManager 조회 실패");
            return;
        }
        ClipData clip = ClipData.newPlainText(getString(R.string.share_clip_label), url);
        clipboard.setPrimaryClip(clip);
        Log.d(TAG, "YouTube 링크 클립보드 복사 성공: videoId=" + videoId);
        deliverResultAndDismiss(ACTION_COPY_LINK);
    }

    private void shareViaSystemChooser() {
        String shareText = buildShareText();
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, buildAuthorHeading());
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        // 사용자가 공유 창에서 앱을 고르면 OS가 ShareChooserReceiver로 브로드캐스트를 전달한다.
        Intent receiverIntent = new Intent(requireContext(), ShareChooserReceiver.class);
        receiverIntent.putExtra(ShareChooserReceiver.EXTRA_VIDEO_ID, videoId);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(), 0, receiverIntent, flags);

        Intent chooser = Intent.createChooser(
                sendIntent,
                getString(R.string.share_chooser_title),
                pendingIntent.getIntentSender());
        try {
            startActivity(chooser);
            Log.d(TAG, "시스템 공유 창 실행 성공 (BroadcastReceiver 연결)");
            deliverResultAndDismiss(ACTION_MORE);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "시스템 공유 창 실행 실패", e);
            Toast.makeText(requireContext(), R.string.share_chooser_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void deliverResultAndDismiss(String action) {
        Bundle result = new Bundle();
        result.putString(RESULT_ACTION, action);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        Log.d(TAG, "Fragment 결과 전달(setFragmentResult): action=" + action);
        dismiss();
    }

    private String buildAuthorHeading() {
        String displayAuthor = TextUtils.isEmpty(authorName)
                ? getString(R.string.feed_unknown_author) : authorName.trim();
        return getString(R.string.share_author_heading, displayAuthor);
    }

    private String buildYouTubeUrl() {
        if (TextUtils.isEmpty(videoId)) {
            return "";
        }
        return YouTubeLinks.watchUri(videoId).toString();
    }

    private String buildPreviewLink() {
        String url = buildYouTubeUrl();
        if (url.startsWith("https://www.")) {
            return url.substring("https://www.".length());
        }
        return url;
    }

    private String buildShareText() {
        StringBuilder builder = new StringBuilder();
        builder.append(buildAuthorHeading()).append("\n");
        builder.append(RatingFormat.stars(score))
                .append(" ")
                .append(getString(R.string.rating_score, score));
        String trimmedComment = comment == null ? "" : comment.trim();
        if (!trimmedComment.isEmpty()) {
            builder.append("\n").append(getString(R.string.share_quote_format, trimmedComment));
        }
        String trimmedTitle = videoTitle == null ? "" : videoTitle.trim();
        if (!trimmedTitle.isEmpty()) {
            builder.append("\n").append(trimmedTitle);
        }
        String url = buildYouTubeUrl();
        if (!url.isEmpty()) {
            builder.append("\n").append(url);
        }
        return builder.toString();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
