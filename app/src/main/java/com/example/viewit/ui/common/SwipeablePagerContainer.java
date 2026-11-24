package com.example.viewit.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

// 메인 ViewPager2 안에 가로 스크롤 뷰(카테고리 칩 HorizontalScrollView, 프로필 내부 ViewPager2)가 있을 때
// 안쪽 뷰가 먼저 스크롤되고, 끝에 도달했을 때만 바깥 5탭 ViewPager2로 넘어가도록 터치를 조율하는 컨테이너.
public class SwipeablePagerContainer extends FrameLayout {
    private final int touchSlop;
    private final int[] locationBuffer = new int[2];

    private float downX;
    private float downY;
    private View nestedHorizontalTarget;
    private boolean gestureDecided;
    private boolean disabledForChild;

    public SwipeablePagerContainer(@NonNull Context context) {
        this(context, null);
    }

    public SwipeablePagerContainer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeablePagerContainer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        ViewPager2 pager = findMainPager();
        if (pager == null) {
            return super.dispatchTouchEvent(ev);
        }

        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            gestureDecided = false;
            if (disabledForChild) {
                pager.setUserInputEnabled(true);
                disabledForChild = false;
            }
            View pagerRecycler = pager.getChildCount() > 0 ? pager.getChildAt(0) : null;
            nestedHorizontalTarget = findHorizontallyScrollableChild(
                    pager, ev.getRawX(), ev.getRawY(), pager, pagerRecycler);
            return super.dispatchTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (!gestureDecided && nestedHorizontalTarget != null) {
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                float decisionSlop = touchSlop * 0.5f;
                if (absDx > decisionSlop || absDy > decisionSlop) {
                    gestureDecided = true;
                    if (absDx > absDy) {
                        int direction = dx > 0 ? -1 : 1;
                        if (nestedHorizontalTarget.canScrollHorizontally(direction)) {
                            pager.setUserInputEnabled(false);
                            disabledForChild = true;
                        }
                    }
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean handled = super.dispatchTouchEvent(ev);
            if (disabledForChild) {
                pager.setUserInputEnabled(true);
                disabledForChild = false;
            }
            nestedHorizontalTarget = null;
            gestureDecided = false;
            return handled;
        }

        return super.dispatchTouchEvent(ev);
    }

    @Nullable
    private ViewPager2 findMainPager() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ViewPager2) {
                return (ViewPager2) child;
            }
        }
        return null;
    }

    @Nullable
    private View findHorizontallyScrollableChild(View view, float rawX, float rawY,
                                                 View pager, View pagerInternalRecycler) {
        if (view.getVisibility() != View.VISIBLE || !isPointInsideView(view, rawX, rawY)) {
            return null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = findHorizontallyScrollableChild(
                        group.getChildAt(i), rawX, rawY, pager, pagerInternalRecycler);
                if (found != null) {
                    return found;
                }
            }
        }
        if (view != pager && view != pagerInternalRecycler
                && (view.canScrollHorizontally(-1) || view.canScrollHorizontally(1))) {
            return view;
        }
        return null;
    }

    private boolean isPointInsideView(View view, float rawX, float rawY) {
        view.getLocationOnScreen(locationBuffer);
        int left = locationBuffer[0];
        int top = locationBuffer[1];
        return rawX >= left
                && rawX <= left + view.getWidth()
                && rawY >= top
                && rawY <= top + view.getHeight();
    }
}
