package com.jiexi.apppp.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import com.jiexi.apppp.ui.DownloadListActivity;
import com.jiexi.apppp.util.FileUtil;
import com.jiexi.apppp.util.HttpUtil;
import com.jiexi.apppp.util.Logger;
import com.jiexi.apppp.util.MediaMerger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DownloadService extends Service {

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID_BASE = 1000;

    private List<DownloadItem> mTaskList = new ArrayList<DownloadItem>();
    private List<Future<?>> mFutures = new ArrayList<Future<?>>();
    private ExecutorService mExecutor;
    private NotificationManager mNotificationManager;

    private final IBinder mBinder = new DownloadBinder();

    public class DownloadBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mExecutor = Executors.newFixedThreadPool(3);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public synchronized int addTask(String title, String url, String qualityName,
                                     String fileExt, String bvid) {
        return addTaskWithFallback(title, url, qualityName, fileExt, bvid, null, null);
    }

    public synchronized int addTaskWithFallback(String title, String url, String qualityName,
                                     String fileExt, String bvid,
                                     List<String> fallbackUrls, String bestAudioUrl) {
        // Deduplicate: skip if same title+quality already downloading
        for (DownloadItem existing : mTaskList) {
            if (existing.title.equals(title) && existing.qualityName.equals(qualityName)) {
                if (existing.status == DownloadItem.STATUS_DOWNLOADING
                        || existing.status == DownloadItem.STATUS_PENDING) {
                    return (int) existing.id;
                }
            }
        }

        DownloadItem item = new DownloadItem();
        item.id = System.currentTimeMillis();
        item.title = title;
        item.url = url;
        item.bestAudioUrl = bestAudioUrl;
        item.qualityName = qualityName;
        item.bvid = bvid;
        item.status = DownloadItem.STATUS_PENDING;
        item.progress = 0;
        item.downloadedSize = 0;
        item.totalSize = 0;
        item.createTime = System.currentTimeMillis();
        item.saveDir = FileUtil.getDownloadDir().getAbsolutePath();
        item.fileName = FileUtil.sanitizeFileName(title) + "_" + qualityName + fileExt;
        item.filePath = item.saveDir + File.separator + item.fileName;

        mTaskList.add(item);
        Logger.i("Download", "添加: " + title + " [" + qualityName + "]"
                + (fallbackUrls != null && fallbackUrls.size() > 0 ?
                " 备选" + fallbackUrls.size() : ""));
        startDownload(item, fallbackUrls);
        return (int) item.id;
    }

    public synchronized void pauseTask(long id) {
        DownloadItem item = findTask(id);
        if (item != null && item.status == DownloadItem.STATUS_DOWNLOADING) {
            item.status = DownloadItem.STATUS_PAUSED;
        }
    }

    public synchronized void resumeTask(long id) {
        DownloadItem item = findTask(id);
        if (item != null && item.status == DownloadItem.STATUS_PAUSED) {
            startDownload(item, null);
        }
    }

    public synchronized void deleteTask(long id) {
        for (int i = mTaskList.size() - 1; i >= 0; i--) {
            if (mTaskList.get(i).id == id) {
                DownloadItem item = mTaskList.get(i);
                if (item.status == DownloadItem.STATUS_DOWNLOADING) {
                    item.status = DownloadItem.STATUS_PAUSED;
                }
                try {
                    File f = new File(item.filePath);
                    if (f.exists()) f.delete();
                } catch (Exception ignored) {}
                mTaskList.remove(i);
                break;
            }
        }
    }

    public synchronized DownloadItem findTask(long id) {
        for (DownloadItem item : mTaskList) {
            if (item.id == id) return item;
        }
        return null;
    }

    public synchronized List<DownloadItem> getAllTasks() {
        return new ArrayList<DownloadItem>(mTaskList);
    }

    private void startDownload(final DownloadItem item, final List<String> fallbackUrls) {
        item.status = DownloadItem.STATUS_DOWNLOADING;
        updateNotification(item);

        Future<?> future = mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                downloadWithFallback(item, item.url, fallbackUrls, 0);
            }
        });

        mFutures.add(future);
    }

    private void downloadWithFallback(final DownloadItem item, final String currentUrl,
                                        final List<String> fallbackUrls,
                                        final int attemptIndex) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            // Always start fresh — delete partial file
            File file = new File(item.filePath);
            if (file.exists()) file.delete();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            item.downloadedSize = 0;
            Logger.i("Download", "下载#" + (attemptIndex + 1) + ": "
                    + item.qualityName + " " + currentUrl.substring(0,
                    Math.min(50, currentUrl.length())));

            conn = HttpUtil.openDownloadConnection(currentUrl, 0);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 206) {
                // Try fallback
                if (fallbackUrls != null && attemptIndex < fallbackUrls.size()) {
                    downloadWithFallback(item, fallbackUrls.get(attemptIndex),
                            fallbackUrls, attemptIndex + 1);
                    return;
                }
                throw new Exception("HTTP " + responseCode);
            }

            long contentLength = conn.getContentLength();
            item.totalSize = contentLength;

            is = conn.getInputStream();
            fos = new FileOutputStream(file, false);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long lastUpdateTime = System.currentTimeMillis();

            while (item.status == DownloadItem.STATUS_DOWNLOADING
                    && (bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                item.downloadedSize += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastUpdateTime > 200) {
                    if (item.totalSize > 0) {
                        item.progress = (int) (item.downloadedSize * 100 / item.totalSize);
                    }
                    updateNotification(item);
                    lastUpdateTime = now;
                }
            }

            if (item.status == DownloadItem.STATUS_DOWNLOADING) {
                item.status = DownloadItem.STATUS_COMPLETED;
                item.progress = 100;
                updateNotification(item);
                Logger.i("Download", "完成: " + item.title + " [" + item.qualityName + "]");

                // Download best audio and merge if available
                if (item.bestAudioUrl != null && item.bestAudioUrl.length() > 0) {
                    mergeAudio(item);
                }
            }

        } catch (Exception e) {
            // Try fallback
            if (fallbackUrls != null && attemptIndex < fallbackUrls.size()) {
                Logger.i("Download", "备选#" + (attemptIndex + 1)
                        + ": " + item.qualityName);
                downloadWithFallback(item, fallbackUrls.get(attemptIndex),
                        fallbackUrls, attemptIndex + 1);
                return;
            }
            Logger.e("Download", "失败: " + item.title + " ["
                    + item.qualityName + "]", e);
            if (item.status != DownloadItem.STATUS_PAUSED) {
                item.status = DownloadItem.STATUS_FAILED;
                updateNotification(item);
                try {
                    File f = new File(item.filePath);
                    if (f.exists()) f.delete();
                } catch (Exception ignored) {}
            }
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Notification.Builder createNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID);
        } else {
            return new Notification.Builder(this);
        }
    }

    private void updateNotification(DownloadItem item) {
        updateNotification(item, null);
    }

    private void updateNotification(DownloadItem item, String customStatus) {
        String title = item.title;
        if (title.length() > 30) {
            title = title.substring(0, 30) + "...";
        }

        int notifId = NOTIFICATION_ID_BASE + ((int) item.id % 1000);

        if (item.status == DownloadItem.STATUS_COMPLETED) {
            Notification notification = createNotificationBuilder()
                    .setContentTitle("下载完成")
                    .setContentText(title)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true)
                    .build();
            mNotificationManager.notify(notifId, notification);
            return;
        }

        if (item.status == DownloadItem.STATUS_FAILED) {
            Notification notification = createNotificationBuilder()
                    .setContentTitle("下载失败")
                    .setContentText(title)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setAutoCancel(true)
                    .build();
            mNotificationManager.notify(notifId, notification);
            return;
        }

        String progressText = item.progress + "%";
        if (item.totalSize > 0) {
            progressText += " - " + FileUtil.formatSize(item.downloadedSize)
                    + "/" + FileUtil.formatSize(item.totalSize);
        }

        String statusTitle = item.status == DownloadItem.STATUS_PAUSED ? "已暂停" : "下载中";
        if (customStatus != null) statusTitle = customStatus;

        Notification.Builder builder = createNotificationBuilder()
                .setContentTitle(statusTitle)
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, item.progress, false)
                .setContentInfo(progressText);

        Intent intent = new Intent(this, DownloadListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pi);

        mNotificationManager.notify(notifId, builder.build());
    }

    private void mergeAudio(DownloadItem item) {
        String videoPath = item.filePath;
        String audioPath = videoPath.replaceFirst("\\.[^.]+$", "_audio.m4a");
        String mergedPath = videoPath.replaceFirst("\\.[^.]+$", "_merged.mp4");

        Logger.i("Download", "开始下载音频: " + item.title);
        updateNotification(item, "下载音频中...");

        // Download audio
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            File audioFile = new File(audioPath);
            conn = HttpUtil.openDownloadConnection(item.bestAudioUrl, 0);
            conn.connect();
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) {
                Logger.e("Download", "音频下载失败 HTTP " + code);
                return;
            }

            is = conn.getInputStream();
            fos = new FileOutputStream(audioFile, false);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
            fos.flush();

            Logger.i("Download", "音频下载完成, 开始合并: " + item.title);
            updateNotification(item, "合并音视频中...");

            // Merge
            boolean merged = MediaMerger.merge(videoPath, audioPath, mergedPath);
            if (merged) {
                // Replace video file with merged file
                File videoFile = new File(videoPath);
                videoFile.delete();
                new File(mergedPath).renameTo(videoFile);
                Logger.i("Download", "合并成功: " + item.title);
            } else {
                Logger.e("Download", "合并失败: " + item.title);
            }
            // Clean up audio temp
            try { new File(audioPath).delete(); } catch (Exception ignored) {}

        } catch (Exception e) {
            Logger.e("Download", "音频下载/合并异常: " + item.title, e);
        } finally {
            if (fos != null) { try { fos.close(); } catch (Exception ignored) {} }
            if (is != null) { try { is.close(); } catch (Exception ignored) {} }
            if (conn != null) { conn.disconnect(); }
            updateNotification(item);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "下载通知",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("视频下载进度通知");
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }
}
