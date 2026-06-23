package com.jiexi.apppp.api;

import com.jiexi.apppp.util.HttpUtil;
import com.jiexi.apppp.util.Logger;

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
     * Parse Douyin video info from share URL.
     * Uses multiple strategies to find the video ID.
     */
    public static VideoInfo parseVideo(String shareUrl) throws Exception {
        Logger.i("DouyinApi", "解析: " + shareUrl);

        // Strategy 1: Resolve redirect with full mobile headers
        String realUrl = resolveDouyinRedirect(shareUrl);
        Logger.i("DouyinApi", "重定向结果: " + realUrl);

        String videoId = extractVideoId(realUrl);

        // Strategy 2: If redirect gave bare domain, try direct API with share URL
        if (videoId == null && shareUrl.contains("v.douyin.com/")) {
            videoId = resolveViaShareApi(shareUrl);
        }

        if (videoId == null) {
            throw new Exception("无法解析抖音视频ID (URL=" + realUrl + ")");
        }

        Logger.i("DouyinApi", "videoId=" + videoId);
        return fetchVideoInfo(videoId);
    }

    /**
     * Resolve v.douyin.com short URL with anti-spider headers.
     */
    private static String resolveDouyinRedirect(String urlStr) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(urlStr);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("Referer", "https://www.douyin.com/");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.connect();
            return conn.getURL().toString();
        } catch (Exception e) {
            Logger.e("DouyinApi", "重定向失败", e);
            return urlStr;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Try to get video ID via iesdouyin share API using the short link code.
     */
    private static String resolveViaShareApi(String shareUrl) {
        try {
            // The short link path segment (after v.douyin.com/) can be used
            // to query the share info API
            int idx = shareUrl.indexOf("v.douyin.com/");
            if (idx < 0) return null;
            String code = shareUrl.substring(idx + "v.douyin.com/".length())
                    .replaceAll("/.*", "");
            if (code.length() < 4) return null;

            // Use the share info endpoint
            String apiUrl = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/"
                    + "?item_ids=";
            // Try the share code as item_id first
            Map<String, String> headers = createDouyinHeaders();
            headers.put("Accept-Language", "zh-CN,zh;q=0.9");
            headers.put("X-Requested-With", "XMLHttpRequest");

            // The item_ids API needs actual video IDs, not share codes.
            // Use a different approach: fetch the share page HTML and parse.
            String sharePageUrl = "https://www.iesdouyin.com/share/video/" + code;
            String html = HttpUtil.get(sharePageUrl, headers);
            Logger.i("DouyinApi", "分享页长度=" + (html != null ? html.length() : 0));

            // Try to extract video ID from page HTML
            String vid = extractVideoIdFromHtml(html);
            if (vid != null) return vid;

            // Try modal_id pattern
            vid = extractBetween(html, "modal_id=", "&");
            if (vid != null) return vid;

        } catch (Exception e) {
            Logger.e("DouyinApi", "分享API解析失败", e);
        }
        return null;
    }

    private static String extractVideoIdFromHtml(String html) {
        if (html == null) return null;
        // Common patterns in Douyin share pages:
        String[] patterns = {
                "\"aweme_id\":\"", "\"item_id\":\"",
                "/video/", "video_id\":\""
        };
        for (int i = 0; i < patterns.length; i++) {
            String pat = patterns[i];
            int idx = html.indexOf(pat);
            if (idx >= 0) {
                int start = idx + pat.length();
                StringBuilder sb = new StringBuilder();
                for (int j = start; j < html.length() && j < start + 30; j++) {
                    char c = html.charAt(j);
                    if (c == '"' || c == '&' || c == '?' || c == '/'
                            || c == '\\' || c == '\'') break;
                    sb.append(c);
                }
                String id = sb.toString().trim();
                if (id.length() > 5 && id.length() < 30
                        && id.matches("[0-9]+")) {
                    return id;
                }
            }
        }
        return null;
    }

    private static String extractBetween(String text, String start, String end) {
        if (text == null) return null;
        int idx = text.indexOf(start);
        if (idx < 0) return null;
        int s = idx + start.length();
        int e = text.indexOf(end, s);
        if (e < 0) e = Math.min(s + 30, text.length());
        String id = text.substring(s, e).trim();
        if (id.length() > 5 && id.length() < 30) return id;
        return null;
    }

    private static String extractVideoId(String url) {
        if (url == null) return null;
        String[] patterns = {"/video/", "/note/", "modal_id=", "video/"};
        for (int p = 0; p < patterns.length; p++) {
            String pat = patterns[p];
            int idx = url.indexOf(pat);
            if (idx >= 0) {
                String rest = url.substring(idx + pat.length());
                int end = rest.length();
                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (!Character.isDigit(c)) { end = i; break; }
                }
                String id = rest.substring(0, end);
                if (id.length() > 5 && id.length() < 30) return id;
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

        JSONObject author = item.getJSONObject("author");
        info.authorName = author.optString("nickname", "");
        info.authorAvatar = author.getJSONObject("avatar_thumb").getJSONArray("url_list")
                .getString(0);

        JSONObject cover = item.getJSONObject("video").getJSONObject("cover");
        info.coverUrl = cover.getJSONArray("url_list").getString(0);

        info.durationSeconds = item.getJSONObject("video").getLong("duration") / 1000;

        JSONObject stats = item.getJSONObject("statistics");
        info.viewCount = stats.optLong("play_count", 0);
        info.likeCount = stats.optLong("digg_count", 0);

        JSONObject videoInfo = item.getJSONObject("video");
        JSONObject playAddr = videoInfo.getJSONObject("play_addr");
        String videoUrl = playAddr.getJSONArray("url_list").getString(0);
        videoUrl = videoUrl.replace("playwm", "play")
                .replace("watermark=1", "watermark=0");

        VideoInfo.QualityOption opt = new VideoInfo.QualityOption();
        opt.isAudio = false;
        opt.qualityCode = 0;
        opt.qualityName = "原画";
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
