package io.whale.ncmlite;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 弹窗 UI：色板 / 色相盘 / 详情卡片 */
public class Ui {

    public interface ColorCallback {
        void onColor(int color);
    }

    private static final int[] PALETTE = {
            0xCC1F6FEB, 0xCC06B6D4, 0xCC10B981, 0xCC84CC16,
            0xCCEAB308, 0xCCF97316, 0xCCEF4444, 0xCCEC4899,
            0xCCA855F7, 0xCC6366F1, 0xCC64748B, 0xCCE6EDF3,
    };

    // ---------------- 通用 ----------------

    private static Dialog base(Context ctx) {
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private static void sizeWindow(Dialog d, Context ctx, float ratio) {
        try {
            d.setCanceledOnTouchOutside(true);
            Window w = d.getWindow();
            if (w == null) return;
            w.setType(Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * ratio);
            w.setAttributes(lp);
        } catch (Throwable ignored) {}
    }

    private static LinearLayout card(Context ctx) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF17202B, 0xFF0E141C});
        bg.setCornerRadius(dp(ctx, 20));
        bg.setStroke(dp(ctx, 1), 0xFF223247);
        c.setBackground(bg);
        return c;
    }

    private static TextView label(Context ctx, String t, int color, float sp, boolean bold) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private static TextView item(Context ctx, final Dialog d, String text,
                                 final View.OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(0xFFE6EDF3);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        t.setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 14));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (d.isShowing()) d.dismiss();
                } catch (Throwable ignored) {}
                try {
                    if (l != null) l.onClick(v);
                } catch (Throwable ignored) {}
            }
        });
        return t;
    }

    static TextView menuItem(Context ctx, Dialog d, String text, View.OnClickListener l) {
        return item(ctx, d, text, l);
    }

    /** 切换型菜单项：点击后不关闭菜单，方便看到状态变化 */
    static TextView toggleItem(Context ctx, String text, final View.OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(0xFFE6EDF3);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        t.setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 14));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (l != null) l.onClick(v);
                } catch (Throwable ignored) {}
            }
        });
        return t;
    }

    static LinearLayout menuCard(Context ctx) {
        return card(ctx);
    }

    static void menuWindow(Dialog d, Context ctx, float ratio) {
        sizeWindow(d, ctx, ratio);
    }

    static Dialog menuDialog(Context ctx) {
        return base(ctx);
    }

    public interface SizeCallback {
        void onSize(int heightDp, float textSp);
    }

    /** 胶囊尺寸调节：高度 / 字号，各带 − ＋ */
    public static void showSizePicker(final Context ctx, int curH, float curSp,
                                      final SizeCallback cb) {
        try {
            final Dialog d = base(ctx);
            LinearLayout c = card(ctx);
            final int[] h = {curH};
            final float[] spv = {curSp};

            TextView title = label(ctx, "胶囊尺寸", 0xFF7D8590, 11f, false);
            title.setPadding(dp(ctx, 4), 0, 0, dp(ctx, 10));
            c.addView(title);

            c.addView(adjustRow(ctx, "高度", h[0] + " dp",
                    new Runnable() {
                        @Override
                        public void run() {
                            h[0] = Math.max(20, h[0] - 2);
                            cb.onSize(h[0], spv[0]);
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            h[0] = Math.min(52, h[0] + 2);
                            cb.onSize(h[0], spv[0]);
                        }
                    }, true));

            c.addView(adjustRow(ctx, "字号", String.format(java.util.Locale.US, "%.1f sp", spv[0]),
                    new Runnable() {
                        @Override
                        public void run() {
                            spv[0] = Math.max(9f, spv[0] - 0.5f);
                            cb.onSize(h[0], spv[0]);
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            spv[0] = Math.min(18f, spv[0] + 0.5f);
                            cb.onSize(h[0], spv[0]);
                        }
                    }, false));

            TextView tip = label(ctx, "改完会立刻生效，关闭后记住", 0xFF484F58, 10f, false);
            tip.setPadding(0, dp(ctx, 12), 0, 0);
            c.addView(tip);

            d.setContentView(c);
            sizeWindow(d, ctx, 0.78f);
            d.show();
            popIn(c);
        } catch (Throwable t) {
            Store.log(ctx, "size picker fail " + t);
        }
    }

    /** 一行「名称  −  值  ＋」，valueGet 用来刷新显示 */
    static LinearLayout adjustRow(Context ctx, final String name,
                                  String valueText,
                                  final Runnable minus, final Runnable plus,
                                  boolean isHeight) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 8);
        row.setLayoutParams(lp);

        TextView n = label(ctx, name, 0xFFE6EDF3, 13f, false);
        n.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(n);

        row.addView(roundBtn(ctx, "−", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                minus.run();
            }
        }));

        final TextView val = label(ctx, valueText, 0xFF58A6FF, 13f, true);
        val.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(ctx, 78),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        val.setLayoutParams(vlp);
        row.addView(val);

        row.addView(roundBtn(ctx, "＋", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                plus.run();
            }
        }));
        return row;
    }

    static TextView roundBtn(Context ctx, String text, View.OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(0xFFE6EDF3);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 36)));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(ctx, 10));
        g.setColor(0xFF1F2A38);
        t.setBackground(g);
        t.setOnClickListener(l);
        return t;
    }

    // ---------------- 色板 ----------------

    public static void showColorPicker(Context ctx, int current, final ColorCallback cb) {
        try {
            final Dialog d = base(ctx);
            LinearLayout c = card(ctx);

            TextView title = label(ctx, "胶囊颜色", 0xFF7D8590, 11f, false);
            title.setPadding(dp(ctx, 4), 0, 0, dp(ctx, 12));
            c.addView(title);

            for (int row = 0; row < 3; row++) {
                LinearLayout line = new LinearLayout(ctx);
                line.setOrientation(LinearLayout.HORIZONTAL);
                for (int col = 0; col < 4; col++) {
                    final int color = PALETTE[row * 4 + col];
                    View sw = new View(ctx);
                    GradientDrawable g = new GradientDrawable();
                    g.setCornerRadius(dp(ctx, 12));
                    g.setColor(color);
                    if (color == current) g.setStroke(dp(ctx, 2), 0xFFFFFFFF);
                    sw.setBackground(g);
                    LinearLayout.LayoutParams lp =
                            new LinearLayout.LayoutParams(0, dp(ctx, 46), 1f);
                    lp.setMargins(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));
                    sw.setLayoutParams(lp);
                    sw.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                if (d.isShowing()) d.dismiss();
                            } catch (Throwable ignored) {}
                            cb.onColor(color);
                        }
                    });
                    line.addView(sw);
                }
                c.addView(line);
            }

            d.setContentView(c);
            sizeWindow(d, ctx, 0.72f);
            d.show();
            popIn(c);
        } catch (Throwable t) {
            Store.log(ctx, "color picker fail " + t);
        }
    }

    // ---------------- 色相盘 ----------------

    public static void showHuePicker(final Context ctx, int current, final ColorCallback cb) {
        try {
            final Dialog d = base(ctx);
            LinearLayout c = card(ctx);

            TextView title = label(ctx, "色相 + 亮度", 0xFF7D8590, 11f, false);
            title.setPadding(0, 0, 0, dp(ctx, 12));
            c.addView(title);

            final float[] hsv = new float[3];
            Color.colorToHSV(current & 0x00FFFFFF, hsv);
            if (hsv[1] < 0.25f) hsv[1] = 0.78f;
            if (hsv[2] < 0.25f) hsv[2] = 0.92f;

            final View preview = new View(ctx);
            setRound(preview, Color.HSVToColor(0xCC, hsv), dp(ctx, 12));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 44));
            plp.bottomMargin = dp(ctx, 8);
            preview.setLayoutParams(plp);
            c.addView(preview);

            // ---- 色相条 ----
            View hueBar = new View(ctx) {
                @Override
                protected void onDraw(Canvas canvas) {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    int w = getWidth(), hgt = getHeight();
                    int[] cs = new int[13];
                    for (int i = 0; i <= 12; i++) {
                        cs[i] = Color.HSVToColor(new float[]{i * 30f, 0.82f, 0.95f});
                    }
                    p.setShader(new LinearGradient(0, 0, w, 0, cs, null, Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(0, 0, w, hgt, dp(ctx, 9), dp(ctx, 9), p);
                }

                @Override
                public boolean onTouchEvent(MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                        float x = Math.max(0, Math.min(getWidth(), e.getX()));
                        hsv[0] = x / Math.max(1, getWidth()) * 360f;
                        setRound(preview, Color.HSVToColor(0xCC, hsv), dp(ctx, 12));
                        View v = getRootView().findViewWithTag("valbar");
                        if (v != null) v.invalidate();
                        return true;
                    }
                    if (act == MotionEvent.ACTION_UP) {
                        try {
                            if (d.isShowing()) d.dismiss();
                        } catch (Throwable ignored) {}
                        cb.onColor(Color.HSVToColor(0xCC, hsv));
                        return true;
                    }
                    return true;
                }
            };
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 44));
            hlp.bottomMargin = dp(ctx, 10);
            hueBar.setLayoutParams(hlp);
            c.addView(hueBar);

            // ---- 亮度条 ----
            View valBar = new View(ctx) {
                @Override
                protected void onDraw(Canvas canvas) {
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    int w = getWidth(), hgt = getHeight();
                    float[] a = {hsv[0], hsv[1], 0.15f};
                    float[] b = {hsv[0], hsv[1], 1.0f};
                    p.setShader(new LinearGradient(0, 0, w, 0,
                            Color.HSVToColor(a), Color.HSVToColor(b),
                            Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(0, 0, w, hgt, dp(ctx, 9), dp(ctx, 9), p);
                }

                @Override
                public boolean onTouchEvent(MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                        float x = Math.max(0, Math.min(getWidth(), e.getX()));
                        hsv[2] = 0.15f + (x / Math.max(1, getWidth())) * 0.85f;
                        setRound(preview, Color.HSVToColor(0xCC, hsv), dp(ctx, 12));
                        return true;
                    }
                    if (act == MotionEvent.ACTION_UP) {
                        try {
                            if (d.isShowing()) d.dismiss();
                        } catch (Throwable ignored) {}
                        cb.onColor(Color.HSVToColor(0xCC, hsv));
                        return true;
                    }
                    return true;
                }
            };
            valBar.setTag("valbar");
            valBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 44)));
            c.addView(valBar);

            TextView tip = label(ctx, "上：色相　下：亮度　松手确认", 0xFF484F58, 10f, false);
            tip.setPadding(0, dp(ctx, 12), 0, 0);
            c.addView(tip);

            d.setContentView(c);
            sizeWindow(d, ctx, 0.80f);
            d.show();
            popIn(c);
        } catch (Throwable t) {
            Store.log(ctx, "hue picker fail " + t);
        }
    }

    // ---------------- 详情卡片 ----------------

    public static void showDetail(Context ctx, String big, String bigUnit, String[][] rows) {
        try {
            final Dialog d = base(ctx);
            LinearLayout c = card(ctx);

            c.addView(label(ctx, "听歌时长 · 实时", 0xFF7D8590, 11f, false));

            TextView num = label(ctx, big, 0xFF58A6FF, 38f, true);
            num.setPadding(0, dp(ctx, 6), 0, 0);
            c.addView(num);
            c.addView(label(ctx, bigUnit, 0xFF6E7681, 11f, false));

            c.addView(divider(ctx));

            for (String[] kv : rows) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(ctx, 7);
                row.setLayoutParams(lp);

                TextView k = label(ctx, kv[0], 0xFF7D8590, 12f, false);
                k.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(k);
                row.addView(label(ctx, kv[1], 0xFFE6EDF3, 13f, false));
                c.addView(row);
            }

            TextView foot = label(ctx, "本机数据，不经过服务器", 0xFF484F58, 10f, false);
            foot.setPadding(0, dp(ctx, 14), 0, 0);
            c.addView(foot);

            d.setContentView(c);
            sizeWindow(d, ctx, 0.86f);
            d.show();
            popIn(c);
        } catch (Throwable t) {
            Store.log(ctx, "detail fail " + t);
        }
    }

    // ---------------- 工具 ----------------

    private static void popIn(View card) {
        try {
            card.setScaleX(0.82f);
            card.setScaleY(0.82f);
            card.setAlpha(0f);
            card.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.15f))
                    .start();
        } catch (Throwable ignored) {}
    }

    private static View divider(Context ctx) {
        View v = new View(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 1)));
        lp.topMargin = dp(ctx, 15);
        lp.bottomMargin = dp(ctx, 9);
        v.setLayoutParams(lp);
        v.setBackgroundColor(0xFF21262D);
        return v;
    }

    private static void setRound(View v, int color, int radius) {
        try {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(radius);
            g.setColor(color);
            v.setBackground(g);
        } catch (Throwable ignored) {}
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }
}
