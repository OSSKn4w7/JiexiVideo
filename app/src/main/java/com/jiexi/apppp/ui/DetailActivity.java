package com.jiexi.apppp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jiexi.apppp.R;
import com.jiexi.apppp.api.VideoInfo;
import com.jiexi.apppp.api.VideoInfo.QualityOption;
import com.jiexi.apppp.api.VideoInfo.SubtitleOption;
import com.jiexi.apppp.download.DownloadService;
import com.jiexi.apppp.util.FileUtil;
import com.jiexi.apppp.util.Logger;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DetailActivity extends Activity {

    private VideoInfo mVideoInfo;
    private boolean mIsLoggedIn;
    private boolean mIsVip;

    private FrameLayout mPreviewFrame;
    private ImageView mThumbnailImage;
    private SurfaceView mPreviewVideo;
    private View mPlayOverlay;
    private Button mBtnPlay;
    private TextView mTitleText;
    private TextView mAuthorText;
    private TextView mDurationText;
    private TextView mViewText;
    private TextView mLikeText;
    private TextView mDescText;
    private TextView mLoginHintText;
    private TextView mQualityCountText;
    private LinearLayout mVideoQualityList;
    private LinearLayout mAudioQualityList;
    private TextView mAudioSectionLabel;
    private LinearLayout mSubtitleList;
    private TextView mSubtitleSectionLabel;

    private DownloadService mDownloadService;
    private boolean mServiceBound;

    private String m720pUrl;
    private boolean mPreviewPlaying;
    private MediaPlayer mMediaPlayer;
    private boolean mSurfaceReady;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) service;
            mDownloadService = binder.getService();
            mServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mDownloadService = null;
            mServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        mVideoInfo = (VideoInfo) getIntent().getSerializableExtra("video_info");
        mIsLoggedIn = getIntent().getBooleanExtra("is_logged_in", false);
        mIsVip = getIntent().getBooleanExtra("is_vip", false);

        if (mVideoInfo == null) {
            finish();
            return;
        }

        initViews();
        displayVideoInfo();
        populateQualities();
        populateSubtitles();

        Intent serviceIntent = new Intent(this, DownloadService.class);
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        startService(new Intent(this, DownloadService.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
        stopPreview();
    }

    private void initViews() {
        mPreviewFrame = (FrameLayout) findViewById(R.id.previewFrame);
        mThumbnailImage = (ImageView) findViewById(R.id.thumbnailImage);
        mPreviewVideo = (SurfaceView) findViewById(R.id.previewVideo);
        mPreviewVideo.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceReady = true;
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format,
                                        int width, int height) {
                mSurfaceReady = true;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSurfaceReady = false;
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                }
            }
        });
        mPlayOverlay = findViewById(R.id.playOverlay);
        mBtnPlay = (Button) findViewById(R.id.btnPlay);
        mTitleText = (TextView) findViewById(R.id.titleText);
        mAuthorText = (TextView) findViewById(R.id.authorText);
        mDurationText = (TextView) findViewById(R.id.durationText);
        mViewText = (TextView) findViewById(R.id.viewText);
        mLikeText = (TextView) findViewById(R.id.likeText);
        mDescText = (TextView) findViewById(R.id.descText);
        mLoginHintText = (TextView) findViewById(R.id.loginHintText);
        mQualityCountText = (TextView) findViewById(R.id.qualityCountText);
        mVideoQualityList = (LinearLayout) findViewById(R.id.videoQualityList);
        mAudioQualityList = (LinearLayout) findViewById(R.id.audioQualityList);
        mAudioSectionLabel = (TextView) findViewById(R.id.audioSectionLabel);
        mSubtitleList = (LinearLayout) findViewById(R.id.subtitleList);
        mSubtitleSectionLabel = (TextView) findViewById(R.id.subtitleSectionLabel);

        final Button btnCopyLink = (Button) findViewById(R.id.btnCopyLink);
        btnCopyLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyOriginalLink();
            }
        });

        final Button btnShowDesc = (Button) findViewById(R.id.btnShowDesc);
        btnShowDesc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDescription();
            }
        });

        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePreview();
            }
        });

        mPlayOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePreview();
            }
        });
    }

    private void togglePreview() {
        if (mPreviewPlaying) {
            stopPreview();
        } else {
            startPreview();
        }
    }

    private void startPreview() {
        if (m720pUrl == null || m720pUrl.length() == 0) {
            Toast.makeText(this, "无可用预览流", Toast.LENGTH_SHORT).show();
            Logger.e("Detail", "预览失败: 无720P流地址");
            return;
        }

        // Release any existing player
        if (mMediaPlayer != null) {
            try { mMediaPlayer.release(); } catch (Exception ignored) {}
            mMediaPlayer = null;
        }

        mPlayOverlay.setVisibility(View.GONE);
        mThumbnailImage.setVisibility(View.GONE);
        mPreviewVideo.setVisibility(View.VISIBLE);
        mPreviewPlaying = true;
        mBtnPlay.setText("■");

        Logger.i("Detail", "开始加载预览: " + m720pUrl.substring(0,
                Math.min(60, m720pUrl.length())) + "...");

        mMediaPlayer = new MediaPlayer();
        try {
            final Map<String, String> headers = new HashMap<String, String>();
            headers.put("Referer", "https://www.bilibili.com");
            headers.put("Origin", "https://www.bilibili.com");
            headers.put("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.0) AppleWebKit/537.36 (KHTML, like Gecko)");

            Logger.i("Detail", "setDataSource url=" + m720pUrl.substring(0,
                    Math.min(80, m720pUrl.length())));
            mMediaPlayer.setDataSource(this, Uri.parse(m720pUrl), headers);

            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    int vw = mp.getVideoWidth();
                    int vh = mp.getVideoHeight();
                    Logger.i("Detail", "预览流就绪, 开始播放 width=" + vw + "x" + vh);

                    // Adjust SurfaceView to match video aspect ratio
                    if (vw > 0 && vh > 0) {
                        int containerWidth = mPreviewFrame.getWidth();
                        int containerHeight = mPreviewFrame.getHeight();
                        if (containerWidth > 0 && containerHeight > 0) {
                            float videoRatio = (float) vw / vh;
                            float containerRatio = (float) containerWidth / containerHeight;
                            FrameLayout.LayoutParams lp =
                                    (FrameLayout.LayoutParams) mPreviewVideo.getLayoutParams();
                            lp.gravity = android.view.Gravity.CENTER;
                            if (videoRatio > containerRatio) {
                                lp.width = containerWidth;
                                lp.height = (int) (containerWidth / videoRatio);
                            } else {
                                lp.height = containerHeight;
                                lp.width = (int) (containerHeight * videoRatio);
                            }
                            mPreviewVideo.setLayoutParams(lp);
                            Logger.i("Detail", "预览尺寸调整: " + lp.width + "x"
                                    + lp.height + " ratio=" + videoRatio);
                        }
                    }

                    mp.setDisplay(mPreviewVideo.getHolder());
                    mp.start();
                }
            });

            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Logger.e("Detail", "预览播放失败 what=" + what + " extra=" + extra);
                    Toast.makeText(DetailActivity.this,
                            "播放失败(code:" + extra + "), 请尝试下载",
                            Toast.LENGTH_SHORT).show();
                    stopPreview();
                    return true;
                }
            });

            mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    Logger.i("Detail", "预览 onInfo what=" + what
                            + " extra=" + extra);
                    return false;
                }
            });

            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Logger.i("Detail", "预览播放完成");
                    stopPreview();
                }
            });

            mMediaPlayer.setScreenOnWhilePlaying(true);
            Logger.i("Detail", "prepareAsync开始");
            mMediaPlayer.prepareAsync();
            Logger.i("Detail", "prepareAsync已调用");

        } catch (final Exception e) {
            Logger.e("Detail", "预览异常", e);
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopPreview();
        }
    }

    private void stopPreview() {
        mPreviewPlaying = false;
        mBtnPlay.setText("▶");
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (Exception ignored) {}
            try {
                mMediaPlayer.release();
            } catch (Exception ignored) {}
            mMediaPlayer = null;
        }
        mPreviewVideo.setVisibility(View.GONE);
        mThumbnailImage.setVisibility(View.VISIBLE);
        mPlayOverlay.setVisibility(View.VISIBLE);
    }

    private void displayVideoInfo() {
        mTitleText.setText(mVideoInfo.title);

        if (mVideoInfo.platform == VideoInfo.PLATFORM_BILIBILI) {
            mAuthorText.setText("UP: " + mVideoInfo.authorName);
        } else {
            mAuthorText.setText("@" + mVideoInfo.authorName);
        }

        mDurationText.setText(FileUtil.formatDuration(mVideoInfo.durationSeconds));
        mViewText.setText(formatCount(mVideoInfo.viewCount) + " 播放");
        mLikeText.setText(formatCount(mVideoInfo.likeCount) + " 点赞");

        mDescText.setText(mVideoInfo.description);

        if (!TextUtils.isEmpty(mVideoInfo.coverUrl)) {
            loadThumbnail(mVideoInfo.coverUrl);
        }
    }

    private void populateQualities() {
        // Find 720P URL for preview
        m720pUrl = findBestPreviewUrl();

        // Show all qualities
        int totalCount = mVideoInfo.videoQualities.size()
                + mVideoInfo.audioQualities.size();
        mQualityCountText.setText(totalCount + " 项");

        Logger.i("Detail", "视频画质: " + mVideoInfo.videoQualities.size()
                + "个, 音频: " + mVideoInfo.audioQualities.size()
                + "个, 字幕: " + mVideoInfo.subtitles.size()
                + "个, 720P预览: " + (m720pUrl != null ? "有" : "无"));

        // Show login hint if user isn't logged in
        if (!mIsLoggedIn && mVideoInfo.platform == VideoInfo.PLATFORM_BILIBILI) {
            mLoginHintText.setText("登录后可获取更多清晰度");
            mLoginHintText.setVisibility(View.VISIBLE);
        }

        // Video qualities
        mVideoQualityList.removeAllViews();
        for (int i = 0; i < mVideoInfo.videoQualities.size(); i++) {
            final QualityOption opt = mVideoInfo.videoQualities.get(i);
            View itemView = createQualityItemView(opt, false);
            mVideoQualityList.addView(itemView);
        }

        // Audio qualities
        if (mVideoInfo.audioQualities.size() > 0) {
            mAudioSectionLabel.setVisibility(View.VISIBLE);
            mAudioQualityList.removeAllViews();
            for (int i = 0; i < mVideoInfo.audioQualities.size(); i++) {
                final QualityOption opt = mVideoInfo.audioQualities.get(i);
                View itemView = createQualityItemView(opt, true);
                mAudioQualityList.addView(itemView);
            }
        } else {
            mAudioSectionLabel.setVisibility(View.GONE);
        }
    }

    /**
     * Find the best preview URL: prefer 720P, fallback to highest available.
     */
    private String findBestPreviewUrl() {
        String url720 = null;
        String bestUrl = null;
        int bestQn = 0;
        for (QualityOption opt : mVideoInfo.videoQualities) {
            if (opt.qualityCode == 64) { // 720P
                url720 = opt.url;
            }
            if (opt.qualityCode > bestQn && opt.url != null && opt.url.length() > 0) {
                bestQn = opt.qualityCode;
                bestUrl = opt.url;
            }
        }
        if (url720 != null) return url720;
        return bestUrl;
    }

    private View createQualityItemView(final QualityOption opt, final boolean isAudio) {
        View itemView = getLayoutInflater().inflate(R.layout.item_quality, null);

        final TextView nameText = (TextView) itemView.findViewById(R.id.qualityNameText);
        final TextView infoText = (TextView) itemView.findViewById(R.id.qualityInfoText);
        final TextView tagText = (TextView) itemView.findViewById(R.id.qualityTagText);
        final Button btnDownload = (Button) itemView.findViewById(R.id.btnDownload);

        // Build quality name with optional auth label
        String displayName = opt.qualityName;
        if (opt.needsAuth) {
            displayName = displayName + (mIsVip ? "" : " (需登录)");
        }
        nameText.setText(displayName);

        String infoStr = opt.format.toUpperCase();
        if (opt.fallbackUrls.size() > 0) {
            infoStr += " +" + opt.fallbackUrls.size() + "备选";
        }
        if (opt.fileSize > 0) {
            infoStr += "  ~" + FileUtil.formatSize(opt.fileSize);
        }
        infoText.setText(infoStr);

        // Visual treatment for qualities that need auth
        if (opt.needsAuth && !mIsLoggedIn) {
            // Show all but visually indicate needs login
            nameText.setTextColor(0xff888888);
            btnDownload.setEnabled(true);
            btnDownload.setBackgroundColor(0xfffb7299);
            btnDownload.setTextColor(0xffffffff);

            tagText.setVisibility(View.VISIBLE);
            tagText.setText("需登录");
            tagText.setBackgroundColor(0xff555555);
        } else {
            // Fully accessible
            nameText.setTextColor(0xffffffff);
            btnDownload.setEnabled(true);
            btnDownload.setBackgroundColor(0xfffb7299);
            btnDownload.setTextColor(0xffffffff);
            tagText.setVisibility(View.GONE);
        }

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAudio) {
                    startAudioDownload(opt);
                } else {
                    startVideoDownload(opt);
                }
            }
        });

        return itemView;
    }

    private void populateSubtitles() {
        if (mVideoInfo.subtitles.isEmpty()) {
            mSubtitleSectionLabel.setVisibility(View.GONE);
            return;
        }

        mSubtitleSectionLabel.setVisibility(View.VISIBLE);
        mSubtitleList.removeAllViews();

        for (int i = 0; i < mVideoInfo.subtitles.size(); i++) {
            final SubtitleOption sub = mVideoInfo.subtitles.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(14, 14, 14, 14);

            TextView nameText = new TextView(this);
            nameText.setText(sub.languageName + " (" + sub.format.toUpperCase() + ")");
            nameText.setTextColor(0xffffffff);
            nameText.setTextSize(14);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            nameText.setLayoutParams(nameParams);
            row.addView(nameText);

            Button downloadBtn = new Button(this);
            downloadBtn.setText("下载");
            downloadBtn.setTextSize(13);
            downloadBtn.setTextColor(0xffffffff);
            downloadBtn.setBackgroundColor(0xfffb7299);
            downloadBtn.setMinimumWidth(80);

            downloadBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startSubtitleDownload(sub);
                }
            });
            row.addView(downloadBtn);

            mSubtitleList.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(0xff1a1a1a);
            LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            mSubtitleList.addView(divider);
        }
    }

    private void startVideoDownload(QualityOption opt) {
        if (mDownloadService == null) {
            Toast.makeText(this, "下载服务未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        // Force .mp4 extension regardless of CDN URL extension
        String ext = ".mp4";

        String fileName = FileUtil.sanitizeFileName(mVideoInfo.title)
                + "_" + opt.qualityName + ext;
        String fullPath = FileUtil.getDownloadDir().getAbsolutePath()
                + java.io.File.separator + fileName;

        // Find best audio URL
        String bestAudioUrl = null;
        for (VideoInfo.QualityOption a : mVideoInfo.audioQualities) {
            if (a.qualityCode == 30280) {
                bestAudioUrl = a.url;
                break;
            }
        }
        if (bestAudioUrl == null && mVideoInfo.audioQualities.size() > 0) {
            bestAudioUrl = mVideoInfo.audioQualities.get(0).url;
        }

        mDownloadService.addTaskWithFallback(mVideoInfo.title, opt.url,
                opt.qualityName, ext, mVideoInfo.bvid, opt.fallbackUrls,
                bestAudioUrl, VideoInfo.PLATFORM_BILIBILI);
        showDownloadDialog(opt.qualityName, fullPath);
    }

    private void showDownloadDialog(String quality, final String path) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加到下载");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 16);

        TextView nameView = new TextView(this);
        nameView.setText(quality);
        nameView.setTextSize(16);
        nameView.setTextColor(0xffffffff);
        nameView.setPadding(0, 0, 0, 12);
        layout.addView(nameView);

        final ProgressBar progressBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(true);
        layout.addView(progressBar);

        final TextView statusView = new TextView(this);
        statusView.setText("准备下载...");
        statusView.setTextSize(12);
        statusView.setTextColor(0xff98989d);
        statusView.setPadding(0, 10, 0, 8);
        layout.addView(statusView);

        TextView pathView = new TextView(this);
        pathView.setText(path);
        pathView.setTextSize(10);
        pathView.setTextColor(0xff6e6e73);
        pathView.setSingleLine(false);
        pathView.setMaxLines(3);
        layout.addView(pathView);

        builder.setView(layout);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();

        // Poll progress
        final Handler handler = new Handler();
        final Runnable poller = new Runnable() {
            @Override
            public void run() {
                if (mDownloadService == null) return;
                com.jiexi.apppp.download.DownloadItem item = null;
                for (com.jiexi.apppp.download.DownloadItem t :
                        mDownloadService.getAllTasks()) {
                    if (path.endsWith(t.fileName)) { item = t; break; }
                }
                if (item != null) {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(item.progress);
                    if (item.totalSize > 0) {
                        statusView.setText(item.progress + "%  "
                                + com.jiexi.apppp.util.FileUtil.formatSize(item.downloadedSize)
                                + "/" + com.jiexi.apppp.util.FileUtil.formatSize(item.totalSize));
                    } else {
                        statusView.setText(item.progress + "%  下载中...");
                    }
                    if (item.status == com.jiexi.apppp.download.DownloadItem.STATUS_COMPLETED) {
                        statusView.setText("下载完成");
                        return;
                    }
                    if (item.status == com.jiexi.apppp.download.DownloadItem.STATUS_FAILED) {
                        statusView.setText("下载失败");
                        return;
                    }
                    handler.postDelayed(this, 500);
                }
            }
        };
        handler.postDelayed(poller, 300);
    }

    private void startAudioDownload(QualityOption opt) {
        if (mDownloadService == null) {
            Toast.makeText(this, "下载服务未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = FileUtil.sanitizeFileName(mVideoInfo.title)
                + "_仅音频_" + opt.qualityName + ".m4a";
        String fullPath = FileUtil.getDownloadDir().getAbsolutePath()
                + java.io.File.separator + fileName;

        mDownloadService.addTask(mVideoInfo.title, opt.url,
                "仅音频_" + opt.qualityName, ".m4a", mVideoInfo.bvid);
        showDownloadDialog("仅音频 " + opt.qualityName, fullPath);
    }

    private void startSubtitleDownload(SubtitleOption sub) {
        if (mDownloadService == null) {
            Toast.makeText(this, "下载服务未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = FileUtil.sanitizeFileName(mVideoInfo.title)
                + "_字幕_" + sub.languageName + "." + sub.format;
        String fullPath = FileUtil.getDownloadDir().getAbsolutePath()
                + java.io.File.separator + fileName;

        mDownloadService.addTask(mVideoInfo.title + "_字幕", sub.url,
                sub.languageName, "." + sub.format, mVideoInfo.bvid);
        Toast.makeText(this, "已添加字幕: " + sub.languageName
                + "\n路径: " + fullPath, Toast.LENGTH_LONG).show();
    }

    private void copyOriginalLink() {
        String link = "";
        if (mVideoInfo.platform == VideoInfo.PLATFORM_BILIBILI) {
            link = "https://www.bilibili.com/video/" + mVideoInfo.bvid;
        } else if (mVideoInfo.platform == VideoInfo.PLATFORM_DOUYIN) {
            link = "https://www.douyin.com/video/" + mVideoInfo.aid;
        }

        final String copyLink = link;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("video_link", copyLink);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show();
    }

    private void toggleDescription() {
        if (mDescText.getVisibility() == View.VISIBLE) {
            mDescText.setVisibility(View.GONE);
            ((Button) findViewById(R.id.btnShowDesc)).setText("查看简介");
        } else {
            mDescText.setVisibility(View.VISIBLE);
            ((Button) findViewById(R.id.btnShowDesc)).setText("收起简介");
        }
    }

    private void loadThumbnail(final String url) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("Referer", "https://www.bilibili.com/");
                    InputStream is = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mThumbnailImage.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private String formatCount(long count) {
        if (count >= 10000) {
            return String.format("%.1f万", count / 10000.0);
        }
        return String.valueOf(count);
    }
}
