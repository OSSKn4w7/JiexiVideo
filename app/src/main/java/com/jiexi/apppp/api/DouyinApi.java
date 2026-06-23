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
     * Resolve v.douyin.com short URL by reading response HTML for redirect target.
     */
    private static String resolveDouyinRedirect(String urlStr) {
        java.net.HttpURLConnection conn = null;
        try {
            // Strategy A: fetch without redirect to get the landing URL in HTML
            java.net.URL url = new java.net.URL(urlStr);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.connect();
            int code = conn.getResponseCode();

            // Follow HTTP redirects (with depth limit)
            if (code == 301 || code == 302) {
                String loc = conn.getHeaderField("Location");
                if (loc != null && !loc.equals(urlStr)) {
                    conn.disconnect();
                    conn = null;
                    return resolveDouyinRedirect(loc);
                }
            }

            // Read response body and search for video URL patterns
            java.io.InputStream is = conn.getInputStream();
            java.io.ByteArrayOutputStream baos =
                    new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            is.close();
            String body = baos.toString("UTF-8");
            Logger.i("DouyinApi", "短链接响应长度=" + body.length());
            Logger.i("DouyinApi", "响应前300: " + body.substring(0,
                    Math.min(300, body.length())));

            // Search for douyin.com/video/ URL in HTML
            String vidUrl = extractFirst(body,
                    "https://www.douyin.com/video/",
                    "\"");
            if (vidUrl != null) return "https://www.douyin.com/video/" + vidUrl;

            vidUrl = extractFirst(body, "douyin.com/video/", "\"");
            if (vidUrl != null) return "https://www.douyin.com/video/" + vidUrl;

            vidUrl = extractFirst(body, "modal_id=", "\"");
            if (vidUrl != null) return "https://www.douyin.com/video/" + vidUrl;

            // Search for 19-digit video ID in entire body
            int vidStart = findLongDigit(body, 15);
            if (vidStart >= 0) {
                String around = body.substring(Math.max(0, vidStart - 30),
                        Math.min(body.length(), vidStart + 50));
                Logger.i("DouyinApi", "找到长数字: " + around);
                String id = body.substring(vidStart,
                        vidStart + findDigitLen(body, vidStart));
                if (id.length() >= 16) {
                    return "https://www.douyin.com/video/" + id;
                }
            }

            // Try regex for any numeric video ID near douyin.com
            int idx = body.indexOf("douyin.com");
            if (idx >= 0) {
                String around = body.substring(Math.max(0, idx - 20),
                        Math.min(body.length(), idx + 200));
                String id = extractVideoId(around);
                if (id != null) return around;
            }

        } catch (Exception e) {
            Logger.e("DouyinApi", "短链接解析失败", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return urlStr;
    }

    private static String extractFirst(String text, String prefix, String endChar) {
        if (text == null) return null;
        int idx = text.indexOf(prefix);
        if (idx < 0) return null;
        int start = idx + prefix.length();
        int end = text.indexOf(endChar, start);
        if (end < 0) end = Math.min(start + 30, text.length());
        return text.substring(start, end).trim();
    }

    private static int findLongDigit(String text, int minLen) {
        if (text == null) return -1;
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                run++;
                if (run >= minLen) return i - run + 1;
            } else {
                run = 0;
            }
        }
        return -1;
    }

    private static int findDigitLen(String text, int start) {
        int len = 0;
        for (int i = start; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) len++; else break;
        }
        return len;
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
            Logger.i("DouyinApi", "分享页HTML长度=" + (html != null ? html.length() : 0));
            if (html != null && html.length() > 100) {
                Logger.i("DouyinApi", "HTML前500: " + html.substring(0,
                        Math.min(500, html.length())));
            }

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

        // Try to find JSON render data first (most reliable)
        String jsonData = extractJsonBlock(html, "RENDER_DATA");
        Logger.i("DouyinApi", "RENDER_DATA长度="
                + (jsonData != null ? jsonData.length() : 0));
        if (jsonData != null) {
            String vid = extractVideoIdFromJson(jsonData);
            if (vid != null) return vid;
        }

        // Try window.__INITIAL_STATE__
        jsonData = extractJsonBlock(html, "__INITIAL_STATE__");
        if (jsonData != null) {
            String vid = extractVideoIdFromJson(jsonData);
            if (vid != null) return vid;
        }

        // Fallback: scan entire HTML for ID patterns
        String[] patterns = {"aweme_id", "item_id", "video_id",
                "/video/", "modal_id="};
        for (int pi = 0; pi < patterns.length; pi++) {
            String pat = patterns[pi];
            String result = scanForId(html, pat);
            if (result != null) return result;
        }

        return null;
    }

    private static String extractJsonBlock(String html, String blockId) {
        // Pattern: <script id="RENDER_DATA" ...>JSON</script>
        // or: window.__INITIAL_STATE__ *= *{JSON}
        int start = -1;
        int end = -1;

        if (blockId.equals("RENDER_DATA")) {
            start = html.indexOf("id=\"RENDER_DATA\"");
            if (start < 0) start = html.indexOf("id='RENDER_DATA'");
            if (start < 0) return null;
            start = html.indexOf(">", start) + 1;
            end = html.indexOf("</script>", start);
        } else if (blockId.equals("__INITIAL_STATE__")) {
            start = html.indexOf("__INITIAL_STATE__");
            if (start < 0) return null;
            start = html.indexOf("{", start);
            if (start < 0) return null;
            // Find matching closing brace (rough)
            int depth = 0;
            for (int i = start; i < html.length(); i++) {
                char c = html.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) { end = i + 1; break; } }
            }
        }

        if (start >= 0 && end > start) {
            return html.substring(start, end);
        }
        return null;
    }

    private static String extractVideoIdFromJson(String json) {
        if (json == null) return null;
        // Search for numeric ID values near key video fields
        String[] keys = {"\"aweme_id\":\"", "\"aweme_id\":", "\"item_id\":\"",
                "\"item_id\":", "\"video_id\":\"", "\"video_id\":"};
        for (int ki = 0; ki < keys.length; ki++) {
            String key = keys[ki];
            int idx = json.indexOf(key);
            if (idx >= 0) {
                int s = idx + key.length();
                // Skip opening quote if present
                if (s < json.length() && json.charAt(s) == '"') s++;
                StringBuilder sb = new StringBuilder();
                for (int j = s; j < json.length() && j < s + 30; j++) {
                    char c = json.charAt(j);
                    if (!Character.isDigit(c)) break;
                    sb.append(c);
                }
                String id = sb.toString();
                if (id.length() > 10 && id.length() < 25) return id;
            }
        }
        return null;
    }

    private static String scanForId(String html, String pattern) {
        int idx = 0;
        while ((idx = html.indexOf(pattern, idx)) >= 0) {
            int start = idx + pattern.length();
            // If pattern is like /video/ or modal_id=, just grab digits
            StringBuilder sb = new StringBuilder();
            for (int j = start; j < html.length() && j < start + 30; j++) {
                char c = html.charAt(j);
                if (c == '"' || c == '&' || c == '?' || c == '/'
                        || c == '\\' || c == '\'' || c == ' ' || c == ',') break;
                sb.append(c);
            }
            String id = sb.toString().trim();
            // If pattern is a key name like aweme_id, also try after :" format
            if (id.length() < 8 && (pattern.contains("_id") || pattern.contains("video/"))) {
                // Try the JSON value format: "key":"value"
                int colon = html.indexOf(":", start);
                if (colon > start && colon < start + 20) {
                    int quote = html.indexOf("\"", colon + 2);
                    int endQuote = html.indexOf("\"", quote + 1);
                    if (quote > 0 && endQuote > quote) {
                        id = html.substring(quote + 1, endQuote).trim();
                    }
                }
            }
            if (id.length() > 10 && id.length() < 25 && id.matches("[0-9]+")) {
                return id;
            }
            idx = start;
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
