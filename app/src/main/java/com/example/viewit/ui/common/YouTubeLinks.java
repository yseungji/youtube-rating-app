package com.example.viewit.ui.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.viewit.R;

// YouTube 앱(없으면 브라우저)으로 영상을 연다 (4.2). 앱 안에서 재생이 막힌 영상에 쓴다.
// 예: YouTubeLinks.open(context, "jNQXAC9IVRw");
public final class YouTubeLinks {

    private YouTubeLinks() {
    }

    public static Uri watchUri(@NonNull String videoId) {
        return Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(videoId));
    }

    public static void open(@NonNull Context context, @NonNull String videoId) {
        // 주소만 알려 주면 기기에서 YouTube 주소를 여는 앱(YouTube 앱 또는 브라우저)이 열린다 (암시적 인텐트)
        Intent intent = new Intent(Intent.ACTION_VIEW, watchUri(videoId));
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.video_player_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
