package com.example.viewit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

// 확인창 UI만 검사한다. 실제 삭제 확인 버튼은 누르지 않으므로 서버 평가는 바뀌지 않는다.
@RunWith(AndroidJUnit4.class)
public class RatingDeleteDialogTest {
    private ActivityScenario<VideoDetailActivity> launchDetail() {
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), VideoDetailActivity.class);
        intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, "cUgYiPrtAA0");
        intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, "22");
        return ActivityScenario.launch(intent);
    }

    private void openDialog(ActivityScenario<VideoDetailActivity> scenario) {
        // 계정에 저장된 평가 유무에 의존하지 않도록 이미 연결된 클릭 리스너만 호출한다.
        // 새 평가를 만들거나 서버 문서를 삭제하지 않는다.
        scenario.onActivity(activity -> activity.findViewById(R.id.my_rating_delete).performClick());
    }

    @Test
    public void dialogIsCenteredAndCancelLeavesDetailOpen() {
        try (ActivityScenario<VideoDetailActivity> scenario = launchDetail()) {
            openDialog(scenario);
            // 연속 클릭해도 동일 창만 유지되어야 한다.
            openDialog(scenario);
            onView(withText(R.string.video_detail_delete_dialog_title))
                    .inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(R.id.rating_delete_confirm))
                    .inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(R.id.rating_delete_dialog)).inRoot(isDialog()).check((view, error) -> {
                if (error != null) {
                    throw error;
                }
                WindowManager.LayoutParams params =
                        (WindowManager.LayoutParams) view.getRootView().getLayoutParams();
                assertEquals(Gravity.CENTER, params.gravity & Gravity.CENTER);
                assertTrue("확인창이 충분한 너비로 표시되어야 함", view.getWidth() > 100);
            });
            onView(withId(R.id.rating_delete_cancel)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.rating_delete_dialog)).check(doesNotExist());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    @Test
    public void systemBackDismissesOnlyTheDialogAndAllowsReopening() {
        try (ActivityScenario<VideoDetailActivity> scenario = launchDetail()) {
            openDialog(scenario);
            pressBack();
            onView(withId(R.id.rating_delete_dialog)).check(doesNotExist());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
            openDialog(scenario);
            onView(withId(R.id.rating_delete_dialog)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(R.id.rating_delete_cancel)).inRoot(isDialog()).perform(click());
        }
    }
}
