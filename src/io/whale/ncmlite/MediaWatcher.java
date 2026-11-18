package io.whale.ncmlite;

import android.service.notification.NotificationListenerService;

/**
 * 只为拿到「通知使用权」这张门票。
 * 拿到之后 MediaSessionManager.getActiveSessions() 才允许调用，
 * 免 root 版就是靠它读到网易云的播放状态。
 */
public class MediaWatcher extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        Store.init(this);
        Store.log(this, "notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        Store.log(this, "notification listener disconnected");
    }
}
