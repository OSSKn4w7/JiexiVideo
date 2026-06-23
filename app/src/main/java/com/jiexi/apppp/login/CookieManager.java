package com.jiexi.apppp.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.jiexi.apppp.util.HttpUtil;

public class CookieManager {

    private static final String PREFS_NAME = "bilibili_cookies";
    private static final String KEY_COOKIE = "cookie_string";
    private static final String KEY_IS_VIP = "is_vip";

    private final SharedPreferences mPrefs;

    private static CookieManager sInstance;

    public static synchronized CookieManager getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new CookieManager(ctx.getApplicationContext());
        }
        return sInstance;
    }

    private CookieManager(Context ctx) {
        mPrefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Load saved cookie into HttpUtil on startup
        String saved = getCookie();
        if (!TextUtils.isEmpty(saved)) {
            HttpUtil.setGlobalCookie(saved);
        }
    }

    public void saveCookie(String cookie) {
        mPrefs.edit().putString(KEY_COOKIE, cookie).apply();
        HttpUtil.setGlobalCookie(cookie);
    }

    public String getCookie() {
        return mPrefs.getString(KEY_COOKIE, "");
    }

    public void setVip(boolean isVip) {
        mPrefs.edit().putBoolean(KEY_IS_VIP, isVip).apply();
    }

    public boolean isVip() {
        return mPrefs.getBoolean(KEY_IS_VIP, false);
    }

    public boolean hasCookie() {
        return !TextUtils.isEmpty(getCookie());
    }

    public void clearCookie() {
        mPrefs.edit().remove(KEY_COOKIE).remove(KEY_IS_VIP).apply();
        HttpUtil.setGlobalCookie("");
    }
}
