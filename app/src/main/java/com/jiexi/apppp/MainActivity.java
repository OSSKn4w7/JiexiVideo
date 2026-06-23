package com.jiexi.apppp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jiexi.apppp.util.LinkExtractor;
import com.jiexi.apppp.api.BilibiliApi;
import com.jiexi.apppp.api.BilibiliApi.LoginStatus;
import com.jiexi.apppp.api.DouyinApi;
import com.jiexi.apppp.api.VideoInfo;
import com.jiexi.apppp.login.CookieManager;
import com.jiexi.apppp.ui.DetailActivity;
import com.jiexi.apppp.ui.DownloadListActivity;
import com.jiexi.apppp.ui.LoginActivity;

public class MainActivity extends Activity {

    private static final int TAB_BILIBILI = 0;
    private static final int TAB_DOUYIN = 1;

    private int mCurrentTab = TAB_BILIBILI;

    private Button mTabBilibili;
    private Button mTabDouyin;
    private EditText mUrlInput;
    private Button mBtnParse;
    private LinearLayout mLoadingLayout;
    private TextView mErrorText;

    private LinearLayout mLoginStatusLayout;
    private ImageView mAvatarImage;
    private TextView mLoginStatusText;

    private CookieManager mCookieManager;
    private boolean mIsLoggedIn;
    private boolean mIsVip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCookieManager = CookieManager.getInstance(this);

        initViews();
        checkLoginStatus();
        initWbi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLoginStatus();
    }

    private void initViews() {
        mTabBilibili = (Button) findViewById(R.id.tabBilibili);
        mTabDouyin = (Button) findViewById(R.id.tabDouyin);
        mUrlInput = (EditText) findViewById(R.id.urlInput);
        mBtnParse = (Button) findViewById(R.id.btnParse);
        mLoadingLayout = (LinearLayout) findViewById(R.id.loadingLayout);
        mErrorText = (TextView) findViewById(R.id.errorText);
        mLoginStatusLayout = (LinearLayout) findViewById(R.id.loginStatusLayout);
        mAvatarImage = (ImageView) findViewById(R.id.avatarImage);
        mLoginStatusText = (TextView) findViewById(R.id.loginStatusText);

        mTabBilibili.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_BILIBILI);
            }
        });

        mTabDouyin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(TAB_DOUYIN);
            }
        });

        mBtnParse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parseVideo();
            }
        });

        mLoginStatusLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });

        Button btnDownloads = (Button) findViewById(R.id.btnDownloads);
        btnDownloads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DownloadListActivity.class);
                startActivity(intent);
            }
        });
    }

    private void switchTab(int tab) {
        mCurrentTab = tab;
        if (tab == TAB_BILIBILI) {
            mTabBilibili.setBackgroundColor(0xfffb7299);
            mTabBilibili.setTextColor(0xffffffff);
            mTabDouyin.setBackgroundColor(0xff272727);
            mTabDouyin.setTextColor(0xff888888);
            mUrlInput.setHint("粘贴B站视频链接或BV号...");
        } else {
            mTabDouyin.setBackgroundColor(0xfffb7299);
            mTabDouyin.setTextColor(0xffffffff);
            mTabBilibili.setBackgroundColor(0xff272727);
            mTabBilibili.setTextColor(0xff888888);
            mUrlInput.setHint("粘贴抖音分享链接...");
        }
    }

    private void initWbi() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                com.jiexi.apppp.util.WbiSignUtil.init();
            }
        }).start();
    }

    private void checkLoginStatus() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final LoginStatus status = BilibiliApi.checkLoginStatus();
                    final boolean hasCookie = mCookieManager.hasCookie();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsLoggedIn = status.isLoggedIn;
                            mIsVip = status.isVip;

                            mCookieManager.setVip(status.isVip);

                            if (status.isLoggedIn) {
                                mLoginStatusText.setText(status.uname);
                                mLoginStatusText.setTextColor(0xff88c0ff);
                                if (hasCookie) {
                                    mCookieManager.setVip(status.isVip);
                                }
                            } else if (hasCookie) {
                                mLoginStatusText.setText("Cookie 已导入");
                                mLoginStatusText.setTextColor(0xff88c0ff);
                                mIsLoggedIn = true;
                            } else {
                                mLoginStatusText.setText("未登录 (点击登录)");
                                mLoginStatusText.setTextColor(0xffaaaaaa);
                            }
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mCookieManager.hasCookie()) {
                                mLoginStatusText.setText("Cookie 已导入");
                                mLoginStatusText.setTextColor(0xff88c0ff);
                                mIsLoggedIn = true;
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void parseVideo() {
        final String input = mUrlInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "请输入分享链接", Toast.LENGTH_SHORT).show();
            return;
        }

        // Auto-extract clean URL from pasted share text
        final String cleanUrl = LinkExtractor.extract(input);
        if (cleanUrl == null) {
            showError("未识别到有效的视频链接");
            return;
        }

        // Auto-detect platform from extracted URL
        final int detectedPlatform = LinkExtractor.detectPlatform(cleanUrl);

        mErrorText.setVisibility(View.GONE);
        mLoadingLayout.setVisibility(View.VISIBLE);
        mBtnParse.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final VideoInfo[] infoRef = new VideoInfo[1];
                    if (detectedPlatform == VideoInfo.PLATFORM_DOUYIN) {
                        infoRef[0] = DouyinApi.parseVideo(cleanUrl);
                    } else {
                        String bvid = BilibiliApi.extractBvid(cleanUrl);
                        if (bvid == null || bvid.length() < 10) {
                            showError("无法识别B站视频ID");
                            return;
                        }
                        infoRef[0] = BilibiliApi.fetchVideoInfo(bvid);
                        BilibiliApi.fetchPlayUrls(infoRef[0]);
                        BilibiliApi.fetchSubtitles(infoRef[0]);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingLayout.setVisibility(View.GONE);
                            mBtnParse.setEnabled(true);

                            Intent intent = new Intent(MainActivity.this,
                                    DetailActivity.class);
                            intent.putExtra("video_info", infoRef[0]);
                            intent.putExtra("is_logged_in", mIsLoggedIn);
                            intent.putExtra("is_vip", mIsVip);
                            startActivity(intent);
                        }
                    });
                } catch (final Exception e) {
                    showError(e.getMessage());
                }
            }
        }).start();
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
