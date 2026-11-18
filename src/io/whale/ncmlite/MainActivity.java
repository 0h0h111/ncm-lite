package io.whale.ncmlite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 启动页：引导授权 + 启动服务 */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        root.setBackgroundColor(0xFF0D1117);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("NCM Lite");
        title.setTextColor(0xFF58A6FF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("网易云听歌统计 · 免 root 版\n靠「通知使用权」读取播放状态，不需要 Xposed");
        sub.setTextColor(0xFF8B949E);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sub.setPadding(0, dp(8), 0, dp(24));
        root.addView(sub);

        status = new TextView(this);
        status.setTextColor(0xFFE6EDF3);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        status.setPadding(0, 0, 0, dp(16));
        root.addView(status);

        root.addView(button("① 授予悬浮窗权限", v -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) {}
        }));

        root.addView(button("② 授予通知使用权", v -> {
            try {
                Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(i);
            } catch (Throwable ignored) {}
        }));

        root.addView(button("③ 启动悬浮胶囊", v -> {
            try {
                Intent i = new Intent(this, PetService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                else startService(i);
                toast("已启动，回到桌面看看");
            } catch (Throwable t) {
                toast("启动失败：" + t);
            }
        }));

        root.addView(button("④ 停止", v -> {
            try {
                stopService(new Intent(this, PetService.class));
                toast("已停止");
            } catch (Throwable ignored) {}
        }));

        TextView tip = new TextView(this);
        tip.setText("\n日志：Android/data/io.whale.ncmlite/files/lite.log"
                + "\n记录：Android/data/io.whale.ncmlite/files/records.jsonl");
        tip.setTextColor(0xFF6E7681);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tip.setPadding(0, dp(24), 0, 0);
        root.addView(tip);

        setContentView(sv);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean overlay = canOverlay();
        boolean notif = notifGranted();
        StringBuilder sb = new StringBuilder();
        sb.append(overlay ? "✅" : "❌").append(" 悬浮窗权限\n");
        sb.append(notif ? "✅" : "❌").append(" 通知使用权\n");
        status.setText(sb.toString());
    }

    private boolean canOverlay() {
        if (Build.VERSION.SDK_INT >= 23) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private boolean notifGranted() {
        try {
            ComponentName cn = new ComponentName(this, MediaWatcher.class);
            String flat = Settings.Secure.getString(getContentResolver(),
                    "enabled_notification_listeners");
            return flat != null && flat.contains(cn.flattenToString());
        } catch (Throwable t) {
            return false;
        }
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
