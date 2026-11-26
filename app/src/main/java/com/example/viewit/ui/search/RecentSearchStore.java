package com.example.viewit.ui.search;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

// 최근 검색어를 기기에 저장한다 (4.4). 앱을 지웠다 깔거나 다른 기기에서는 보이지 않는다.
// 최근 것이 맨 앞, 같은 검색어는 한 번만, 최대 10개.
// SharedPreferences에는 목록을 그대로 넣을 수 없어서, 검색어들을 줄바꿈으로 이은 문자열 하나로 저장한다
// (join / split 도우미 사용. 검색어에는 줄바꿈이 들어가지 않는다)
public class RecentSearchStore {

    private static final String PREFS_NAME = "recent_search";
    private static final String KEY_QUERIES = "queries";
    private static final int MAX_COUNT = 10;

    private SharedPreferences prefs;

    public RecentSearchStore(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // 저장된 최근 검색어 (최근 것이 맨 앞). 없으면 빈 목록
    @NonNull
    public List<String> load() {
        return split(prefs.getString(KEY_QUERIES, ""));
    }

    // 검색어를 맨 앞에 추가한다. 이미 있던 같은 검색어는 지우고, 10개를 넘으면 오래된 것을 버린다
    public void add(@NonNull String query) {
        List<String> queries = load();
        queries.remove(query);
        queries.add(0, query);
        while (queries.size() > MAX_COUNT) {
            queries.remove(queries.size() - 1);
        }
        prefs.edit().putString(KEY_QUERIES, join(queries)).apply();
    }

    // 검색어 하나를 지운다 (최근 검색어 X 버튼)
    public void remove(@NonNull String query) {
        List<String> queries = load();
        queries.remove(query);
        prefs.edit().putString(KEY_QUERIES, join(queries)).apply();
    }

    // 모두 지운다 ("전체 삭제")
    public void clear() {
        prefs.edit().remove(KEY_QUERIES).apply();
    }

    // ---------- 도우미 ----------

    // ["나폴리 피자", "승지"] → "나폴리 피자\n승지"
    private static String join(List<String> queries) {
        return String.join("\n", queries);
    }

    // "나폴리 피자\n승지" → ["나폴리 피자", "승지"]. 빈 문자열이면 빈 목록
    private static List<String> split(String saved) {
        List<String> queries = new ArrayList<>();
        if (saved == null || saved.isEmpty()) {
            return queries;
        }
        for (String query : saved.split("\n")) {
            if (!query.isEmpty()) {
                queries.add(query);
            }
        }
        return queries;
    }
}
