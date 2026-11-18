# NCM Lite

**网易云音乐 · 听歌统计悬浮胶囊** —— **不需要 root**。

一个独立 App，通过系统的「通知使用权」读取网易云的播放状态，在屏幕上挂一个小胶囊，实时显示你听了多久。

> 网易云的本年度听歌报告要等它发，而且它只给你看它想给你看的。
> 这个胶囊把时长实时摆在桌面上，数据存在你自己手机里。

---

## 下载

| 版本 | 说明 |
|---|---|
| [NCMLite-v1.0.apk](releases/NCMLite-v1.0.apk) | 免 root 版，37 KB |

安装后按 [使用说明](docs/USAGE.md) 里的三步授权即可。

---

## 和 root 版的区别

| | NCM Archive（root 版） | **NCM Lite（本项目）** |
|---|---|---|
| root | 需要 | **不需要** |
| 框架 | LSPosed / Vector | 无 |
| 数据来源 | hook 进程内部 | `MediaSessionManager` 读系统媒体会话 |
| 精度 | 精确到毫秒 | 靠回调推算，有 1~2 秒误差 |
| 胶囊 | ✅ 悬浮窗 | ✅ 悬浮窗（同一套绘制） |
| 切歌 | ✅ | ✅ |
| 数据记录 | ✅ | ✅ 同样的 JSONL 格式 |

---

## 功能

### 悬浮胶囊

- **进度填充**：胶囊从左往右填，跟着当前歌曲走
- **两态显示**：单击在「总时长」和「今日累计」之间切换
- **双击切歌**：双击 = 下一首
- **长按菜单**：13 项设置
- **拖动移位**：位置自动记住

### 外观可调

| 项目 | 选项 |
|---|---|
| **形状** | 胶囊 / 超椭圆 / 不对称 / 方角 |
| **尺寸** | 小 0.85× / 中 1.0× / 大 1.2× / 超大 1.45× |
| **长度** | 短 +0dp / 标准 +10dp / 长 +22dp / 很长 +40dp |
| **颜色** | 12 色板 / 色相盘（色相 + 亮度双条） |
| **动画** | 切换翻牌动画可开关 |

### 数据记录

存在 `Android/data/io.whale.ncmlite/files/`：

| 文件 | 内容 |
|---|---|
| `records.jsonl` | 每段播放一条：歌名 / 歌手 / 时长 / 实际听了多久 / 是否听完 |
| `lite.log` | 运行日志 |

全部本机，不联网上传。

---

## 原理

免 root 靠的是 Android 的公开能力，三件套：

```
① 通知使用权（NotificationListenerService）
      ↓ 拿到这张门票后
② MediaSessionManager.getActiveSessions()
      ↓ 才能读取网易云注册的媒体会话
③ WindowManager + TYPE_APPLICATION_OVERLAY
      ↓ 画一个不属于任何 App 的悬浮窗
```

播放时长的算法：

```java
收到 onPlaybackStateChanged(PLAYING) → 开始计时
收到 onPlaybackStateChanged(PAUSED)  → 结算这一段
收到 onMetadataChanged（换歌）        → 结算上一首，重新开始
```

切歌/暂停走 `MediaController.getTransportControls()` 直连，不经过系统按键分发，响应快。

---

## 权限

只需要两个，都是用户手动授权的普通权限：

| 权限 | 用途 |
|---|---|
| **显示在其他应用上层** | 画悬浮胶囊（必需） |
| **通知使用权** | 读网易云的播放状态（必需） |

不申请存储、不申请定位、不申请无障碍。

---

## 编译

```bash
# 依赖：JDK 17+、Android SDK（build-tools 34 + platform android-34）
export ANDROID_SDK_ROOT=/path/to/android-sdk
./build.sh                  # 产物 NCMLite-v1.0.apk
```

不用 Gradle，`javac → d8 → aapt2 → zipalign → apksigner` 一条龙。

## 安装

```
1. 装 APK
2. 打开 App，依次点：
     ① 授予悬浮窗权限
     ② 授予通知使用权
     ③ 启动悬浮胶囊
3. 打开网易云放首歌
```

详细说明见 [docs/USAGE.md](docs/USAGE.md)。

---

## 已知限制

- **精度**：免 root 只能靠状态回调推算，暂停/切歌瞬间可能有 1~2 秒误差
- **只支持网易云**：写死了 `com.netease.cloudmusic`，改包名就能支持别的播放器
- **后台回调**：部分机型网易云在后台不发状态回调，那一段只能靠切歌时补算

## 许可

MIT
