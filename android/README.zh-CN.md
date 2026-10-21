# M8RE Android 移植版

[English](README.md) | 中文

把 [M8RE](https://github.com/mtdcmz/M8RE) 做成**纯 APK** 的实验性移植。

M8RE 原本是一个 Chrome 扩展，用于在现代 bilibili HTML5 播放器上复活 Flash 时代的
**M8 代码弹幕**（mode 8，又称 *biliscript*）。本项目把它的引擎放进 Android 的 WebView 里运行，
不需要浏览器扩展，安装即可使用。

本项目是 M8RE 的 fork，Android 部分位于 `android/`；上游的扩展代码
（`content/`、`background/`、`manifest.json`）保持原样，一行未改。

---

## ⚠️ 重要声明

> **本项目纯 AI 生成，仅做过测试与少量实际使用体验，不代表在你的手机机型上一定能正常工作。**
>
> 可能出现闪退、卡顿、黑屏、按钮失灵或其他问题。**如果有任何问题，请立即上报给本项目作者
> （[mawqer123](https://github.com/mawqer123)），不要打扰上游作者 mtdcmz。**
>
> 上游 M8RE 与本项目无关，上游作者不负责本移植版的任何问题。

---

## 这是什么

2012–2017 年，bilibili 的 Flash 播放器支持「代码弹幕」：弹幕内容不是文字，而是一段
*biliscript*（ECMAScript 方言）脚本，由沙盒 VM 执行，可以在弹幕层里运行小游戏、粒子特效、
音乐可视化等作品。Flash 播放器退役后，这些作品就失效了。

M8RE 从零复刻了那个 M8 虚拟机。本项目让它能跑在 Android 上，并加了屏幕虚拟手柄，
用来玩 2D 横版小游戏类的稿件。

> **本项目不是 bilibili 官方产品**，与哔哩哔哩无任何关联，也未获其授权或背书。

---

## 当前状态

**实验性原型。** 能安装、能启动、引擎能挂载，但兼容性远谈不上完整 —— 上游 M8RE 的 README
也明确写了兼容性「currently NOT good」。请按原型看待，不要当作成品。

已知限制：

- 依赖 bilibili **桌面版**播放器页面结构（`.bpx-player-*`）。APK 会强制使用桌面版 UA。
- WebView 里跑复杂脚本可能掉帧。
- 部分老稿件依赖已失效的外部加载器或播放器特殊机制，无法还原。
- 由于本项目纯 AI 生成，不同手机机型、系统版本、WebView 版本上的表现可能存在差异。

---

## 下载

最新版本请到 Releases 页面下载：

**https://github.com/mawqer123/M8RE/releases/latest**

指定版本（历史地址）：

**https://github.com/mawqer123/M8RE/releases/tag/v1.0**

直接下载 v1.0 的 APK：

**https://github.com/mawqer123/M8RE/releases/download/v1.0/M8RE-1.0.apk**

---

## 安装

1. 下载 Releases 里的 `M8RE-*.apk`。
2. 手机允许「安装未知来源应用」，然后安装。
3. 打开 APP，点顶部 **登录** 登录 bilibili 账号（不登录拿不到弹幕数据）。
4. 顶部输入框粘贴视频链接或 **BV 号**（例如 `BV1Bs411Z7rR`），按 Go。
5. 底部状态栏出现 `M8RE 引擎已启动 ✓` 即表示成功。

## 虚拟手柄

顶部 **手柄** 按钮可开关。按 2D 横版游戏的习惯布局：

| 按键 | 发出的 keyCode | 依据 |
|---|---|---|
| ← ↑ → ↓ | 37 / 38 / 39 / 40 | 引擎 `_allow()` 白名单 |
| A | 65 | 同上 |
| B | 83 | 66 被白名单拒绝，改用 S |
| 跳 | 87 | 同上 |
| ⏻ | 27 | ESC |

支持多点触控，可以一边按方向一边按跳跃。

---

## 从源码构建

不依赖 Gradle，直接使用 Android SDK 的 `aapt2 / javac / d8 / apksigner`，离线可用，几秒出包。

依赖：JDK 17+、Android SDK（build-tools 37、platform android-35）、python3、node（用于打包前校验）。

```bash
cd android
./build.sh
# 产物: android/out/M8RE-<version>.apk
```

`build.sh` 会**先从仓库根的 `content/` 同步 15 个引擎文件**再打包，所以 Android 版始终与
同一棵树里的引擎一致；这些副本不提交进 git。SDK 路径通过 `ANDROID_HOME`、`local.properties`
或脚本内默认值定位。

打包前会自动运行 `tools/check_chunks.py`：用 `node --check` 校验每个注入分块是合法 JS，
避免「切块切在函数中间 → 整块被丢弃 → 引擎一行都没加载」这类问题。

版本号只在 `build.sh` 顶部的 `APP_VERSION_NAME` / `APP_VERSION_CODE` 定义一次。

### 用自己的密钥签名（发布用）

仓库里的 `debug.keystore` 仅用于本地测试。发布请使用你自己的密钥：

```bash
export M8_KEYSTORE=/path/to/your.jks
export M8_KS_PASS='<store 密码>'      # 自己填
export M8_KEY_ALIAS='<alias>'
export M8_KEY_PASS='<key 密码>'
./build.sh
```

不设置这些变量时回退到 `debug.keystore`（口令 `android`）。
**不要把真实密钥和密码提交进仓库。**

---

## 项目结构

```
../content/                 引擎 JS —— 来自上游 M8RE，原样未改，构建时同步进来
app/src/main/assets/m8re/   仅存移植层文件：host_shim.js / _mouse.js / inject.js / LICENSE.txt
app/src/main/java/.../      Android 宿主
  MainActivity.java         WebView 承载、网络拦截、JS 桥
  EngineInjector.java       分块注入引擎，注入后探测挂载状态
  GamepadView.java          屏幕虚拟手柄
  M8Http.java               API/CDN 请求（CORS 与 cookie 由原生处理）
sample/dodge_m8.js          可玩的示例 M8 小游戏
tools/check_chunks.py       打包前分块语法校验
tools/test_game.js          用引擎真实 VM 运行示例游戏的无头测试
```

引擎加载顺序与上游 `manifest.json` 的 `content_scripts.js` 完全一致。

### 与上游扩展的差异

| 原扩展接口 | 本项目替代 |
|---|---|
| `chrome.runtime.sendMessage`（后台跨域代理） | `shouldInterceptRequest` + `M8Http` |
| `chrome.runtime.getURL`（注入 inject.js） | 引擎自动降级到轮询模式 |
| `chrome.storage.local` | 自动降级到 `localStorage` |

不伪造 `chrome.runtime.id`，让引擎的 `isExt` 判断为 false，自动走非扩展分支 ——
所以引擎零改动。

---

## 许可

本项目代码：MIT，见 [LICENSE](LICENSE)。

内置的 M8RE 引擎：MIT，Copyright (c) mtdcmz，全文见 [NOTICE](NOTICE) 与
`app/src/main/assets/m8re/LICENSE.txt`。

---

## 免责声明

- 本项目不提供、不分发任何弹幕内容，仅作为**弹幕格式解析与运行时复刻的技术研究**。
- 不破解、不绕过任何付费或版权限制。
- 使用本项目产生的任何后果由使用者自行承担。
- 若有任何问题，请上报给本项目作者，**不要打扰上游作者**。
- 若相关方认为本项目有不妥之处，请联系删除。
