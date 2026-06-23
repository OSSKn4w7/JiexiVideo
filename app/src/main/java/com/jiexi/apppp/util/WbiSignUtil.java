package com.jiexi.apppp.util;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONObject;

public class WbiSignUtil {

    private static String sMixinKey = "";
    private static boolean sInited = false;

    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 52, 44, 34
    };

    public static void init() {
        if (sInited) return;
        try {
            String resp = HttpUtil.get("https://api.bilibili.com/x/web-interface/nav");
            JSONObject json = new JSONObject(resp);
            JSONObject data = json.getJSONObject("data");
            JSONObject wbiImg = data.getJSONObject("wbi_img");
            String imgUrl = wbiImg.getString("img_url");
            String subUrl = wbiImg.getString("sub_url");

            String imgKey = extractKey(imgUrl);
            String subKey = extractKey(subUrl);
            String rawKey = imgKey + subKey;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                int idx = MIXIN_KEY_ENC_TAB[i];
                if (idx < rawKey.length()) {
                    sb.append(rawKey.charAt(idx));
                }
            }
            sMixinKey = sb.toString();
            sInited = true;
        } catch (Exception e) {
            sMixinKey = "";
            sInited = true;
        }
    }

    private static String extractKey(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    public static String signParams(Map<String, String> params) throws IOException {
        if (!sInited) {
            init();
        }
        if (sMixinKey.length() == 0) {
            init();
        }

        TreeMap<String, String> sorted = new TreeMap<String, String>(params);
        sorted.put("wts", String.valueOf(System.currentTimeMillis() / 1000));

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (query.length() > 0) query.append("&");
            query.append(entry.getKey());
            query.append("=");
            query.append(encodeURIComponent(entry.getValue()));
        }

        String signStr = query.toString() + sMixinKey;
        String wRid = md5(signStr);

        return query.toString() + "&w_rid=" + wRid;
    }

    private static String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
