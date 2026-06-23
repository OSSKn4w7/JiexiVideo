package com.jiexi.apppp.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class HttpUtil {

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;
    private static String sGlobalCookie = "";

    public static void setGlobalCookie(String cookie) {
        sGlobalCookie = (cookie != null) ? cookie : "";
    }

    public static String getGlobalCookie() {
        return sGlobalCookie;
    }

    public static String get(String urlStr) throws IOException {
        return get(urlStr, null);
    }

    public static String get(String urlStr, Map<String, String> headers) throws IOException {
        return getInternal(urlStr, headers, true);
    }

    /**
     * GET request WITHOUT updating the global cookie from response headers.
     * Use this for endpoints that may return guest credentials (e.g. WBI key init).
     */
    public static String getWithoutCookieUpdate(String urlStr, Map<String, String> headers)
            throws IOException {
        return getInternal(urlStr, headers, false);
    }

    private static String getInternal(String urlStr, Map<String, String> headers,
                                       boolean updateCookie) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            if (sGlobalCookie.length() > 0) {
                conn.setRequestProperty("Cookie", sGlobalCookie);
            }
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            int code = conn.getResponseCode();
            if (code == 301 || code == 302) {
                String location = conn.getHeaderField("Location");
                return getInternal(location, headers, updateCookie);
            }

            if (updateCookie) {
                // Update cookies from response
                List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
                if (setCookies != null) {
                    StringBuilder cookieBuilder = new StringBuilder(sGlobalCookie);
                    for (String sc : setCookies) {
                        if (sc != null) {
                            String[] parts = sc.split(";");
                            if (parts.length > 0) {
                                String kv = parts[0].trim();
                                String key = kv.split("=", 2)[0];
                                int idx = cookieBuilder.indexOf(key + "=");
                                if (idx >= 0) {
                                    int end = cookieBuilder.indexOf(";", idx);
                                    if (end < 0) end = cookieBuilder.length();
                                    cookieBuilder.replace(idx, end, kv);
                                } else {
                                    if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
                                    cookieBuilder.append(kv);
                                }
                            }
                        }
                    }
                    sGlobalCookie = cookieBuilder.toString();
                }
            }

            return readStream(conn);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String post(String urlStr, String body, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            if (sGlobalCookie.length() > 0) {
                conn.setRequestProperty("Cookie", sGlobalCookie);
            }
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (body != null) {
                OutputStream os = null;
                try {
                    os = conn.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                } finally {
                    if (os != null) os.close();
                }
            }

            return readStream(conn);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static HttpURLConnection openDownloadConnection(String urlStr, long startOffset) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("Origin", "https://www.bilibili.com");
        if (sGlobalCookie.length() > 0) {
            conn.setRequestProperty("Cookie", sGlobalCookie);
        }
        if (startOffset > 0) {
            conn.setRequestProperty("Range", "bytes=" + startOffset + "-");
        }
        return conn;
    }

    public static String resolveRedirectUrl(String urlStr) throws IOException {
        String current = urlStr;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(current);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");
                conn.connect();
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null) break;
                    if (location.startsWith("/")) {
                        location = url.getProtocol() + "://" + url.getHost() + location;
                    }
                    current = location;
                } else {
                    break;
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return current;
    }

    private static String readStream(HttpURLConnection conn) throws IOException {
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            String encoding = conn.getContentEncoding();
            if (conn.getResponseCode() >= 400) {
                is = conn.getErrorStream();
            } else {
                is = conn.getInputStream();
            }
            if (is == null) {
                return "";
            }
            if ("gzip".equalsIgnoreCase(encoding)) {
                is = new GZIPInputStream(is);
            }
            baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString("UTF-8");
        } finally {
            if (baos != null) {
                try { baos.close(); } catch (IOException ignored) {}
            }
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }
}
