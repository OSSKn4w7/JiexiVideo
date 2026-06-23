package com.jiexi.apppp.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jiexi.apppp.R;
import com.jiexi.apppp.api.DouyinApi;
import com.jiexi.apppp.api.VideoInfo;
import com.jiexi.apppp.download.DownloadService;
import com.jiexi.apppp.util.FileUtil;
import com.jiexi.apppp.util.LinkExtractor;
import com.jiexi.apppp.util.Logger;

import java.io.File;

public class DouyinActivity extends Activity {

    private EditText mUrlInput;
    private Button mBtnParse;
    private LinearLayout mLoadingLayout;
    private TextView mErrorText;

    private DownloadService mDownloadService;
    private boolean mServiceBound;

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
        setContentView(R.layout.activity_douyin);

        initViews();

        Intent si = new Intent(this, DownloadService.class);
        bindService(si, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }

    private void initViews() {
        mUrlInput = (EditText) findViewById(R.id.urlInput);
        mBtnParse = (Button) findViewById(R.id.btnParse);
        mLoadingLayout = (LinearLayout) findViewById(R.id.loadingLayout);
        mErrorText = (TextView) findViewById(R.id.errorText);

        mBtnParse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parseVideo();
            }
        });

        Button btnDownloads = (Button) findViewById(R.id.btnDownloads);
        btnDownloads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DouyinActivity.this, DownloadListActivity.class);
                intent.putExtra("platform", VideoInfo.PLATFORM_DOUYIN);
                startActivity(intent);
            }
        });
    }

    private void parseVideo() {
        final String input = mUrlInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "请输入抖音分享链接", Toast.LENGTH_SHORT).show();
            return;
        }

        final String cleanUrl = LinkExtractor.extract(input);
        Logger.i("Douyin", "提取URL=" + cleanUrl);
        if (cleanUrl == null) {
            showError("未识别到有效链接");
            return;
        }

        mErrorText.setVisibility(View.GONE);
        mLoadingLayout.setVisibility(View.VISIBLE);
        mBtnParse.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Logger.i("Douyin", "开始解析: " + cleanUrl);
                    final VideoInfo info = DouyinApi.parseVideo(cleanUrl);
                    Logger.i("Douyin", "解析成功: " + info.title);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingLayout.setVisibility(View.GONE);
                            mBtnParse.setEnabled(true);
                            showDouyinDownload(info);
                        }
                    });
                } catch (final Exception e) {
                    Logger.e("Douyin", "解析失败", e);
                    showError(e.getMessage());
                }
            }
        }).start();
    }

    private void showDouyinDownload(final VideoInfo info) {
        VideoInfo.QualityOption opt = info.videoQualities.get(0);
        if (mDownloadService == null) {
            Toast.makeText(this, "下载服务未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = FileUtil.sanitizeFileName(info.title) + ".mp4";
        String fullPath = FileUtil.getDownloadDir().getAbsolutePath()
                + java.io.File.separator + fileName;

        mDownloadService.addTaskWithFallback(info.title, opt.url,
                "原画", ".mp4", info.aid, null, null, VideoInfo.PLATFORM_DOUYIN);
        Toast.makeText(this, "已添加下载: " + info.title
                + "\n路径: " + fullPath, Toast.LENGTH_LONG).show();
    }

    private void showError(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingLayout.setVisibility(View.GONE);
                mBtnParse.setEnabled(true);
                mErrorText.setText(msg);
                mErrorText.setVisibility(View.VISIBLE);
            }
        });
    }
}
