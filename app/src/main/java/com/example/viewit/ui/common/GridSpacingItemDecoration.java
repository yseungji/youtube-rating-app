package com.example.viewit.ui.common;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

// 격자(GridLayoutManager) 칸 사이에만 간격을 넣는다. 화면 바깥 테두리에는 간격을 넣지 않는다.
// 예: Figma 프로필 "Rated video grid"는 3열, 칸 사이 2px → 128.67 × 3 + 2 × 2 = 390
// 사용: recyclerView.addItemDecoration(new GridSpacingItemDecoration(3, 간격px));
public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int spanCount;   // 한 줄에 놓는 칸 수 (GridLayoutManager와 같은 값)
    private final int spacingPx;   // 칸 사이 간격. 코드에서 크기는 px 단위

    public GridSpacingItemDecoration(int spanCount, int spacingPx) {
        this.spanCount = spanCount;
        this.spacingPx = spacingPx;
    }

    // RecyclerView가 칸마다 호출한다. outRect에 칸 둘레의 여백(left/top/right/bottom)을 채워 준다.
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return;   // 삭제 애니메이션 중인 칸 등
        }
        int column = position % spanCount;   // 0, 1, 2 중 몇 번째 열인지

        // 가로: 칸 너비가 모두 같아지도록 간격을 열마다 나눠서 준다.
        // 3열·간격 s라면 왼쪽 칸 (0, 2s/3), 가운데 (s/3, s/3), 오른쪽 (2s/3, 0)
        // → 칸 사이는 항상 s, 바깥쪽 테두리는 0
        outRect.left = column * spacingPx / spanCount;
        outRect.right = spacingPx - (column + 1) * spacingPx / spanCount;

        // 세로: 첫 줄을 뺀 나머지 줄의 위쪽에만 간격
        outRect.top = position >= spanCount ? spacingPx : 0;
    }
}
