package com.example.viewit.data.youtube;

// YouTube 할당량에 영향을 주는 숫자를 한곳에 모아 둔다 (하루 10,000유닛, 프로젝트 전체 공유).
// 출시 때 할당량을 늘리거나 서버 캐시로 옮기면 여기 숫자만 바꾼다.
//
// 구독 채널 새 영상 한 번 불러오기 ≈ 1(구독 목록) + 1(채널) + 채널 수(최신 영상) + 1(영상 정보) 유닛
// 예: 채널 30개 → 약 33유닛. 같은 결과는 CACHE_MILLIS 동안 메모리에서 다시 쓴다.
public final class YouTubeLimits {

    private YouTubeLimits() {
    }

    // 구독 채널 중 최신 영상을 볼 채널 수 (YouTube "관련성 순" 앞에서부터)
    public static final int SUBSCRIPTION_CHANNELS = 30;

    // 채널마다 가져올 최신 업로드 수
    public static final int VIDEOS_PER_CHANNEL = 3;

    // 구독 채널 탭에 보여 줄 최대 영상 수 (영상 정보 요청 1번 = 50개까지)
    public static final int SUBSCRIPTION_FEED_SIZE = 50;

    // 좋아요 탭에 보여 줄 최대 영상 수 (요청 1번 = 1유닛)
    public static final int LIKED_VIDEOS = 50;

    // 같은 목록을 다시 부르지 않고 메모리 결과를 쓰는 시간 (당겨서 새로고침하면 무시)
    public static final long CACHE_MILLIS = 30 * 60 * 1000L;
}
