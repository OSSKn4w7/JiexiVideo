package com.jiexi.apppp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkExtractor {

    // Douyin patterns
    private static final Pattern DOUYIN_SHORT = Pattern.compile(
            "https?://v\\.douyin\\.com/[a-zA-Z0-9]+/?");
    private static final Pattern DOUYIN_FULL = Pattern.compile(
            "https?://(www\\.)?douyin\\.com/(video|note)/\\d+");
    private static final Pattern DOUYIN_IES = Pattern.compile(
            "https?://(www\\.)?iesdouyin\\.com/share/video/\\d+");

    // Bilibili patterns
    private static final Pattern BILIBILI_SHORT = Pattern.compile(
            "https?://b23\\.tv/[a-zA-Z0-9]+/?");
    private static final Pattern BILIBILI_VIDEO = Pattern.compile(
            "https?://(www\\.)?bilibili\\.com/video/BV[a-zA-Z0-9]+/?[^\\s]*");
    private static final Pattern BILIBILI_BV = Pattern.compile(
            "BV[a-zA-Z0-9]{10}");

    // Generic URL pattern as fallback
    private static final Pattern GENERIC_URL = Pattern.compile(
            "https?://[^\\s\\u4e00-\\u9fff]+");

    /**
     * Extract a video URL from pasted share text.
     * Returns the cleaned video URL, or null if not found.
     */
    public static String extract(String rawText) {
        if (rawText == null || rawText.trim().length() == 0) {
            return null;
        }

        String text = rawText.trim();

        // Priority 1: Douyin short link
        Matcher m = DOUYIN_SHORT.matcher(text);
        if (m.find()) {
            return cleanUrl(m.group());
        }

        // Priority 2: Bilibili short link
        m = BILIBILI_SHORT.matcher(text);
        if (m.find()) {
            return cleanUrl(m.group());
        }

        // Priority 3: Douyin full link
        m = DOUYIN_FULL.matcher(text);
        if (m.find()) {
            return cleanUrl(m.group());
        }

        // Priority 4: Douyin ies link
        m = DOUYIN_IES.matcher(text);
        if (m.find()) {
            return cleanUrl(m.group());
        }

        // Priority 5: Bilibili video link
        m = BILIBILI_VIDEO.matcher(text);
        if (m.find()) {
            return cleanUrl(m.group());
        }

        // Priority 6: BV number (without URL)
        m = BILIBILI_BV.matcher(text);
        if (m.find()) {
            return m.group();
        }

        // Priority 7: Try generic URL detection for any other video link
        m = GENERIC_URL.matcher(text);
        while (m.find()) {
            String url = m.group();
            if (url.contains("douyin") || url.contains("bilibili")
                    || url.contains("b23.tv") || url.contains("tiktok")) {
                return cleanUrl(url);
            }
        }

        // No URL found, return original text (let existing parser handle it)
        return text;
    }

    /**
     * Clean URL: remove trailing punctuation, query params beyond base,
     * and fix common formatting issues.
     */
    private static String cleanUrl(String url) {
        if (url == null) return null;

        // Remove trailing non-URL characters
        url = url.replaceAll("[,，。.!！?？、；;：:）)】\"]+$", "");

        // Remove trailing slash unless it's integral
        if (url.endsWith("/") && url.length() > 8) {
            String withoutSlash = url.substring(0, url.length() - 1);
            // Only strip if it looks like a trailing slash on a path
            if (withoutSlash.length() - withoutSlash.lastIndexOf('/') > 2) {
                url = withoutSlash;
            }
        }

        return url;
    }

    /**
     * Determine the platform from an extracted URL.
     */
    public static int detectPlatform(String url) {
        if (url == null) return -1;
        if (url.contains("douyin") || url.contains("tiktok")) {
            return com.jiexi.apppp.api.VideoInfo.PLATFORM_DOUYIN;
        }
        if (url.contains("bilibili") || url.contains("b23.tv") || url.startsWith("BV")) {
            return com.jiexi.apppp.api.VideoInfo.PLATFORM_BILIBILI;
        }
        return -1;
    }
}
