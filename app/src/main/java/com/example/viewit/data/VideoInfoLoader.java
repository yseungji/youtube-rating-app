package com.example.viewit.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.example.viewit.BuildConfig;
import com.example.viewit.data.model.Video;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

// 평가 저장 테스트에 필요한 영상 정보 조회. 반드시 백그라운드 스레드에서 호출한다.
// 영상 정보나 집계값을 Firestore에 쓰지는 않는다.
public final class VideoInfoLoader {
    public static final class Result {
        public final Video video;
        public final Bitmap thumbnail;

        Result(Video video, Bitmap thumbnail) {
            this.video = video;
            this.thumbnail = thumbnail;
        }
    }

    public Result load(String videoId) throws IOException, JSONException {
        if (BuildConfig.YOUTUBE_API_KEY.trim().isEmpty()) {
            throw new IllegalStateException("YouTube API key missing");
        }
        Uri uri = Uri.parse("https://www.googleapis.com/youtube/v3/videos").buildUpon()
                .appendQueryParameter("part", "snippet")
                .appendQueryParameter("id", videoId)
                .appendQueryParameter("fields",
                        "items(id,snippet(title,channelId,channelTitle,categoryId,thumbnails))")
                .appendQueryParameter("key", BuildConfig.YOUTUBE_API_KEY)
                .build();
        HttpsURLConnection connection = (HttpsURLConnection) new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        JSONObject response;
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                // API 키가 포함된 URL이나 응답 본문을 로그·오류 메시지에 남기지 않는다.
                throw new IOException("YouTube API HTTP " + status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                response = new JSONObject(json.toString());
            }
        } finally {
            connection.disconnect();
        }

        JSONArray items = response.getJSONArray("items");
        if (items.length() == 0) {
            throw new IOException("Video unavailable");
        }
        JSONObject item = items.getJSONObject(0);
        JSONObject snippet = item.getJSONObject("snippet");
        Video video = new Video();
        video.setVideoId(item.getString("id"));
        video.setTitle(snippet.getString("title"));
        video.setChannelId(snippet.getString("channelId"));
        video.setChannelTitle(snippet.getString("channelTitle"));
        video.setCategoryId(snippet.getString("categoryId"));
        video.setThumbnailUrl(snippet.getJSONObject("thumbnails")
                .getJSONObject("medium").getString("url"));

        Bitmap thumbnail = null;
        HttpsURLConnection imageConnection = null;
        try {
            imageConnection = (HttpsURLConnection) new URL(video.getThumbnailUrl()).openConnection();
            imageConnection.setConnectTimeout(10000);
            imageConnection.setReadTimeout(10000);
            try (InputStream stream = imageConnection.getInputStream()) {
                thumbnail = BitmapFactory.decodeStream(stream);
            }
        } catch (IOException ignored) {
            // 썸네일 실패가 제목·카테고리 조회와 평가 저장을 막지는 않는다.
        } finally {
            if (imageConnection != null) {
                imageConnection.disconnect();
            }
        }
        return new Result(video, thumbnail);
    }
}
