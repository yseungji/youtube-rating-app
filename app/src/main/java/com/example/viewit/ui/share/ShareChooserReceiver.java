package com.example.viewit.ui.share;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

// Android 시스템 공유 창(Intent.createChooser)에서 사용자가 어떤 앱을 선택했는지 OS로부터 수신한다.
public class ShareChooserReceiver extends BroadcastReceiver {
    private static final String TAG = "ShareChooserReceiver";
    public static final String EXTRA_VIDEO_ID = "share.videoId";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "공유 결과 브로드캐스트 인텐트가 비어 있음");
            return;
        }
        ComponentName chosenComponent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            chosenComponent = intent.getParcelableExtra(
                    Intent.EXTRA_CHOSEN_COMPONENT, ComponentName.class);
        } else {
            chosenComponent = getLegacyChosenComponent(intent);
        }

        String videoId = intent.getStringExtra(EXTRA_VIDEO_ID);
        if (chosenComponent != null) {
            Log.d(TAG, "시스템 공유 대상 선택 성공: package=" + chosenComponent.getPackageName()
                    + ", videoId=" + (videoId != null ? videoId : ""));
        } else {
            Log.w(TAG, "시스템 공유 대상 정보를 읽지 못함");
        }
    }

    @SuppressWarnings("deprecation")
    private ComponentName getLegacyChosenComponent(Intent intent) {
        return intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT);
    }
}
