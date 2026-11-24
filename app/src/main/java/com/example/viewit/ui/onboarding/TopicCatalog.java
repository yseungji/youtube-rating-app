package com.example.viewit.ui.onboarding;

import com.example.viewit.R;

// 온보딩 관심 주제 (3.3, Figma C). YouTube 영상 카테고리 ID와 화면 이름.
// 카테고리 탭(4.3)과 같은 ID를 쓴다. Firestore categories 컬렉션이 채워지면 그쪽으로 바꿀 수 있다.
public final class TopicCatalog {

    private TopicCatalog() {
    }

    public static final String[] IDS = {
        "1", "10", "15", "17", "19", "20", "22", "23", "24", "26", "27", "28", "2", "25"
    };

    public static final int[] NAMES = {
        R.string.category_film,          // 1  영화·애니메이션
        R.string.category_music,         // 10 음악
        R.string.category_pets,          // 15 동물
        R.string.category_sports,        // 17 스포츠
        R.string.topic_travel,           // 19 여행·이벤트
        R.string.category_game,          // 20 게임
        R.string.topic_people,           // 22 인물·블로그
        R.string.category_comedy,        // 23 코미디
        R.string.category_entertainment, // 24 엔터테인먼트
        R.string.category_howto,         // 26 노하우·스타일
        R.string.topic_education,        // 27 교육
        R.string.category_science,       // 28 과학기술
        R.string.category_autos,         // 2  자동차
        R.string.category_news           // 25 뉴스·정치
    };

    public static final int MIN_SELECTION = 3;
}
