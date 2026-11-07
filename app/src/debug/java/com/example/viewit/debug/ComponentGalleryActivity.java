package com.example.viewit.debug;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.R;
import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.ActivityComponentGalleryBinding;
import com.example.viewit.databinding.ItemRatingTileBinding;
import com.example.viewit.databinding.ViewAuthorBinding;
import com.example.viewit.ui.common.DurationFormat;
import com.example.viewit.ui.common.RatingFormat;
import com.example.viewit.ui.common.TimeAgoFormat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// 공통 컴포넌트를 한 화면에서 확인하는 디버그 전용 화면. 출시용 빌드에는 포함되지 않는다.
// 아래 예시 데이터를 넣는 코드는 각 화면에서 컴포넌트를 채우는 방법의 예시이기도 하다.
public class ComponentGalleryActivity extends AppCompatActivity {

    private static final long MINUTE = 60_000L;

    private ActivityComponentGalleryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityComponentGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        showVideoComponents();
        showPeopleComponents();
        showRatingComponents();
    }

    private void showVideoComponents() {
        String title = getString(R.string.sample_video_title);
        String channel = getString(R.string.sample_channel);

        binding.galleryThumbnail.thumbnailDuration.setText(DurationFormat.format(750));

        binding.galleryVideoRow.videoRowTitle.setText(title);
        binding.galleryVideoRow.videoRowMeta.setText(
                getString(R.string.video_meta, channel, getString(R.string.sample_duration_text)));

        binding.galleryCategoryVideo.categoryVideoTitle.setText(title);
        binding.galleryCategoryVideo.categoryVideoRating.setText(
                getString(R.string.video_average, RatingFormat.averageText(4.0, 3)));

        setTile(binding.galleryTile1, 4);
        setTile(binding.galleryTile2, 5);
        setTile(binding.galleryTile3, 3);
    }

    private void setTile(ItemRatingTileBinding tile, int score) {
        tile.ratingTileScore.setText(String.valueOf(score));
    }

    private void showPeopleComponents() {
        Date now = new Date();
        setAuthor(binding.galleryAuthor, getString(R.string.sample_user_seungji), "seungji", now, now);

        binding.galleryReviewActions.reviewActionLike.setText(getString(R.string.review_like_count, 2));

        binding.galleryReviewQuote.reviewQuoteTitle.setText(
                getString(R.string.review_quote_title, getString(R.string.sample_user_seungji)));
        binding.galleryReviewQuote.reviewQuoteRating.setText(ratingText(4));
        binding.galleryReviewQuote.reviewQuoteComment.setText(R.string.sample_comment_4);

        setAuthor(binding.galleryReviewDetail.reviewAuthor, getString(R.string.sample_user_juwon), "juwon",
                new Date(now.getTime() - 5 * MINUTE), now);
        binding.galleryReviewDetail.reviewRating.setText(ratingText(3));
        binding.galleryReviewDetail.reviewComment.setText(R.string.sample_comment_3);
        binding.galleryReviewDetail.reviewActions.reviewActionLike.setText(getString(R.string.review_like_count, 2));

        binding.galleryUserResult.userResultAvatar.setText(initial(getString(R.string.sample_user_seungji)));
        binding.galleryUserResult.userResultName.setText(R.string.sample_user_seungji);
        binding.galleryUserResult.userResultMeta.setText(getString(R.string.user_public_rating_count, "seungji", 1));

        binding.galleryFollowRow.followRowAvatar.setText(initial(getString(R.string.sample_user_minseo)));
        binding.galleryFollowRow.followRowName.setText(R.string.sample_user_minseo);
        binding.galleryFollowRow.followRowMeta.setText(getString(R.string.user_rating_count, "minseo", 12));
        binding.galleryFollowRow.followRowNewCount.setText(getString(R.string.user_new_rating_count, 1));

        binding.galleryLibraryRow.libraryRowLabel.setText(R.string.sample_library);
        binding.gallerySearchHistory.searchHistoryText.setText(R.string.sample_search);
    }

    private void setAuthor(ViewAuthorBinding author, String name, String username, Date written, Date now) {
        author.authorAvatar.setText(initial(name));
        author.authorName.setText(name);
        author.authorMeta.setText(getString(R.string.author_meta, username, TimeAgoFormat.format(this, written, now)));
    }

    private void showRatingComponents() {
        binding.galleryRatingPanel.setScore(4);
        binding.galleryRatingPanel.setOnSaveClickListener(v -> Toast.makeText(this,
                binding.galleryRatingPanel.getScore() + "점 · " + binding.galleryRatingPanel.getRatingVisibility(),
                Toast.LENGTH_SHORT).show());

        binding.galleryRatingPanelSaved.setScore(4);
        binding.galleryRatingPanelSaved.setRatingVisibility(Rating.VISIBILITY_PRIVATE);
        binding.galleryRatingPanelSaved.setSaved(true);

        Map<String, Long> distribution = new HashMap<>();
        distribution.put("s5", 1L);
        distribution.put("s4", 1L);
        distribution.put("s3", 1L);
        binding.galleryRatingStats.setStats(4.0, 3, distribution);
    }

    private String ratingText(int score) {
        return RatingFormat.stars(score) + "  " + getString(R.string.rating_score, score);
    }

    private static String initial(String name) {
        return name.isEmpty() ? "" : name.substring(0, 1);
    }
}
