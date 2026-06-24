package com.jiexi.apppp.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ThemeHelper {

    // Dark theme
    public static final int DARK_BG         = 0xff08080c;
    public static final int DARK_SURFACE    = 0xff14141a;
    public static final int DARK_CARD       = 0xff1a1a22;
    public static final int DARK_INPUT      = 0xff1e1e28;
    public static final int DARK_BTN_SEC    = 0xff22222e;
    public static final int DARK_TEXT       = 0xffffffff;
    public static final int DARK_TEXT_SEC   = 0xff98989d;
    public static final int DARK_TEXT_MUTED = 0xff6e6e73;
    public static final int DARK_HINT       = 0xff48484d;
    public static final int DARK_DIVIDER    = 0xff18ffffff;

    // Light theme
    public static final int LIGHT_BG         = 0xffffffff;
    public static final int LIGHT_SURFACE    = 0xfff5f5f7;
    public static final int LIGHT_CARD       = 0xffffffff;
    public static final int LIGHT_INPUT      = 0xfff0f0f3;
    public static final int LIGHT_BTN_SEC    = 0xffe8e8ed;
    public static final int LIGHT_TEXT       = 0xff1d1d1f;
    public static final int LIGHT_TEXT_SEC   = 0xff86868b;
    public static final int LIGHT_TEXT_MUTED = 0xffaeaeb2;
    public static final int LIGHT_HINT       = 0xffaeaeb2;
    public static final int LIGHT_DIVIDER    = 0xffd1d1d6;

    // Bilibili pink (same in both modes)
    public static final int BILI_PINK = 0xfffb7299;

    public static int bg(boolean light)       { return light ? LIGHT_BG : DARK_BG; }
    public static int surface(boolean light)  { return light ? LIGHT_SURFACE : DARK_SURFACE; }
    public static int card(boolean light)     { return light ? LIGHT_CARD : DARK_CARD; }
    public static int input(boolean light)    { return light ? LIGHT_INPUT : DARK_INPUT; }
    public static int btnSec(boolean light)   { return light ? LIGHT_BTN_SEC : DARK_BTN_SEC; }
    public static int text(boolean light)     { return light ? LIGHT_TEXT : DARK_TEXT; }
    public static int textSec(boolean light)  { return light ? LIGHT_TEXT_SEC : DARK_TEXT_SEC; }
    public static int hint(boolean light)     { return light ? LIGHT_HINT : DARK_HINT; }
    public static int divider(boolean light)  { return light ? LIGHT_DIVIDER : DARK_DIVIDER; }

    /**
     * Recursively set background on known view types
     */
    public static void applyTheme(View root, boolean light) {
        if (root == null) return;
        String tag = (String) root.getTag();
        if ("keep".equals(tag)) return;

        if (root instanceof LinearLayout) {
            if (!"nobg".equals(tag)) {
                int id = root.getId();
                if (id == android.R.id.content || id == 0) {
                    root.setBackgroundColor(bg(light));
                }
            }
        }

        if (root instanceof EditText) {
            root.setBackgroundColor(input(light));
            ((EditText) root).setTextColor(text(light));
            ((EditText) root).setHintTextColor(hint(light));
        }

        if (root instanceof Button) {
            ((Button) root).setTextColor(textSec(light));
        }

        if (root instanceof TextView) {
            String tvTag = (String) root.getTag();
            if ("primary".equals(tvTag)) {
                ((TextView) root).setTextColor(text(light));
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTheme(vg.getChildAt(i), light);
            }
        }
    }
}
