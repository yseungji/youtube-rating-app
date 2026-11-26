package com.example.viewit.ui.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.databinding.ItemSearchHistoryBinding;

import java.util.List;

// 최근 검색어 한 줄(item_search_history.xml)을 맡는 ViewHolder
class SearchHistoryViewHolder extends RecyclerView.ViewHolder {

    private final ItemSearchHistoryBinding itemBinding;

    SearchHistoryViewHolder(@NonNull ItemSearchHistoryBinding itemBinding) {
        super(itemBinding.getRoot());
        this.itemBinding = itemBinding;
    }

    void bind(String query, SearchHistoryAdapter.Listener listener) {
        itemBinding.searchHistoryText.setText(query);
        itemBinding.getRoot().setOnClickListener(v -> listener.onHistoryClick(query));
        itemBinding.searchHistoryDelete.setOnClickListener(v -> listener.onHistoryDelete(query));
    }
}

// 최근 검색어 목록 어댑터. 누르기·지우기는 Listener로 화면(SearchFragment)에 알린다
public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryViewHolder> {

    public interface Listener {
        // 검색어를 눌렀을 때 → 그 검색어로 다시 검색
        void onHistoryClick(String query);

        // X를 눌렀을 때 → 그 검색어만 지우기
        void onHistoryDelete(String query);
    }

    private final List<String> queries;
    private final Listener listener;

    public SearchHistoryAdapter(List<String> queries, Listener listener) {
        this.queries = queries;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SearchHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchHistoryBinding itemBinding = ItemSearchHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SearchHistoryViewHolder(itemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchHistoryViewHolder holder, int position) {
        holder.bind(queries.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return queries.size();
    }
}
