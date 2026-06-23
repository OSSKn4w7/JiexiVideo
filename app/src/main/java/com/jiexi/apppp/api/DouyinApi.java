package com.jiexi.apppp.api;

import com.jiexi.apppp.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class DouyinApi {

    /**
     * Check if URL is a Douyin/TikTok share link
     */
    public static boolean isDouyinUrl(String url) {
        if (url == null) return false;
        return url.contains("douyin.com") || url.contains("iesdouyin.com")
                || url.contains("tiktok.com");
    }

    /**
     * Parse Douyin video info from share URL
     */
    public static VideoInfo parseVideo(String shareUrl) throws Exception {
        // Follow redirects to get the real video page URL
        String realUrl = HttpUtil.resolveRedirectUrl(shareUrl);
        // Extract video id
        String videoId = extractVideoId(realUrl);
        if (videoId == null) {
            throw new Exception("无法解析抖音视频ID");
        }

        return fetchVideoInfo(videoId);
    }

    private static String extractVideoId(String url) {
        if (url == null) return null;

        // Pattern: /video/123456... or /note/... or modal_id=...
        String[] patterns = {"/video/", "/note/", "modal_id=", "video/"};

        for (int p = 0; p < patterns.length; p++) {
            String pat = patterns[p];
            int idx = url.indexOf(pat);
            if (idx >= 0) {
                String rest = url.substring(idx + pat.length());
                int end = rest.length();
                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (!Character.isDigit(c)) {
                        end = i;
                        break;
                    }
                }
                String id = rest.substring(0, end);
                if (id.length() > 5 && id.length() < 30) {
                    return id;
                }
            }
        }
        return null;
    }

    private static VideoInfo fetchVideoInfo(String videoId) throws Exception {
        String url = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=" + videoId;
        Map<String, String> headers = createDouyinHeaders();
        String resp = HttpUtil.get(url, headers);

        JSONObject json = new JSONObject(resp);
        JSONArray items = json.getJSONArray("item_list");
        if (items.length() == 0) {
            throw new Exception("未找到该抖音视频");
        }

        JSONObject item = items.getJSONObject(0);
        VideoInfo info = new VideoInfo();
        info.platform = VideoInfo.PLATFORM_DOUYIN;
        info.aid = videoId;
        info.title = item.optString("desc", "");
        info.description = item.optString("desc", "");

        // Author
        JSONObject author = item.getJSONObject("author");
        info.authorName = author.optString("nickname", "");
        info.authorAvatar = author.getJSONObject("avatar_thumb").getJSONArray("url_list")
                .getString(0);

        // Cover
        JSONObject cover = item.getJSONObject("video").getJSONObject("cover");
        info.coverUrl = cover.getJSONArray("url_list").getString(0);

        // Duration in milliseconds
        info.durationSeconds = item.getJSONObject("video").getLong("duration") / 1000;

        // Stats
        JSONObject stats = item.getJSONObject("statistics");
        info.viewCount = stats.optLong("play_count", 0);
        info.likeCount = stats.optLong("digg_count", 0);

        // Video play URL
        JSONObject videoInfo = item.getJSONObject("video");
        JSONObject playAddr = videoInfo.getJSONObject("play_addr");
        String videoUrl = playAddr.getJSONArray("url_list").getString(0);
        // Replace watermark URL with non-watermark
        videoUrl = videoUrl.replace("playwm", "play")
                .replace("watermark=1", "watermark=0");

        VideoInfo.QualityOption opt = new VideoInfo.QualityOption();
        opt.isAudio = false;
        opt.qualityCode = 0;
        opt.qualityName = "默认画质";
        opt.format = "mp4";
        opt.url = videoUrl;
        opt.fileSize = 0;
        opt.needsAuth = false;
        info.videoQualities.add(opt);

        return info;
    }

    private static Map<String, String> createDouyinHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("User-Agent",
                "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
        headers.put("Referer", "https://www.douyin.com/");
        return headers;
    }
}
