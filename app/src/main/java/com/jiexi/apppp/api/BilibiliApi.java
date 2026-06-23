package com.jiexi.apppp.api;

import android.util.Log;

import com.jiexi.apppp.util.HttpUtil;
import com.jiexi.apppp.util.Logger;
import com.jiexi.apppp.util.WbiSignUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BilibiliApi {

    private static final String TAG = "BilibiliApi";

    // Bilibili quality codes
    public static final int QN_240P = 6;
    public static final int QN_360P = 16;
    public static final int QN_480P = 32;
    public static final int QN_720P = 64;
    public static final int QN_720P60 = 74;
    public static final int QN_1080P = 80;
    public static final int QN_1080P_PLUS = 112;
    public static final int QN_1080P60 = 116;
    public static final int QN_4K = 120;
    public static final int QN_HDR = 125;
    public static final int QN_DOLBY = 126;
    public static final int QN_8K = 127;

    // Login required quality thresholds
    public static final int QN_LOGIN_REQUIRED = 80;  // 1080p needs login
    public static final int QN_VIP_REQUIRED = 112;    // 1080p+ needs VIP

    /**
     * Parse a Bilibili URL and extract bvid
     */
    public static String extractBvid(String url) {
        if (url == null) return null;
        // Pattern: BV1xx411c7mD or bvid=... or video/BV...
        String[] patterns = {
                "bilibili.com/video/BV",
                "b23.tv/",
                "bvid="
        };
        for (String p : patterns) {
            int idx = url.indexOf(p);
            if (idx >= 0) {
                String rest = url.substring(idx + p.length());
                // For b23.tv short links, follow redirect first
                if (p.equals("b23.tv/")) {
                    return resolveShortLink(url);
                }
                // Match BV followed by 10 chars
                if (p.contains("BV")) {
                    return "BV" + rest.substring(0, Math.min(10, rest.length()));
                }
                if (p.equals("bvid=")) {
                    int end = rest.indexOf("&");
                    if (end < 0) end = rest.length();
                    return rest.substring(0, end);
                }
            }
        }
        // Try regex-like extraction
        int bvIdx = url.indexOf("BV");
        if (bvIdx >= 0 && url.indexOf("bilibili") >= 0) {
            String bv = url.substring(bvIdx, Math.min(bvIdx + 12, url.length()));
            return bv.replaceAll("[^A-Za-z0-9]", "");
        }
        return null;
    }

    private static String resolveShortLink(String shortUrl) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(shortUrl).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            int code = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if ((code == 301 || code == 302) && location != null) {
                return extractBvid(location);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Fetch video basic information
     */
    public static VideoInfo fetchVideoInfo(String bvid) throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("bvid", bvid);
        String query = WbiSignUtil.signParams(params);
        String url = "https://api.bilibili.com/x/web-interface/wbi/view?" + query;

        String resp = HttpUtil.get(url);
        JSONObject json = new JSONObject(resp);
        if (json.getInt("code") != 0) {
            throw new Exception("B站API错误: " + json.optString("message", "未知错误"));
        }

        JSONObject data = json.getJSONObject("data");
        VideoInfo info = new VideoInfo();
        info.platform = VideoInfo.PLATFORM_BILIBILI;
        info.bvid = data.getString("bvid");
        info.aid = String.valueOf(data.getLong("aid"));
        info.cid = String.valueOf(data.getLong("cid"));
        info.title = data.getString("title");
        info.description = data.optString("desc", "");
        info.coverUrl = data.getString("pic");
        info.durationSeconds = data.getLong("duration");

        JSONObject stat = data.getJSONObject("stat");
        info.viewCount = stat.optLong("view", 0);
        info.likeCount = stat.optLong("like", 0);

        JSONObject owner = data.getJSONObject("owner");
        info.authorName = owner.getString("name");
        info.authorAvatar = owner.getString("face");

        return info;
    }

    /**
     * Fetch play URLs (DASH streams) with all quality options.
     * All qualities from the API are returned — the server filters
     * based on auth cookie. No client-side login/VIP filtering.
     */
    public static void fetchPlayUrls(VideoInfo info) throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("bvid", info.bvid);
        params.put("cid", info.cid);
        params.put("fnval", "4048"); // DASH + Dolby + HDR + 8K
        params.put("fnver", "0");
        params.put("fourk", "1");
        params.put("qn", "127"); // request highest available

        String query = WbiSignUtil.signParams(params);
        String url = "https://api.bilibili.com/x/player/wbi/playurl?" + query;

        Logger.i("BiliApi", "请求播放地址: cookie长度="
                + HttpUtil.getGlobalCookie().length());
        String resp = HttpUtil.get(url);
        JSONObject json = new JSONObject(resp);
        JSONObject data = json.getJSONObject("data");

        // Get accepted quality list for reference
        JSONArray acceptQn = data.optJSONArray("accept_quality");
        JSONArray acceptDesc = data.optJSONArray("accept_description");
        Logger.i("BiliApi", "playurl code=" + json.getInt("code")
                + " 可选画质数=" + (acceptQn != null ? acceptQn.length() : 0));

        Map<Integer, String> qualityMap = new HashMap<Integer, String>();
        if (acceptQn != null && acceptDesc != null) {
            for (int i = 0; i < acceptQn.length() && i < acceptDesc.length(); i++) {
                qualityMap.put(acceptQn.getInt(i), acceptDesc.getString(i));
            }
        }

        // Parse DASH video streams
        JSONObject dash = data.optJSONObject("dash");
        if (dash != null) {
            JSONArray videos = dash.optJSONArray("video");
            if (videos != null) {
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.getJSONObject(i);
                    VideoInfo.QualityOption opt = new VideoInfo.QualityOption();
                    opt.isAudio = false;
                    opt.qualityCode = v.getInt("id");
                    opt.qualityName = getQualityName(opt.qualityCode);
                    if (qualityMap.containsKey(opt.qualityCode)) {
                        opt.qualityName = qualityMap.get(opt.qualityCode);
                    }
                    opt.format = v.optString("codecs", "avc");
                    opt.url = v.optString("baseUrl", v.optString("base_url", ""));
                    opt.fileSize = estimateFileSize(info.durationSeconds, opt.qualityCode, false);
                    // Mark qualities likely needing auth — informational only
                    opt.needsAuth = (opt.qualityCode >= QN_LOGIN_REQUIRED);

                    if (opt.url.length() > 0) {
                        info.videoQualities.add(opt);
                    }
                }
            }

            // Parse DASH audio streams
            JSONArray audios = dash.optJSONArray("audio");
            if (audios != null) {
                for (int i = 0; i < audios.length(); i++) {
                    JSONObject a = audios.getJSONObject(i);
                    VideoInfo.QualityOption opt = new VideoInfo.QualityOption();
                    opt.isAudio = true;
                    opt.qualityCode = a.getInt("id");
                    opt.qualityName = "音频 (" + getAudioQualityName(opt.qualityCode) + ")";
                    opt.format = "aac";
                    opt.url = a.optString("baseUrl", a.optString("base_url", ""));
                    opt.fileSize = estimateFileSize(info.durationSeconds, opt.qualityCode, true);
                    opt.needsAuth = false;

                    if (opt.url.length() > 0) {
                        info.audioQualities.add(opt);
                    }
                }
            }
        }

        // Fallback: if no DASH, try durl (flv/mp4 segments)
        if (info.videoQualities.isEmpty() && info.audioQualities.isEmpty()) {
            JSONArray durl = data.optJSONArray("durl");
            if (durl != null && durl.length() > 0) {
                JSONObject seg = durl.getJSONObject(0);
                int currentQn = data.optInt("quality", 0);

                VideoInfo.QualityOption opt = new VideoInfo.QualityOption();
                opt.isAudio = false;
                opt.qualityCode = currentQn;
                opt.qualityName = qualityMap.containsKey(currentQn)
                        ? qualityMap.get(currentQn) : getQualityName(currentQn);
                opt.format = "mp4";
                opt.url = seg.optString("url", "");
                opt.fileSize = seg.optLong("size", 0);
                opt.needsAuth = false;

                if (opt.url.length() > 0) {
                    info.videoQualities.add(opt);
                }
            }
        }

        Logger.i("BiliApi", "解析完成: 视频流=" + info.videoQualities.size()
                + "个 音频流=" + info.audioQualities.size() + "个");
    }

    /**
     * Fetch subtitle list
     */
    public static void fetchSubtitles(VideoInfo info) throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("bvid", info.bvid);
        params.put("cid", info.cid);

        String query = WbiSignUtil.signParams(params);
        String url = "https://api.bilibili.com/x/player/wbi/v2?" + query;

        String resp = HttpUtil.get(url);
        JSONObject json = new JSONObject(resp);
        JSONObject data = json.optJSONObject("data");
        if (data == null) return;

        JSONObject subtitle = data.optJSONObject("subtitle");
        if (subtitle == null) return;

        JSONArray subtitles = subtitle.optJSONArray("subtitles");
        if (subtitles == null) return;

        for (int i = 0; i < subtitles.length(); i++) {
            JSONObject sub = subtitles.getJSONObject(i);
            VideoInfo.SubtitleOption opt = new VideoInfo.SubtitleOption();
            opt.language = sub.getString("lan");
            opt.languageName = sub.optString("lan_doc", opt.language);
            opt.url = sub.getString("subtitle_url");
            opt.format = "vtt"; // Bilibili subtitles in JSON → convert to srt/vtt later

            // URL might need https prefix
            if (opt.url.startsWith("//")) {
                opt.url = "https:" + opt.url;
            }
            info.subtitles.add(opt);
        }
    }

    /**
     * Check user login / VIP status
     */
    public static LoginStatus checkLoginStatus() throws Exception {
        String resp = HttpUtil.get("https://api.bilibili.com/x/web-interface/nav");
        JSONObject json = new JSONObject(resp);
        JSONObject data = json.getJSONObject("data");

        LoginStatus status = new LoginStatus();
        status.isLoggedIn = data.optBoolean("isLogin", false);

        if (status.isLoggedIn) {
            status.mid = data.optLong("mid", 0);
            status.uname = data.optString("uname", "");
            status.face = data.optString("face", "");
            status.isVip = data.optInt("vipType", 0) == 2;
            Logger.i("BiliApi", "用户已登录: " + status.uname
                    + " VIP=" + status.isVip);
        } else {
            Logger.i("BiliApi", "用户未登录");
        }

        return status;
    }

    public static class LoginStatus {
        public boolean isLoggedIn;
        public boolean isVip;
        public long mid;
        public String uname;
        public String face;
    }

    private static String getQualityName(int qn) {
        switch (qn) {
            case QN_240P: return "240P 流畅";
            case QN_360P: return "360P 清晰";
            case QN_480P: return "480P 标清";
            case QN_720P: return "720P 高清";
            case QN_720P60: return "720P 60帧";
            case QN_1080P: return "1080P 全高清";
            case QN_1080P_PLUS: return "1080P 高码率";
            case QN_1080P60: return "1080P 60帧";
            case QN_4K: return "4K 超清";
            case QN_HDR: return "HDR 真彩";
            case QN_DOLBY: return "杜比视界";
            case QN_8K: return "8K 超高清";
            default: return qn + "P";
        }
    }

    private static String getAudioQualityName(int qn) {
        switch (qn) {
            case 30216: return "64K";
            case 30232: return "132K";
            case 30280: return "192K";
            case 30251: return "320K";
            default: return qn + "";
        }
    }

    private static long estimateFileSize(long durationSec, int qn, boolean isAudio) {
        // Rough estimation based on bitrate
        int bitrate = 1000; // kbps default
        if (isAudio) {
            switch (qn) {
                case 30216: bitrate = 64; break;
                case 30232: bitrate = 132; break;
                case 30280: bitrate = 192; break;
                case 30251: bitrate = 320; break;
                default: bitrate = 128; break;
            }
        } else {
            switch (qn) {
                case QN_240P: bitrate = 400; break;
                case QN_360P: bitrate = 700; break;
                case QN_480P: bitrate = 1200; break;
                case QN_720P: case QN_720P60: bitrate = 2500; break;
                case QN_1080P: bitrate = 4000; break;
                case QN_1080P_PLUS: case QN_1080P60: bitrate = 6000; break;
                case QN_4K: case QN_HDR: bitrate = 15000; break;
                case QN_DOLBY: bitrate = 20000; break;
                case QN_8K: bitrate = 30000; break;
                default: bitrate = 2000; break;
            }
        }
        return (durationSec * bitrate * 1000L) / 8;
    }
}
