package com.jiexi.apppp.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.jiexi.apppp.R;
import com.jiexi.apppp.util.Logger;

public class LoginActivity extends Activity {

    private WebView mWebView;
    private EditText mCookieInput;
    private Button mBtnImport;

    private com.jiexi.apppp.login.CookieManager mCookieManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mCookieManager = com.jiexi.apppp.login.CookieManager.getInstance(this);

        initViews();
        setupWebView();
    }

    private void initViews() {
        Button btnBack = (Button) findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mWebView = (WebView) findViewById(R.id.loginWebView);
        mCookieInput = (EditText) findViewById(R.id.cookieInput);
        mBtnImport = (Button) findViewById(R.id.btnImportCookie);

        mBtnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importCookie();
            }
        });
    }

    private void setupWebView() {
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.getSettings().setUserAgentString(
                "Mozilla/5.0 (Linux; Android 8.0; Pixel 2) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36");

        CookieManager.getInstance().removeAllCookie();

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Check if logged in after each page load
                if (url.contains("bilibili.com") && !url.contains("passport")) {
                    // Navigated to main page means login might have happened
                    extractCookiesAndSave();
                }
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient());

        // Load Bilibili login page with QR code
        mWebView.loadUrl("https://passport.bilibili.com/h5-app/passport/login");
    }

    private void extractCookiesAndSave() {
        // Collect cookies from multiple B站 subdomains for complete capture
        StringBuilder allCookies = new StringBuilder();
        String[] urls = {
                "https://bilibili.com",
                "https://www.bilibili.com",
                "https://api.bilibili.com",
                "https://passport.bilibili.com"
        };
        java.util.HashSet<String> seenKeys = new java.util.HashSet<String>();

        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            String domainCookies = CookieManager.getInstance().getCookie(url);
            if (domainCookies != null && domainCookies.length() > 0) {
                String[] pairs = domainCookies.split(";");
                for (int j = 0; j < pairs.length; j++) {
                    String pair = pairs[j].trim();
                    if (pair.length() == 0) continue;
                    String key = pair.split("=", 2)[0];
                    if (!seenKeys.contains(key)) {
                        seenKeys.add(key);
                        if (allCookies.length() > 0) allCookies.append("; ");
                        allCookies.append(pair);
                    }
                }
            }
        }

        final String cookieStr = allCookies.toString();
        Logger.i("Login", "提取Cookie: " + seenKeys.size() + "个键, 长度=" + cookieStr.length());
        if (!TextUtils.isEmpty(cookieStr)) {
            mCookieManager.saveCookie(cookieStr);
            com.jiexi.apppp.util.HttpUtil.setGlobalCookie(cookieStr);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(LoginActivity.this,
                            "登录成功！Cookie 已保存 (" + cookieStr.length() + "字)",
                            Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }
            });
        }
    }

    private void importCookie() {
        final String cookieStr = mCookieInput.getText().toString().trim();
        if (TextUtils.isEmpty(cookieStr)) {
            Toast.makeText(this, "请粘贴 Cookie 字符串", Toast.LENGTH_SHORT).show();
            return;
        }

        mCookieManager.saveCookie(cookieStr);
        com.jiexi.apppp.util.HttpUtil.setGlobalCookie(cookieStr);

        // Verify login
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final com.jiexi.apppp.api.BilibiliApi.LoginStatus status =
                            com.jiexi.apppp.api.BilibiliApi.checkLoginStatus();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (status.isLoggedIn) {
                                mCookieManager.setVip(status.isVip);
                                Toast.makeText(LoginActivity.this,
                                        "Cookie 导入成功！用户: " + status.uname,
                                        Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            } else {
                                Toast.makeText(LoginActivity.this,
                                        "Cookie 无效或已过期，请重新获取", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(LoginActivity.this,
                                    "验证失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
