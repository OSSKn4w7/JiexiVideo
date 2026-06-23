package com.jiexi.apppp.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VideoInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    // Platform constants
    public static final int PLATFORM_BILIBILI = 0;
    public static final int PLATFORM_DOUYIN = 1;

    public int platform;

    // Basic info
    public String bvid;
    public String aid;
    public String cid;
    public String title;
    public String description;
    public String coverUrl;
    public String authorName;
    public String authorAvatar;
    public long durationSeconds;
    public long viewCount;
    public long likeCount;

    // Quality / format options
    public List<QualityOption> videoQualities = new ArrayList<QualityOption>();
    public List<QualityOption> audioQualities = new ArrayList<QualityOption>();
    public List<SubtitleOption> subtitles = new ArrayList<SubtitleOption>();

    public static class QualityOption implements Serializable {
        private static final long serialVersionUID = 1L;

        public int qualityCode;
        public String qualityName;
        public String format;
        public String url;
        public long fileSize;
        public boolean isAudio;
        /** Quality likely needs authentication; informational only */
        public boolean needsAuth;
        /** Fallback URLs (different codecs for same quality level) */
        public List<String> fallbackUrls = new ArrayList<String>();
    }

    public static class SubtitleOption implements Serializable {
        private static final long serialVersionUID = 1L;

        public String language;
        public String languageName;
        public String url;
        public String format;
    }
}
