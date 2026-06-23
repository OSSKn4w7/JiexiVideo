package com.jiexi.apppp.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jiexi.apppp.R;
import com.jiexi.apppp.api.BilibiliApi;
import com.jiexi.apppp.api.BilibiliApi.LoginStatus;
import com.jiexi.apppp.api.VideoInfo;
import com.jiexi.apppp.login.CookieManager;
import com.jiexi.apppp.util.LinkExtractor;
import com.jiexi.apppp.util.Logger;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BilibiliActivity extends Activity {

    private EditText mUrlInput;
    private Button mBtnParse;
    private LinearLayout mLoadingLayout;
    private TextView mErrorText;
    private LinearLayout mLoginStatusLayout;
    private ImageView mAvatarImage;
    private TextView mLoginStatusText;
    private TextView mLoginLevelText;
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
        setContentView(R.layout.activity_bilibili);

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

    private void initWbi() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                com.jiexi.apppp.util.WbiSignUtil.init();
            }
        }).start();
    }

    private void initViews() {
        mUrlInput = (EditText) findViewById(R.id.urlInput);
        mBtnParse = (Button) findViewById(R.id.btnParse);
        mLoadingLayout = (LinearLayout) findViewById(R.id.loadingLayout);
        mErrorText = (TextView) findViewById(R.id.errorText);
        mLoginStatusLayout = (LinearLayout) findViewById(R.id.loginStatusLayout);
        mAvatarImage = (ImageView) findViewById(R.id.avatarImage);
        mLoginStatusText = (TextView) findViewById(R.id.loginStatusText);
        mLoginLevelText = (TextView) findViewById(R.id.loginLevelText);
        mUserInfoCard = (LinearLayout) findViewById(R.id.userInfoCard);
        mUserAvatar = (ImageView) findViewById(R.id.userAvatar);
        mUserNameText = (TextView) findViewById(R.id.userNameText);
        mUserVipText = (TextView) findViewById(R.id.userVipText);
        mUserMidText = (TextView) findViewById(R.id.userMidText);
        mUserCookieInfo = (TextView) findViewById(R.id.userCookieInfo);
        mBtnLogout = (Button) findViewById(R.id.btnLogout);
        mHintCard = (LinearLayout) findViewById(R.id.hintCard);

        mBtnParse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parseVideo();
            }
        });

        mLoginStatusLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsLoggedIn) {
                    startActivity(new Intent(BilibiliActivity.this, LoginActivity.class));
                }
            }
        });

        mBtnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCookieManager.clearCookie();
                mIsLoggedIn = false;
                mIsVip = false;
                mUserInfoCard.setVisibility(View.GONE);
                mHintCard.setVisibility(View.VISIBLE);
                mLoginStatusText.setText("点击登录B站");
                mLoginStatusText.setTextColor(0xff98989d);
                mLoginLevelText.setVisibility(View.GONE);
                mAvatarImage.setImageResource(R.drawable.ic_launcher);
                Toast.makeText(BilibiliActivity.this, "已退出登录", Toast.LENGTH_SHORT).show();
                Logger.i("Bili", "用户退出登录");
            }
        });

        Button btnDownloads = (Button) findViewById(R.id.btnDownloads);
        btnDownloads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BilibiliActivity.this, DownloadListActivity.class);
                intent.putExtra("platform", VideoInfo.PLATFORM_BILIBILI);
                startActivity(intent);
            }
        });
    }

    private void checkLoginStatus() {
        Logger.i("Bili", "检查登录: cookie长度=" + mCookieManager.getCookie().length());
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
                                mLoginStatusText.setTextColor(0xffffffff);
                                if (status.userLevel > 0) {
                                    mLoginLevelText.setVisibility(View.VISIBLE);
                                    mLoginLevelText.setText("LV" + status.userLevel);
                                }
                                updateUserCard(status);
                                loadAvatarToBar(status.face);
                            } else if (hasCookie) {
                                mLoginStatusText.setText("Cookie已导入");
                                mLoginStatusText.setTextColor(0xffffcc00);
                                mLoginLevelText.setVisibility(View.GONE);
                                mIsLoggedIn = true;
                                showCookieCard(status);
                            } else {
                                mLoginStatusText.setText("点击登录B站");
                                mLoginStatusText.setTextColor(0xff98989d);
                                mLoginLevelText.setVisibility(View.GONE);
                                mAvatarImage.setImageResource(R.drawable.ic_launcher);
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
                                mLoginStatusText.setText("Cookie已导入");
                                mLoginStatusText.setTextColor(0xffffcc00);
                                mLoginLevelText.setVisibility(View.GONE);
                                mIsLoggedIn = true;
                                showCookieCard(null);
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void updateUserCard(LoginStatus status) {
        mUserInfoCard.setVisibility(View.VISIBLE);
        mHintCard.setVisibility(View.GONE);
        mUserNameText.setText(status.uname);
        if (status.isVip) {
            mUserVipText.setVisibility(View.VISIBLE);
            mUserVipText.setText("大会员");
            mUserVipText.setTextColor(0xffffffff);
            mUserVipText.setBackgroundColor(0xfffb7299);
        } else {
            mUserVipText.setVisibility(View.VISIBLE);
            mUserVipText.setText("普通用户");
            mUserVipText.setTextColor(0xff98989d);
            mUserVipText.setBackgroundColor(0x00000000);
        }
        String levelStr = "UID: " + status.mid;
        if (status.userLevel > 0) {
            levelStr += " · LV" + status.userLevel;
        }
        mUserMidText.setText(levelStr);
        mUserCookieInfo.setText("登录有效");
        if (status.face != null && status.face.length() > 0) {
            loadAvatar(status.face);
        }
        Logger.i("Bili", "用户卡: " + status.uname + " VIP=" + status.isVip
                + " LV=" + status.userLevel);
    }

    private void showCookieCard(LoginStatus status) {
        mUserInfoCard.setVisibility(View.VISIBLE);
        mHintCard.setVisibility(View.GONE);
        if (status != null && status.isLoggedIn) {
            mUserNameText.setText(status.uname);
            mUserMidText.setText("UID: " + status.mid);
        } else {
            mUserNameText.setText("Cookie用户");
            mUserMidText.setText("");
        }
        mUserNameText.setTextColor(0xffcccccc);
        mUserVipText.setVisibility(View.GONE);
        mUserCookieInfo.setText("Cookie无效，请重新登录");
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
                                mAvatarImage.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void loadAvatarToBar(final String url) {
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
                                mAvatarImage.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void parseVideo() {
        final String input = mUrlInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "请输入B站链接", Toast.LENGTH_SHORT).show();
            return;
        }
        // Dev mode
        if ("开发者模式".equals(input)) {
            showLogViewer();
            return;
        }

        final String cleanUrl = LinkExtractor.extract(input);
        Logger.i("Bili", "提取URL=" + cleanUrl);
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
                    final VideoInfo[] infoRef = new VideoInfo[1];
                    String bvid = BilibiliApi.extractBvid(cleanUrl);
                    Logger.i("Bili", "bvid=" + bvid);
                    if (bvid == null || bvid.length() < 10) {
                        showError("无法识别B站视频ID");
                        return;
                    }
                    infoRef[0] = BilibiliApi.fetchVideoInfo(bvid);
                    BilibiliApi.fetchPlayUrls(infoRef[0]);
                    BilibiliApi.fetchSubtitles(infoRef[0]);
                    Logger.i("Bili", "解析成功: " + infoRef[0].title);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLoadingLayout.setVisibility(View.GONE);
                            mBtnParse.setEnabled(true);
                            Intent intent = new Intent(BilibiliActivity.this, DetailActivity.class);
                            intent.putExtra("video_info", infoRef[0]);
                            intent.putExtra("is_logged_in", mIsLoggedIn);
                            intent.putExtra("is_vip", mIsVip);
                            startActivity(intent);
                        }
                    });
                } catch (final Exception e) {
                    Logger.e("Bili", "解析失败", e);
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

    private void showLogViewer() {
        final android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(BilibiliActivity.this);
        builder.setTitle("开发者日志");
        final String logs = Logger.getLogsAsString();
        if (logs.length() == 0) {
            builder.setMessage("暂无日志记录");
        } else {
            final android.widget.ScrollView sv = new android.widget.ScrollView(this);
            final TextView tv = new TextView(this);
            tv.setText(logs);
            tv.setTextSize(11);
            tv.setTextColor(0xff00ff00);
            tv.setBackgroundColor(0xff000000);
            tv.setPadding(16, 16, 16, 16);
            tv.setHorizontallyScrolling(true);
            sv.addView(tv);
            builder.setView(sv);
        }
        builder.setPositiveButton("保存到文件",
                new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String path = Logger.saveToFile();
                if (path != null) {
                    Toast.makeText(BilibiliActivity.this,
                            "日志已保存:\n" + path, Toast.LENGTH_LONG).show();
                } else {
                    try {
                        Intent si = new Intent(Intent.ACTION_SEND);
                        si.setType("text/plain");
                        si.putExtra(Intent.EXTRA_TEXT, Logger.getLogsAsString());
                        si.putExtra(Intent.EXTRA_SUBJECT, "解析App日志");
                        startActivity(Intent.createChooser(si, "保存日志"));
                    } catch (Exception ex) {
                        Toast.makeText(BilibiliActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("清除日志",
                new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                Logger.clear();
                Toast.makeText(BilibiliActivity.this, "日志已清除", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNeutralButton("关闭", null);
        builder.show();
        Logger.i("Bili", "打开日志查看器");
    }
}
