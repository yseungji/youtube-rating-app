package com.example.viewit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.ItemReviewDetailBinding;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// 서버 평가는 저장/삭제하지 않는다. 많은 행의 스크롤은 기기 안의 테스트 데이터로 검증한다.
@RunWith(AndroidJUnit4.class)
public class VideoDetailScrollTest {
    @Test
    public void detailScrollsThroughReviewsWithVideoFixedAndKeepsInputPanel() {
        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), VideoDetailActivity.class);
        intent.putExtra(VideoDetailActivity.EXTRA_VIDEO_ID, "cUgYiPrtAA0");
        intent.putExtra(VideoDetailActivity.EXTRA_CATEGORY_ID, "22");
        AtomicInteger videoTop = new AtomicInteger();
        AtomicReference<View> inputPanel = new AtomicReference<>();
        try (ActivityScenario<VideoDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.public_rating_list);
                assertTrue(list.getAdapter() instanceof ConcatAdapter);
                ConcatAdapter concat = (ConcatAdapter) list.getAdapter();
                assertEquals(3, concat.getAdapters().size());
                assertTrue(concat.getAdapters().get(1) instanceof VideoDetailActivity.MyAdapter);
                // Firestore 데이터를 늘리지 않고 화면의 행 재사용만 검증한다.
                concat.removeAdapter(concat.getAdapters().get(1));
                concat.addAdapter(1, new ReviewFixtureAdapter());
                videoTop.set(activity.findViewById(R.id.video_thumbnail).getTop());
                View panel = activity.findViewById(R.id.rating_panel);
                assertNotNull(panel);
                inputPanel.set(panel);
            });

            onView(withId(R.id.public_rating_list)).perform(swipeUp());
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.public_rating_list);
                assertTrue("목록의 실제 스크롤 위치가 이동해야 함", list.computeVerticalScrollOffset() > 0);
                assertEquals("영상은 스크롤과 무관하게 고정", videoTop.get(),
                        activity.findViewById(R.id.video_thumbnail).getTop());
                LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
                assertNotNull(manager);
                manager.scrollToPositionWithOffset(25, 0);
            });
            onView(withText("테스트 평가 25")).check(matches(isDisplayed()));
            onView(withId(R.id.video_thumbnail)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.public_rating_list);
                LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
                assertNotNull(manager);
                manager.scrollToPositionWithOffset(0, 0);
            });
            onView(withId(R.id.video_title)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertSame(
                    "입력 패널은 다시 생성하지 않고 같은 화면을 유지", inputPanel.get(),
                    activity.findViewById(R.id.rating_panel)));
        }
    }

    private static class ReviewFixtureAdapter extends RecyclerView.Adapter<VideoDetailActivity.MyViewHolder> {
        @NonNull
        @Override
        public VideoDetailActivity.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemReviewDetailBinding itemBinding = ItemReviewDetailBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VideoDetailActivity.MyViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoDetailActivity.MyViewHolder holder, int position) {
            Rating rating = new Rating();
            rating.setUserId("test-user");
            rating.setScore(5);
            rating.setComment("테스트 평가 " + (position + 1));
            holder.bind(rating);
        }

        @Override
        public int getItemCount() {
            return 25;
        }
    }
}
