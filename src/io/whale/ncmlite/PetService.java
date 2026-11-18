package io.whale.ncmlite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.List;

/**
 * 免 root 版核心服务：
 *   ① 常驻前台服务（保活）
 *   ② 通过 MediaSessionManager 监听网易云的播放状态（需要通知使用权）
 *   ③ 悬浮窗胶囊
 *   ④ 本地记录播放时长
 */
public class PetService extends Service {

    public static final String CHANNEL = "ncm_lite";
    public static final int NOTIF_ID = 1001;
    static final String TARGET_PKG = "com.netease.cloudmusic";

    private WindowManager wm;
    private BadgeView badge;
    private WindowManager.LayoutParams lp;
    private MediaSessionManager msm;
    private MediaController controller;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // 当前播放状态
    private String curTitle = null;
    private String curArtist = null;
    private long curDuration = 0L;
    private long curPosition = -1L;
    private long posAt = 0L;
    private boolean playing = false;
    private long segStart = 0L;      // 本段开始时间（记录用）
    private long playedMs = 0L;      // 本段已累计（暂停时结算进去）
    private long playStart = 0L;     // 本次进入播放态的时刻（0=当前不在播）

    // 累计
    private long totalMs = 0L;
    private int sessions = 0;
    private long dayMs = 0L;
    private String dayKey = "";

    // 显示
    private int displayMode = 0;     // 0 总时长 1 今日 2 段数
    private int badgeColor = 0xCC1F6FEB;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIF_ID, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        Store.init(this);
        totalMs = Store.getLong("total_ms", 0L);
        sessions = Store.getInt("sessions", 0);
        dayMs = Store.getLong("day_ms", 0L);
        dayKey = Store.getString("day", "");
        displayMode = Store.getInt("mode", 0);
        badgeColor = Store.getInt("color", 0xCC1F6FEB);

        setDayToday();
        addBadge();
        startWatch();

        ui.postDelayed(ticker, 500L);
    }

    // ---------------- 前台通知 ----------------

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "听歌统计", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("NCM Lite 运行中")
                .setContentText("听歌统计 · 悬浮胶囊")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    // ---------------- 媒体监听 ----------------

    private void startWatch() {
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (msm == null) {
                Store.log(this, "msm null");
                return;
            }
            ComponentName cn = new ComponentName(this, MediaWatcher.class);
            msm.addOnActiveSessionsChangedListener(list -> bindSessions(list), cn);
            bindSessions(msm.getActiveSessions(cn));
            Store.log(this, "media watch started");
        } catch (SecurityException e) {
            Store.log(this, "缺通知使用权: " + e);
        } catch (Throwable t) {
            Store.log(this, "watch fail " + t);
        }
    }

    private void bindSessions(List<MediaController> list) {
        if (list == null) return;
        MediaController target = null;
        for (MediaController mc : list) {
            if (TARGET_PKG.equals(mc.getPackageName())) {
                target = mc;
                break;
            }
        }
        if (target == null || target == controller) return;

        if (controller != null) {
            try {
                controller.unregisterCallback(cb);
            } catch (Throwable ignored) {}
        }
        controller = target;
        target.registerCallback(cb, ui);
        Store.log(this, "bound: " + target.getPackageName());

        onMeta(target.getMetadata());
        onState(target.getPlaybackState());
    }

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata md) {
            onMeta(md);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState ps) {
            onState(ps);
        }
    };

    private void onMeta(MediaMetadata md) {
        if (md == null) return;
        String t = safe(md, MediaMetadata.METADATA_KEY_TITLE);
        String a = safe(md, MediaMetadata.METADATA_KEY_ARTIST);
        long d = 0L;
        try {
            d = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        } catch (Throwable ignored) {}

        boolean same = t != null && t.equals(curTitle) && (a == null || a.equals(curArtist));
        if (same) {
            if (d > 0) curDuration = d;
            return;
        }

        settle("switch");

        curTitle = t;
        curArtist = a;
        curDuration = d;
        curPosition = 0L;
        posAt = SystemClock.elapsedRealtime();
        segStart = System.currentTimeMillis();
        playedMs = 0L;
        playStart = playing ? System.currentTimeMillis() : 0L;

        Store.log(this, "NOW >> " + t + " / " + a);
        if (badge != null) badge.sweepOnce();
        updateBadge();
    }

    private void onState(PlaybackState ps) {
        if (ps == null) return;
        boolean isPlaying = ps.getState() == PlaybackState.STATE_PLAYING;
        long nowMs = System.currentTimeMillis();

        // 播放态进出：进来记起点，出去把这段累加进 playedMs
        if (isPlaying && playStart == 0L) {
            playStart = nowMs;
        } else if (!isPlaying && playStart > 0L) {
            playedMs += nowMs - playStart;
            playStart = 0L;
        }

        if (ps.getPosition() >= 0) {
            curPosition = ps.getPosition();
            posAt = SystemClock.elapsedRealtime();
        }

        if (playing && !isPlaying) {
            settle("pause");
        }
        playing = isPlaying;
        updateBadge();
    }

    // ---------------- 结算与存储 ----------------

    private void settle(String reason) {
        if (curTitle == null) return;
        long played = playedMs;
        if (playStart > 0L) {
            played += System.currentTimeMillis() - playStart;
        }
        if (played >= 1000L) {
            long now = System.currentTimeMillis();
            totalMs += played;
            sessions++;
            setDayToday();
            dayMs += played;
            Store.putLong(this, "total_ms", totalMs);
            Store.putInt(this, "sessions", sessions);
            Store.putLong(this, "day_ms", dayMs);
            Store.putString(this, "day", dayKey);

            Store.appendRecord(this, curTitle, curArtist, curDuration, played, reason, segStart, now);
            Store.log(this, "SAVE[" + reason + "] " + curTitle + " played=" + played / 1000 + "s");
        }
        playedMs = 0L;
        segStart = System.currentTimeMillis();
        playStart = playing ? System.currentTimeMillis() : 0L;
    }

    /** 本段到目前为止的播放时长 */
    private long estimatePlayed() {
        long p = playedMs;
        if (playStart > 0L) {
            p += System.currentTimeMillis() - playStart;
        }
        return p;
    }

    private void setDayToday() {
        String d = Store.today();
        if (!d.equals(dayKey)) {
            dayKey = d;
            dayMs = 0L;
        }
    }

    // ---------------- 悬浮窗 ----------------

    private void addBadge() {
        if (badge != null) return;
        badge = new BadgeView(this);
        badge.setFill(badgeColor);
        badge.setShape(Store.getInt("shape", 1));
        badge.setSize(Store.getInt("badge_h", 27),
                Store.getFloat("badge_sp", 11.5f));
        badge.setSizeScale(SIZE_SCALES[Store.getInt("size", 1) % 4]);
        badge.setExtraPad(LEN_PADS[Store.getInt("len", 1) % 4]);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = getResources().getDisplayMetrics().widthPixels - dp(120);
        lp.y = dp(150);

        bindTouch();

        try {
            wm.addView(badge, lp);
            Store.log(this, "badge added");
        } catch (Throwable t) {
            Store.log(this, "badge add fail（悬浮窗权限？）: " + t);
        }
    }

    private void bindTouch() {
        final float[] down = new float[4];
        final long[] at = new long[1];
        final boolean[] moved = new boolean[1];

        badge.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        down[0] = e.getRawX();
                        down[1] = e.getRawY();
                        down[2] = lp.x;
                        down[3] = lp.y;
                        at[0] = System.currentTimeMillis();
                        moved[0] = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - down[0];
                        float dy = e.getRawY() - down[1];
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved[0] = true;
                        if (moved[0]) {
                            lp.x = (int) (down[2] + dx);
                            lp.y = (int) (down[3] + dy);
                            try {
                                wm.updateViewLayout(v, lp);
                            } catch (Throwable ignored) {}
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!moved[0]) {
                            long held = System.currentTimeMillis() - at[0];
                            long now2 = System.currentTimeMillis();
                            if (held > 250L) {
                                showMenu();
                            } else if (now2 - lastTap < 280L) {
                                lastTap = 0L;
                                skipNext();          // 双击 = 下一首
                            } else {
                                lastTap = now2;
                                ui.postDelayed(singleTap, 280L);
                            }
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private long lastTap = 0L;

    private final Runnable singleTap = new Runnable() {
        @Override
        public void run() {
            if (lastTap == 0L) return;      // 被双击吃掉了
            lastTap = 0L;
            displayMode = (displayMode + 1) % 2;
            Store.putInt(PetService.this, "mode", displayMode);
            if (badge != null) badge.setTextSwitch(textNow());
        }
    };

    private static final String[] SHAPE_NAMES = {"胶囊", "超椭圆", "不对称", "方角"};
    private static final String[] SIZE_NAMES = {"小", "中", "大", "超大"};
    private static final float[] SIZE_SCALES = {0.85f, 1.0f, 1.2f, 1.45f};
    private static final String[] LEN_NAMES = {"短", "标准", "长", "很长"};
    private static final int[] LEN_PADS = {0, 10, 22, 40};

    private void showMenu() {
        try {
            final android.app.Dialog d = Ui.menuDialog(this);
            android.widget.LinearLayout card = Ui.menuCard(this);

            // 显示单位
            final android.widget.TextView[] unitItem = new android.widget.TextView[1];
            unitItem[0] = Ui.toggleItem(this,
                    "显示单位：" + (Store.getInt("minutes", 0) == 1 ? "分钟" : "小时"),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int m = Store.getInt("minutes", 0) == 1 ? 0 : 1;
                            Store.putInt(PetService.this, "minutes", m);
                            unitItem[0].setText("显示单位：" + (m == 1 ? "分钟" : "小时"));
                            updateBadge();
                        }
                    });
            card.addView(unitItem[0]);

            // 形状
            final android.widget.TextView[] shapeItem = new android.widget.TextView[1];
            shapeItem[0] = Ui.toggleItem(this,
                    "形状：" + SHAPE_NAMES[Store.getInt("shape", 1) % 4],
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int sh = (Store.getInt("shape", 1) + 1) % 4;
                            Store.putInt(PetService.this, "shape", sh);
                            shapeItem[0].setText("形状：" + SHAPE_NAMES[sh]);
                            if (badge != null) {
                                badge.setShape(sh);
                                refreshBadgeLayout();
                            }
                        }
                    });
            card.addView(shapeItem[0]);

            // 尺寸
            final android.widget.TextView[] sizeItem = new android.widget.TextView[1];
            sizeItem[0] = Ui.toggleItem(this,
                    "尺寸：" + SIZE_NAMES[Store.getInt("size", 1) % 4],
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int sz = (Store.getInt("size", 1) + 1) % 4;
                            Store.putInt(PetService.this, "size", sz);
                            sizeItem[0].setText("尺寸：" + SIZE_NAMES[sz]);
                            if (badge != null) {
                                badge.setSizeScale(SIZE_SCALES[sz]);
                                refreshBadgeLayout();
                            }
                        }
                    });
            card.addView(sizeItem[0]);

            // 长度
            final android.widget.TextView[] lenItem = new android.widget.TextView[1];
            lenItem[0] = Ui.toggleItem(this,
                    "长度：" + LEN_NAMES[Store.getInt("len", 1) % 4],
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int ln = (Store.getInt("len", 1) + 1) % 4;
                            Store.putInt(PetService.this, "len", ln);
                            lenItem[0].setText("长度：" + LEN_NAMES[ln]);
                            if (badge != null) {
                                badge.setExtraPad(LEN_PADS[ln]);
                                refreshBadgeLayout();
                            }
                        }
                    });
            card.addView(lenItem[0]);

            // 切换动画
            final android.widget.TextView[] animItem = new android.widget.TextView[1];
            animItem[0] = Ui.toggleItem(this,
                    Store.getInt("switchanim", 1) == 1 ? "切换动画：开 ✓" : "切换动画：关",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int a = Store.getInt("switchanim", 1) == 1 ? 0 : 1;
                            Store.putInt(PetService.this, "switchanim", a);
                            animItem[0].setText(a == 1 ? "切换动画：开 ✓" : "切换动画：关");
                        }
                    });
            card.addView(animItem[0]);

            // 色板
            card.addView(Ui.menuItem(this, d, "胶囊颜色", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.showColorPicker(PetService.this, badgeColor, new Ui.ColorCallback() {
                        @Override
                        public void onColor(int c) {
                            badgeColor = c;
                            Store.putInt(PetService.this, "color", c);
                            if (badge != null) badge.setFillAnimated(c);
                        }
                    });
                }
            }));

            // 色相盘
            card.addView(Ui.menuItem(this, d, "自定义颜色（色相盘）", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.showHuePicker(PetService.this, badgeColor, new Ui.ColorCallback() {
                        @Override
                        public void onColor(int c) {
                            badgeColor = c;
                            Store.putInt(PetService.this, "color", c);
                            if (badge != null) badge.setFillAnimated(c);
                        }
                    });
                }
            }));

            card.addView(Ui.menuItem(this, d, "查看详情", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDetailCard();
                }
            }));

            card.addView(Ui.menuItem(this, d, "刷新数字", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateBadge();
                }
            }));

            card.addView(Ui.menuItem(this, d, "上一首", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skipPrev();
                }
            }));

            card.addView(Ui.menuItem(this, d, "播放 / 暂停", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePlay();
                }
            }));

            card.addView(Ui.menuItem(this, d, "下一首", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skipNext();
                }
            }));

            card.addView(Ui.menuItem(this, d, "重置位置", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resetPos();
                }
            }));

            card.addView(Ui.menuItem(this, d, "关闭胶囊", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopSelf();
                }
            }));

            d.setContentView(card);
            Ui.menuWindow(d, this, 0.72f);
            d.show();
        } catch (Throwable t) {
            Store.log(this, "menu fail " + t);
        }
    }

    /** View 尺寸变了，得通知 WindowManager 重新布局，否则看着没反应 */
    private void refreshBadgeLayout() {
        if (badge == null || wm == null || lp == null) return;
        try {
            badge.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            wm.updateViewLayout(badge, lp);
        } catch (Throwable t) {
            Store.log(this, "refresh layout fail " + t);
        }
    }

    private void resetPos() {
        try {
            lp.x = getResources().getDisplayMetrics().widthPixels - dp(120);
            lp.y = dp(150);
            if (badge != null && wm != null) wm.updateViewLayout(badge, lp);
        } catch (Throwable ignored) {}
    }

    private void showDetailCard() {
        long live = playing ? estimateLive() : 0L;
        long total = totalMs + live;
        double hours = total / 3600000.0;

        int trackCount = 0;
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "records.jsonl");
            if (f.exists()) {
                java.util.Set<String> set = new java.util.HashSet<String>();
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
                String line;
                while ((line = r.readLine()) != null) {
                    int i = line.indexOf("\"title\":\"");
                    if (i < 0) continue;
                    int e = line.indexOf('"', i + 9);
                    if (e > i) set.add(line.substring(i + 9, e));
                }
                r.close();
                trackCount = set.size();
            }
        } catch (Throwable ignored) {}

        String[][] rows = new String[][]{
                {"今日累计", shortDur(dayMs + live)},
                {"记录段数", String.valueOf(sessions)},
                {"听过歌曲", trackCount + " 首"},
                {"当前模式", displayMode == 1 ? "今日" : "总时长"},
                {"播放状态", playing ? "播放中" : "已暂停"},
                {"当前歌曲", curTitle == null ? "—" : curTitle},
        };
        Ui.showDetail(this, String.format(java.util.Locale.US, "%.2f", hours), "小时", rows);
    }

    /** 直连 MediaController，比走系统按键分发快得多 */
    private void skipNext() {
        if (controller != null) {
            try {
                controller.getTransportControls().skipToNext();
                Store.log(this, "next (direct)");
                return;
            } catch (Throwable ignored) {}
        }
        sendKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    private void skipPrev() {
        if (controller != null) {
            try {
                controller.getTransportControls().skipToPrevious();
                Store.log(this, "prev (direct)");
                return;
            } catch (Throwable ignored) {}
        }
        sendKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    private void togglePlay() {
        if (controller != null) {
            try {
                android.media.session.PlaybackState ps = controller.getPlaybackState();
                boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                if (playing) controller.getTransportControls().pause();
                else controller.getTransportControls().play();
                Store.log(this, "playpause (direct)");
                return;
            } catch (Throwable ignored) {}
        }
        sendKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    /** 兜底：系统媒体按键（target 拿不到 controller 时用） */
    private void sendKey(int code) {
        try {
            android.media.AudioManager am =
                    (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(
                    t, t, android.view.KeyEvent.ACTION_DOWN, code, 0));
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(
                    t, t, android.view.KeyEvent.ACTION_UP, code, 0));
            Store.log(this, "key fallback " + code);
        } catch (Throwable ignored) {}
    }

    // ---------------- 定时刷新 ----------------

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateBadge();
            ui.postDelayed(this, 500L);
        }
    };

    private void updateBadge() {
        if (badge == null) return;
        badge.setProgress(progress());
        badge.setText(textNow());
        badge.setPlaying(playing);
    }

    private float progress() {
        if (curDuration <= 0 || curPosition < 0) return -1f;
        long p = curPosition;
        if (playing && posAt > 0) {
            p += SystemClock.elapsedRealtime() - posAt;
        }
        return Math.min(1f, Math.max(0f, (float) p / (float) curDuration));
    }

    private String textNow() {
        long live = playing ? estimateLive() : 0L;
        boolean minutes = Store.getInt("minutes", 0) == 1;
        if (displayMode == 1) return shortDur(dayMs + live);
        long total = totalMs + live;
        if (minutes) return Math.round(total / 60000.0) + "m";
        return String.format(java.util.Locale.US, "%.1fh", total / 3600000.0);
    }

    private long estimateLive() {
        return estimatePlayed();
    }

    private String shortDur(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60;
        return h > 0 ? h + "h" + m + "m" : m + "m";
    }

    private String safe(MediaMetadata md, String key) {
        try {
            return md.getString(key);
        } catch (Throwable t) {
            return null;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (badge != null && wm != null) wm.removeView(badge);
        } catch (Throwable ignored) {}
        badge = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
