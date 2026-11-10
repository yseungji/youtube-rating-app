package com.example.viewit.ui.rate;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.paging.PagingConfig;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
import com.example.viewit.data.RatingRepository;
import com.example.viewit.data.model.Rating;
import com.example.viewit.databinding.FragmentRateBinding;
import com.example.viewit.databinding.ItemRatingBinding;
import com.firebase.ui.firestore.paging.FirestorePagingAdapter;
import com.firebase.ui.firestore.paging.FirestorePagingOptions;
import com.google.firebase.firestore.Query;

// FirebaseUI가 공개 평가 Query를 페이지 단위로 읽고 Rating으로 변환한다.
public class RateFragment extends Fragment {
    private static final String TAG = "RateFragment";
    //fragmentrate.xml binding
    private FragmentRateBinding binding;
    private RatingAdapter adapter;
    private boolean loadFailed;

    // ViewBinding으로 XML 화면을 만들고 Fragment에 반환한다.
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Query query = new RatingRepository().publicRatingsQuery();
        // 페이지 크기 10, 다음 페이지 미리 가져오기 5, 빈 자리 표시 안 함.
        PagingConfig config = new PagingConfig(10, 5, false);
        FirestorePagingOptions<Rating> options = new FirestorePagingOptions.Builder<Rating>()
                .setLifecycleOwner(getViewLifecycleOwner())
                .setQuery(query, config, Rating.class)
                .build();

        //Firebase 조회 설정을 Adapter에 전달한다.
        adapter = new RatingAdapter(options);
        //매니저와 어뎁터 연결
        binding.rateVideoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rateVideoList.setAdapter(adapter);
        // FirebaseUI 공식 Java 예제와 같은 람다 콜백. 반환값은 사용되지 않는다.
        adapter.addLoadStateListener(states -> {
            renderLoadState(states);
            return null;
        });

        binding.rateRefresh.setOnClickListener(v -> {
            if (loadFailed) {
                adapter.retry();
            } else {
                adapter.refresh();
            }
        });
    }

    private void renderLoadState(CombinedLoadStates states) {
        if (binding == null || adapter == null) {
            return;
        }
        //로딩 상태 추출
        LoadState refresh = states.getRefresh();
        LoadState append = states.getAppend();
        //에러 발생 여부 확인
        LoadState.Error error = refresh instanceof LoadState.Error
                ? (LoadState.Error) refresh
                : append instanceof LoadState.Error ? (LoadState.Error) append : null;
        loadFailed = error != null;
        //로딩 인디케이터 및 버튼 상태 제어
        boolean loading = refresh instanceof LoadState.Loading || append instanceof LoadState.Loading;
        binding.rateLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.rateRefresh.setEnabled(!loading);
        binding.rateRefresh.setText(loadFailed ? R.string.rate_retry : R.string.rate_refresh);
        //텍스트 안내 메시지 처리 조건문
        if (error != null) {
            Log.e(TAG, "Firestore rating paging failed", error.getError());
            binding.rateStatus.setText(R.string.rate_load_error);
            binding.rateStatus.setVisibility(View.VISIBLE);
        } else if (refresh instanceof LoadState.Loading && adapter.getItemCount() == 0) {
            binding.rateStatus.setText(R.string.rate_loading);
            binding.rateStatus.setVisibility(View.VISIBLE);
        } else if (refresh instanceof LoadState.NotLoading && adapter.getItemCount() == 0) {
            binding.rateStatus.setText(R.string.rate_empty);
            binding.rateStatus.setVisibility(View.VISIBLE);
        } else {
            binding.rateStatus.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        if (adapter != null) {
            adapter.stopListening();
        }
        if (binding != null) {
            binding.rateVideoList.setAdapter(null);
        }
        adapter = null;
        binding = null;
        super.onDestroyView();
    }

    //뷰홀더
    private static class RatingViewHolder extends RecyclerView.ViewHolder {
        private final ItemRatingBinding itemRatingBinding;

        RatingViewHolder(ItemRatingBinding itemRatingBinding) {
            super(itemRatingBinding.getRoot());
            this.itemRatingBinding = itemRatingBinding;
        }

        //bind 함수로 데이터 채우기
        void bind(Rating rating) {
            //영상 ID 표시
            itemRatingBinding.ratingVideo.setText(itemRatingBinding.getRoot().getContext().getString(
                    R.string.rate_video_id_label, rating.getVideoId()));
            //평점 및 작성자 정보 표시
            itemRatingBinding.ratingMetadata.setText(itemRatingBinding.getRoot().getContext().getString(
                    R.string.rate_rating_metadata, rating.getScore(), rating.getUserId()));
            //한줄평 및 예외 처리
            String comment = rating.getComment();
            itemRatingBinding.ratingComment.setText(comment == null || comment.trim().isEmpty()
                    ? itemRatingBinding.getRoot().getContext().getString(R.string.rate_no_comment) : comment);
        }
    }

    //생성자·행 생성·데이터 연결을 한곳에 모은다.
    private static class RatingAdapter extends FirestorePagingAdapter<Rating, RatingViewHolder> {

        public RatingAdapter(@NonNull FirestorePagingOptions<Rating> options) {
            super(options);
        }

        // 한 행의 XML을 만들고 ViewHolder로 감싼다.
        @NonNull
        @Override
        public RatingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemRatingBinding itemRatingBinding = ItemRatingBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new RatingViewHolder(itemRatingBinding);
        }

        // FirebaseUI가 list.get(position) 대신 Rating을 꺼내서 전달한다.
        @Override
        protected void onBindViewHolder(@NonNull RatingViewHolder holder,
                                        int position, @NonNull Rating rating) {
            holder.bind(rating);
        }

        // 목록 데이터와 getItemCount()는 FirebaseUI가 관리하므로 직접 작성하지 않는다.
    }
}
