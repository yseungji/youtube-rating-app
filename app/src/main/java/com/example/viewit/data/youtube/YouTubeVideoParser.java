package com.example.viewit.data.youtube;

import com.example.viewit.data.model.Video;
import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

// videos.list 응답(JSON)을 Video 객체 목록으로 바꾼다. 평가 집계값은 건드리지 않는다.
public final class YouTubeVideoParser {

    // 썸네일 크기 우선순위. high(480x360)는 위아래 검은 띠가 있지만 16:9 칸에 centerCrop하면 띠가 잘린다
    private static final String[] THUMBNAIL_SIZES = {"high", "medium", "standard", "default"};

    private YouTubeVideoParser() {
    }

    public static List<Video> parse(JSONObject response, Timestamp fetchedAt) {
        List<Video> videos = new ArrayList<>();
        JSONArray items = response.optJSONArray("items");
        if (items == null) {
            return videos;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                videos.add(parseItem(item, fetchedAt));
            }
        }
        return videos;
    }

    private static Video parseItem(JSONObject item, Timestamp fetchedAt) {
        JSONObject snippet = item.optJSONObject("snippet");
        JSONObject details = item.optJSONObject("contentDetails");

        Video video = new Video();
        video.setVideoId(item.optString("id"));
        if (snippet != null) {
            video.setTitle(snippet.optString("title"));
            video.setChannelId(snippet.optString("channelId"));
            video.setChannelTitle(snippet.optString("channelTitle"));
            video.setCategoryId(snippet.optString("categoryId"));
            video.setThumbnailUrl(thumbnailUrl(snippet.optJSONObject("thumbnails")));
            video.setPublishedAt(timestamp(snippet.optString("publishedAt")));
        }
        if (details != null) {
            video.setDurationSeconds(YouTubeDuration.toSeconds(details.optString("duration")));
        }
        video.setFetchedAt(fetchedAt);
        return video;
    }

    private static String thumbnailUrl(JSONObject thumbnails) {
        if (thumbnails == null) {
            return null;
        }
        for (String size : THUMBNAIL_SIZES) {
            JSONObject thumbnail = thumbnails.optJSONObject(size);
            if (thumbnail != null && !thumbnail.optString("url").isEmpty()) {
                return thumbnail.optString("url");
            }
        }
        return null;
    }

    // "2024-01-02T03:04:05Z" 형식(UTC). 소수점 초가 붙어도 앞 19자리만 읽는다
    private static Timestamp timestamp(String rfc3339) {
        if (rfc3339 == null || rfc3339.length() < 19) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = format.parse(rfc3339.substring(0, 19));
            return date == null ? null : new Timestamp(date);
        } catch (ParseException e) {
            return null;
        }
    }
}
