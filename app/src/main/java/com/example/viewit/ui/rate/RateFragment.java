package com.example.viewit.ui.rate;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
import com.example.viewit.databinding.ItemRateComponentBinding;
import com.example.viewit.databinding.FragmentRateBinding;

import java.util.ArrayList;
import java.util.List;

// DevRateActivity에서 작성한 RecyclerView 예제를 평가하기 탭에 연결한다.
public class RateFragment extends Fragment {

    public RateFragment() {
        super(R.layout.fragment_rate);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 생성자에서 지정한 XML로 만들어진 화면의 바인딩을 가져온다.
        FragmentRateBinding binding = FragmentRateBinding.bind(view);

        // 서버 연결 전, 기존 예시 영상 한 건으로 목록 표시를 확인한다.
        List<String> list = new ArrayList<>();
        list.add(getString(R.string.rate_sample_video_title));

        binding.rateVideoList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rateVideoList.setAdapter(new MyAdapter(list));
    }

    private class MyViewHolder extends RecyclerView.ViewHolder {

        private ItemRateComponentBinding rateBinding;

        public MyViewHolder(@NonNull ItemRateComponentBinding rateBinding) {
            super(rateBinding.getRoot());
            this.rateBinding = rateBinding;
        }

        public void bind(String text) {
            rateBinding.rateVideoTitle.setText(text);
            rateBinding.rateVideoMetadata.setText(R.string.rate_sample_video_metadata);
            rateBinding.rateVideoThumbnail.setImageResource(R.drawable.rate_sample_pizza);
        }
    }

    private class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {

        private List<String> list;

        private MyAdapter(List<String> list) {
            this.list = list;
        }

        @Override
        public int getItemViewType(int position) {
            return super.getItemViewType(position);
        }

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemRateComponentBinding rateBinding = ItemRateComponentBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new MyViewHolder(rateBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
            String text = list.get(position);
            holder.bind(text);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }
}
