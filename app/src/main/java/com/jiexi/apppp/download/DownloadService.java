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
        DownloadItem item = new DownloadItem();
        item.id = System.currentTimeMillis();
        item.title = title;
        item.url = url;
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
        startDownload(item);
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
            startDownload(item);
        }
    }

    public synchronized void deleteTask(long id) {
        for (int i = mTaskList.size() - 1; i >= 0; i--) {
            if (mTaskList.get(i).id == id) {
                DownloadItem item = mTaskList.get(i);
                if (item.status == DownloadItem.STATUS_DOWNLOADING) {
                    item.status = DownloadItem.STATUS_PAUSED;
                }
                // Delete file
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

    private void startDownload(final DownloadItem item) {
        item.status = DownloadItem.STATUS_DOWNLOADING;
        updateNotification(item);

        Future<?> future = mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream is = null;
                FileOutputStream fos = null;
                try {
                    File file = new File(item.filePath);
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    long startOffset = 0;
                    if (file.exists()) {
                        startOffset = file.length();
                        item.downloadedSize = startOffset;
                    }

                    conn = HttpUtil.openDownloadConnection(item.url, startOffset);
                    conn.connect();

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200 && responseCode != 206) {
                        throw new Exception("HTTP " + responseCode);
                    }

                    long contentLength = conn.getContentLength();
                    if (contentLength > 0) {
                        item.totalSize = startOffset + contentLength;
                    }

                    is = conn.getInputStream();
                    fos = new FileOutputStream(file, startOffset > 0);

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
                    }

                } catch (Exception e) {
                    if (item.status != DownloadItem.STATUS_PAUSED) {
                        item.status = DownloadItem.STATUS_FAILED;
                        updateNotification(item);
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
        });

        mFutures.add(future);
    }

    private Notification.Builder createNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID);
        } else {
            return new Notification.Builder(this);
        }
    }

    private void updateNotification(DownloadItem item) {
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

        Notification.Builder builder = createNotificationBuilder()
                .setContentTitle(item.status == DownloadItem.STATUS_PAUSED ? "已暂停" : "下载中")
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
