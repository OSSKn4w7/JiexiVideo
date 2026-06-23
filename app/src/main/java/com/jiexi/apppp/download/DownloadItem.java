package com.jiexi.apppp.download;

import java.io.Serializable;

public class DownloadItem implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_FAILED = 4;

    public long id;
    public String bvid;
    public String title;
    public String qualityName;
    public String url;
    public String bestAudioUrl;
    public String filePath;
    public String fileName;
    public long totalSize;
    public long downloadedSize;
    public int status;
    public int progress; // 0-100
    public long createTime;
    public String saveDir;
}
