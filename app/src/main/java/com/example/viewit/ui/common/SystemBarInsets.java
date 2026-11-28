package com.example.viewit.ui.common;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

// 상태바(화면 맨 위 시계·배터리 줄) 관련 공통 처리.
// MainActivity는 위쪽 여백을 직접 주지 않고, 탭 화면마다 padTop으로 상태바 높이만큼 띄운다.
// 프로필은 배너를 상태바 뒤까지 깔아야 해서 padTop을 쓰지 않고 ProfileFragment가 직접 처리한다.
public final class SystemBarInsets {

    private SystemBarInsets() {
    }

    // view의 원래 위쪽 패딩 + 상태바 높이. ViewPager2 안처럼 나중에 붙는 화면도 붙는 순간 다시 계산한다.
    public static void padTop(@NonNull View view) {
        final int originalTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), originalTop + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        requestWhenAttached(view);
    }

    // 붙어 있으면 바로, 아니면 붙는 순간 insets를 다시 받는다
    public static void requestWhenAttached(@NonNull View view) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
            return;
        }
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                v.removeOnAttachStateChangeListener(this);
                ViewCompat.requestApplyInsets(v);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
            }
        });
    }

    // 상태바 아이콘 색. darkBackground가 true면 흰 아이콘, false면 검정 아이콘(앱 기본)
    public static void setDarkStatusBarBackground(@NonNull Activity activity, boolean darkBackground) {
        WindowInsetsControllerCompat controller =
            WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!darkBackground);
    }
}
