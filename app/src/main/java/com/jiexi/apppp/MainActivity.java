package com.jiexi.apppp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jiexi.apppp.util.LinkExtractor;
import com.jiexi.apppp.util.Logger;
import com.jiexi.apppp.api.BilibiliApi;
import com.jiexi.apppp.api.BilibiliApi.LoginStatus;
import com.jiexi.apppp.api.DouyinApi;
import com.jiexi.apppp.api.VideoInfo;
import com.jiexi.apppp.login.CookieManager;
import com.jiexi.apppp.ui.DetailActivity;
import com.jiexi.apppp.ui.DownloadListActivity;
import com.jiexi.apppp.ui.LoginActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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

    // User info card
    private LinearLayout mUserInfoCard;
    private ImageView mUserAvatar;
    private TextView mUserNameText;
    private TextView mUserVipText;
    private TextView mUserMidText;
    private TextView mUserCookieInfo;
    private Button mBtnLogout;
    private LinearLayout mHintCard;

    private CookieManager mCookieManager;
    private boolean mIsLoggedIn;
    private boolean mIsVip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCookieManager = CookieManager.getInstance(this);
        requestStoragePermission();

        initViews();
        checkLoginStatus();
        initWbi();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
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

        mUserInfoCard = (LinearLayout) findViewById(R.id.userInfoCard);
        mUserAvatar = (ImageView) findViewById(R.id.userAvatar);
        mUserNameText = (TextView) findViewById(R.id.userNameText);
        mUserVipText = (TextView) findViewById(R.id.userVipText);
        mUserMidText = (TextView) findViewById(R.id.userMidText);
        mUserCookieInfo = (TextView) findViewById(R.id.userCookieInfo);
        mBtnLogout = (Button) findViewById(R.id.btnLogout);
        mHintCard = (LinearLayout) findViewById(R.id.hintCard);

        mBtnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCookieManager.clearCookie();
                mIsLoggedIn = false;
                mIsVip = false;
                mUserInfoCard.setVisibility(View.GONE);
                mHintCard.setVisibility(View.VISIBLE);
                mLoginStatusText.setText("未登录 (点击登录)");
                mLoginStatusText.setTextColor(0xffaaaaaa);
                mAvatarImage.setImageResource(R.drawable.ic_launcher);
                Toast.makeText(MainActivity.this, "已退出登录", Toast.LENGTH_SHORT).show();
                Logger.i("Main", "用户退出登录");
            }
        });

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
        final String cookie = mCookieManager.getCookie();
        Logger.i("Main", "检查登录: cookie长度=" + (cookie != null ? cookie.length() : 0));
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
                                updateUserCard(status);
                                if (hasCookie) {
                                    mCookieManager.setVip(status.isVip);
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
                            } else if (hasCookie) {
                                mLoginStatusText.setText("Cookie 已导入");
                                mLoginStatusText.setTextColor(0xffffcc00);
                                mIsLoggedIn = true;
                                showCookieCard(status);
                            } else {
                                mLoginStatusText.setText("未登录 (点击登录)");
                                mLoginStatusText.setTextColor(0xffaaaaaa);
                                mUserInfoCard.setVisibility(View.GONE);
                                mHintCard.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mCookieManager.hasCookie()) {
                                mLoginStatusText.setText("Cookie 已导入");
                                mLoginStatusText.setTextColor(0xffffcc00);
                                mIsLoggedIn = true;
                                showCookieCard(null);
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

        // Dev mode trigger
        if ("开发者模式".equals(input)) {
            showLogViewer();
            return;
        }

        // Auto-extract clean URL from pasted share text
        final String cleanUrl = LinkExtractor.extract(input);
        Logger.i("Main", "原始输入长度=" + input.length()
                + " 提取URL=" + (cleanUrl != null ? cleanUrl : "null"));

        if (cleanUrl == null) {
            showError("未识别到有效的视频链接");
            return;
        }

        // Auto-detect platform from extracted URL
        final int detectedPlatform = LinkExtractor.detectPlatform(cleanUrl);
        Logger.i("Main", "检测平台=" + (detectedPlatform == VideoInfo.PLATFORM_BILIBILI
                ? "B站" : detectedPlatform == VideoInfo.PLATFORM_DOUYIN ? "抖音" : "未知")
                + " 登录态=" + mIsLoggedIn + " VIP=" + mIsVip);

        mErrorText.setVisibility(View.GONE);
        mLoadingLayout.setVisibility(View.VISIBLE);
        mBtnParse.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final VideoInfo[] infoRef = new VideoInfo[1];
                    if (detectedPlatform == VideoInfo.PLATFORM_DOUYIN) {
                        Logger.i("Main", "开始解析抖音: " + cleanUrl);
                        infoRef[0] = DouyinApi.parseVideo(cleanUrl);
                    } else {
                        String bvid = BilibiliApi.extractBvid(cleanUrl);
                        Logger.i("Main", "B站 bvid=" + bvid);
                        if (bvid == null || bvid.length() < 10) {
                            showError("无法识别B站视频ID");
                            return;
                        }
                        infoRef[0] = BilibiliApi.fetchVideoInfo(bvid);
                        BilibiliApi.fetchPlayUrls(infoRef[0]);
                        BilibiliApi.fetchSubtitles(infoRef[0]);
                        Logger.i("Main", "B站解析成功: " + infoRef[0].title);
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
                    Logger.e("Main", "解析失败", e);
                    showError(e.getMessage());
                }
            }
        }).start();
    }

    private void updateUserCard(final LoginStatus status) {
        mUserInfoCard.setVisibility(View.VISIBLE);
        mHintCard.setVisibility(View.GONE);

        mUserNameText.setText(status.uname);
        mUserNameText.setTextColor(0xffffffff);

        if (status.isVip) {
            mUserVipText.setVisibility(View.VISIBLE);
            mUserVipText.setText("大会员");
            mUserVipText.setTextColor(0xffff6b00);
        } else {
            mUserVipText.setVisibility(View.GONE);
        }

        mUserMidText.setText("UID: " + status.mid);
        mUserCookieInfo.setText("登录有效");

        // Load avatar
        if (status.face != null && status.face.length() > 0) {
            loadAvatar(status.face);
        }

        Logger.i("Main", "用户卡: " + status.uname + " VIP=" + status.isVip);
    }

    private void showCookieCard(final LoginStatus status) {
        mUserInfoCard.setVisibility(View.VISIBLE);
        mHintCard.setVisibility(View.GONE);

        if (status != null && status.isLoggedIn) {
            mUserNameText.setText(status.uname);
            mUserVipText.setVisibility(View.GONE);
            mUserMidText.setText("UID: " + status.mid);
        } else {
            mUserNameText.setText("Cookie 用户");
            mUserVipText.setVisibility(View.GONE);
            mUserMidText.setText("");
        }

        mUserNameText.setTextColor(0xffcccccc);
        mUserCookieInfo.setText("Cookie已导入但未验证，请重新登录");
        mUserCookieInfo.setTextColor(0xffff6b00);
    }

    private void loadAvatar(final String url) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    InputStream is = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mUserAvatar.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) {}
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

    private void showLogViewer() {
        final android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(MainActivity.this);
        builder.setTitle("开发者日志");

        final String logs = Logger.getLogsAsString();
        if (logs.length() == 0) {
            builder.setMessage("暂无日志记录");
        } else {
            final android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            final TextView logView = new TextView(this);
            logView.setText(logs);
            logView.setTextSize(11);
            logView.setTextColor(0xff00ff00);
            logView.setBackgroundColor(0xff000000);
            logView.setPadding(16, 16, 16, 16);
            logView.setHorizontallyScrolling(true);
            scrollView.addView(logView);
            builder.setView(scrollView);
        }

        builder.setPositiveButton("保存到文件",
                new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                final String path = Logger.saveToFile();
                if (path != null) {
                    Toast.makeText(MainActivity.this,
                            "日志已保存:\n" + path, Toast.LENGTH_LONG).show();
                    Logger.i("Main", "日志已保存到: " + path);
                } else {
                    // Fallback: share via system file manager
                    try {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, Logger.getLogsAsString());
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "解析App日志");
                        startActivity(Intent.createChooser(shareIntent, "保存日志到..."));
                        Logger.i("Main", "文件保存失败，转为分享日志");
                    } catch (Exception ex) {
                        Toast.makeText(MainActivity.this,
                                "保存和分享均失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        builder.setNegativeButton("清除日志",
                new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                Logger.clear();
                Toast.makeText(MainActivity.this,
                        "日志已清除", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("关闭", null);
        builder.show();

        Logger.i("Main", "打开日志查看器");
    }
}
