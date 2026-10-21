# M8RE Android port

English | [中文](README.zh-CN.md)

An experimental port of [M8RE](https://github.com/mtdcmz/M8RE) to a **standalone APK**.

M8RE is a Chrome extension that revives the Flash-era **M8 code danmaku** (mode 8,
a.k.a. *biliscript*) on the modern bilibili HTML5 player. This port runs its engine
inside an Android WebView, so no browser extension is required.

This repository is a fork of M8RE; the Android part lives in `android/`. The upstream
extension code (`content/`, `background/`, `manifest.json`) is unchanged.


---

## ⚠️ Important

> **This port is entirely AI-generated. It has only been tested and used briefly.
> It is not guaranteed to work on your device or Android/WebView version.**
>
> It may crash, stutter, show a black screen, or have unresponsive buttons.
> **Please report any issue to the port's author [mawqer123](https://github.com/mawqer123),
> not to the upstream author mtdcmz.**
>
> The upstream M8RE project is unrelated to this port and its author bears no
> responsibility for it.

---

## What this is

Between roughly 2012 and 2017, bilibili's Flash player supported "code danmaku":
comments whose content is not text but an entire *biliscript* (an ECMAScript dialect)
script, executed by a sandboxed VM inside the danmaku layer. Authors used it to build
games, particle effects, interactive stories and music visualizers. When the Flash
player was retired, these works stopped working.

M8RE re-implements that M8 virtual machine from scratch. This port makes it run on
Android and adds an on-screen gamepad for 2D side-scroller style works.

> **Not an official bilibili product.** No affiliation with or endorsement by
> bilibili.

---

## Status

**Experimental prototype.** It installs, it launches, and the engine mounts, but
compatibility is far from complete — the upstream M8RE README also states that
compatibility is "currently NOT good". Treat it as a prototype, not a finished tool.

Known limitations:

- Depends on bilibili's **desktop** player DOM (`.bpx-player-*`). The APK forces a
  desktop user agent.
- Complex scripts may drop frames inside a WebView.
- Some older works depended on external loaders or player quirks that are already dead.
- Being entirely AI-generated, behaviour may differ across devices, Android versions
  and WebView versions.

---

## Download

Latest release:

**https://github.com/mawqer123/M8RE/releases/latest**

Pinned version:

**https://github.com/mawqer123/M8RE/releases/tag/v1.2**

Direct APK for v1.2:

**https://github.com/mawqer123/M8RE/releases/download/v1.2/M8RE-1.2.apk**

---

## This is not a browser

This app is **only for opening bilibili video pages**, not a general-purpose browser.

- The URL field accepts bilibili links or BV / av ids only; anything else is blocked
- Off-site links inside a video page are refused, not opened, and not handed to an
  external browser
- Non-bilibili links shared from other apps are refused as well

The app exists to run the M8RE engine and is neither suited nor intended to be used
as a browser.

## Install

1. Download `M8RE-*.apk` from Releases.
2. Allow "install unknown apps" on your device, then install.
3. Optionally tap **登录** to sign in to your bilibili account.
4. Paste a video URL or a **BV id** (e.g. `BV1Bs411Z7rR`) into the top bar and tap Go.
5. The status bar shows `M8RE 引擎已启动 ✓` when the engine is up.

## Gamepad

Toggle with the **手柄** button in the top bar.

| Button | keyCode emitted | Basis |
|---|---|---|
| ← ↑ → ↓ | 37 / 38 / 39 / 40 | engine `_allow()` whitelist |
| A | 65 | same |
| B | 83 | 66 is rejected by the whitelist, mapped to S |
| Jump | 87 | same |
| ⏻ | 27 | ESC |

Multi-touch is supported, so you can hold a direction and jump at the same time.

Long-press **手柄** to enter edit mode: drag buttons, pinch to resize. It also offers:

- **Simplified / Full** key presets
- **Left / right handed** layout mirroring
- Opacity and button size adjustment
- Layout is saved and restored automatically

The top bar has two rows: URL field with Go / Home / Login, then
Gamepad / Mouse / Bookmarks / Retry / Fullscreen. **Long-press Bookmarks** to open
favourites and browse history, jump to an entry, or clear history.

The status bar is colour-coded and distinguishes stages: page loading, engine injecting,
injected but danmaku layer not ready, engine ready, and non-video pages. Failures come
with a suggested action (retry injection, check network). When engine probing fails it
reports a diagnosis (missing video element, player container not ready, or overlay not
mounted) instead of a bare state string.

---

## Build from source

No Gradle: the build calls `aapt2 / javac / d8 / apksigner` directly, works offline and
takes seconds.

Requirements: JDK 17+, Android SDK (build-tools 37, platform android-35), python3, and
node (for the pre-packaging check).

```bash
cd android
./build.sh
# output: android/out/M8RE-<version>.apk
```

`build.sh` **first syncs the 15 engine files from the repository root `content/`** and
then packages, so the Android build always matches the engine in the same tree; those
copies are not committed. The SDK is located via `ANDROID_HOME`, `local.properties`, or
a built-in default.

Before packaging it runs `tools/check_chunks.py`, which validates every injection chunk
with `node --check`, so a chunk split in the middle of a function can never be silently
dropped and leave the engine unloaded.

The version is defined once at the top of `build.sh` via `APP_VERSION_NAME` /
`APP_VERSION_CODE`.

## Layout

```
../content/                 engine JS, verbatim from upstream M8RE, synced at build time
app/src/main/assets/m8re/   port files only: host_shim.js / _mouse.js / inject.js / LICENSE.txt
app/src/main/java/.../      Android host
  MainActivity.java         WebView, request interception, JS bridges
  EngineInjector.java       chunked engine injection with mount probing
  GamepadView.java          on-screen gamepad
  M8Http.java               API/CDN requests (CORS and cookies handled natively)
sample/dodge_m8.js          playable sample M8 game
tools/check_chunks.py       pre-packaging chunk syntax check
tools/test_game.js          headless test running the sample on the real VM
```

The engine is loaded in the same order as upstream `manifest.json` `content_scripts.js`.

### Differences from the upstream extension

| Upstream extension API | Replacement here |
|---|---|
| `chrome.runtime.sendMessage` (background fetch proxy) | `shouldInterceptRequest` + `M8Http` |
| `chrome.runtime.getURL` (inject.js) | engine falls back to polling |
| `chrome.storage.local` | falls back to `localStorage` |

`chrome.runtime.id` is deliberately left undefined so the engine's `isExt` check is
false and it takes its non-extension path — hence the engine is unmodified.

---

## License

This port: MIT, see [LICENSE](LICENSE).

Bundled M8RE engine: MIT, Copyright (c) mtdcmz. Full text in [NOTICE](NOTICE) and
`app/src/main/assets/m8re/LICENSE.txt`.

---

## Disclaimer

- This project does not host or distribute any danmaku content. It is technical research
  into danmaku format parsing and runtime emulation.
- It does not crack or bypass any paywall or copyright mechanism.
- Any consequences of using this project are the user's own.
- **Please report issues to this port's author, not to the upstream author.**
- If any party considers this project inappropriate, contact the author for removal.
