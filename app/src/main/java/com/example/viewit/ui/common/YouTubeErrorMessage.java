package com.example.viewit.ui.common;

import android.content.Context;

import com.example.viewit.R;
import com.example.viewit.data.youtube.YouTubeException;

// YouTube 호출 실패 이유를 사용자에게 보여 줄 문구로 바꾼다.
// 예: Toast.makeText(this, YouTubeErrorMessage.of(this, error), Toast.LENGTH_SHORT).show();
public final class YouTubeErrorMessage {

    private YouTubeErrorMessage() {
    }

    public static String of(Context context, YouTubeException error) {
        int res;
        switch (error.getReason()) {
            case MISSING_API_KEY:
                res = R.string.youtube_error_missing_key;
                break;
            case KEY_REJECTED:
                res = R.string.youtube_error_key_rejected;
                break;
            case QUOTA_EXCEEDED:
                res = R.string.youtube_error_quota;
                break;
            case NOT_FOUND:
                res = R.string.youtube_error_not_found;
                break;
            case NETWORK:
                res = R.string.youtube_error_network;
                break;
            default:
                res = R.string.youtube_error_unknown;
                break;
        }
        return context.getString(res);
    }
}
