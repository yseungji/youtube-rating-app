package com.example.viewit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

// 스크롤 문제 회귀 검증. 데이터 저장/수정/삭제 없이 하단 UI와 전체 보기 이동만 확인한다.
@RunWith(AndroidJUnit4.class)
public class VideoDetailScrollTest {
    @Test
    public void swipeReachesPublicRatingsAndOpensFullList() {
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), VideoDetailActivity.class);
        intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, "cUgYiPrtAA0");
        intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, "22");
        try (ActivityScenario<VideoDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                NestedScrollView scroll = activity.findViewById(R.id.video_detail_scroll);
                assertTrue("하단 콘텐츠가 스크롤 영역보다 길어야 함", scroll.canScrollVertically(1));
            });

            onView(withId(R.id.video_detail_scroll)).perform(swipeUp());
            scenario.onActivity(activity -> {
                View scroll = activity.findViewById(R.id.video_detail_scroll);
                assertTrue("손가락 스와이프 후 실제 scrollY가 증가해야 함", scroll.getScrollY() > 0);
            });
            onView(withId(R.id.video_detail_scroll)).perform(swipeUp());
            onView(withId(R.id.video_detail_scroll)).perform(swipeUp());
            onView(withId(R.id.public_rating_all)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.reviews_list)).check(matches(isDisplayed()));
        }
    }
}
