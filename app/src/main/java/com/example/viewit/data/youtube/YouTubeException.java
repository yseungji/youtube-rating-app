package com.example.viewit.data.youtube;

// YouTube 호출이 실패한 이유. 화면에서는 getReason()으로 나눠 안내 문구를 고른다.
public class YouTubeException extends Exception {

    public enum Reason {
        MISSING_API_KEY,   // local.properties에 YOUTUBE_API_KEY가 없음
        KEY_REJECTED,      // 키가 틀렸거나, 프로젝트에서 YouTube Data API v3가 꺼져 있거나, 키 제한에 걸림
        QUOTA_EXCEEDED,    // 하루 사용량(기본 10,000유닛)을 다 씀
        NOT_FOUND,         // 없는 영상이거나 비공개·삭제된 영상
        NETWORK,           // 인터넷 연결 문제
        UNKNOWN            // 그 밖의 서버 응답·형식 오류
    }

    private final Reason reason;

    public YouTubeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public YouTubeException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
