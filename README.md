# M8RE

**Bilibili Mode 8 Danmaku JS Runtime Environment (OR Emulator).**

M8RE is a Chrome extension that revives the Flash-era **M8 code danmaku** (mode 8, a.k.a. *biliscript*) on the modern bilibili HTML5 player. It re-implements the original M8 virtual machine from scratch in JavaScript and runs it as an overlay on the page.

## Background

Between roughly 2012 and 2017, bilibili's Flash player supported "code danmaku": comments whose content is not text but an entire script (mode 8) written in *biliscript*, an ECMAScript dialect executed by a sandboxed VM inside the player. Authors used it to build games, particle effects, interactive stories and music visualizers that ran inside the danmaku layer. When the Flash player was retired, these works stopped working.

M8RE makes them run again on today's player.

## How it works

M8RE's content scripts fetch the danmaku protobuf data (segment packs, the dm/web/view metadata and specialDm packs), then execute mode 8 entries with a biliscript VM — a bytecode interpreter ported from the legacy AS3 player, covering the 61-opcode instruction set, scope chains and error semantics. Scripts talk to a host API (`$`, `Player`, `Utils`, `ScriptManager`, `$G`, ...) whose behavior follows the old player, and render onto a DOM/canvas overlay that reproduces the Flash display list: registration-point transforms, `Graphics` drawing, `BitmapData` pixel operations, frame-synced timers and tweens.

Native danmaku are spawned as hidden carriers on the overlay root, so scripts that hook `$.root.addEventListener("added", ...)` — the takeover pattern most M8 games are built on — work without changes.

M8RE also reads mode 9 BAS entries whose content starts with `//M8v1` and executes them as M8 scripts at the danmaku's own timestamp. (`//` is a comment in biliscript and a lexer error for the official BAS renderer, so the carrier is invisible to everyone else.) This means new M8 works can be published today through bilibili's danmaku system itself.

## Compatibility: not great (yet)

**This emulator is early-stage and its compatibility is currently NOT good.** A meaningful part of the classic corpus runs, but many scripts still misbehave or fail. Treat it as a working prototype, not a finished preservation tool.

Known gaps:

- Mode 7 advanced-danmaku 3D rendering is functional but not pixel-perfect (marked `TODO` in the source).
- Flash device-font text metrics (GDI grid-fitting) are approximated.
- Some Flash filters (blur, advanced glow) are approximated or missing.
- A few works relied on player "black tech" quirks or external loaders that were already dead before Flash itself was.

Compatibility reports and test cases are very welcome.

## Install (developer mode)

1. Clone / download this repository.
2. Open `chrome://extensions`, enable **Developer mode**.
3. Click **Load unpacked** and select the extension folder.
4. Open any bilibili video page. Classic mode 8 scripts run automatically; code danmaku with the `//M8v1` BAS carrier are picked up from the BAS channel.

## Debug flags

| localStorage key | Effect |
|---|---|
| `m8re_debug = '0'` | Silence engine logs (default: verbose) |
| `m8re_badge = '1'` | Show the on-page status badge |

## For authors: publishing M8 via BAS

M8 scripts can be shipped through bilibili's danmaku servers as invisible BAS carriers:

1. Take your M8 script and make the first line exactly `//M8v1`.
2. Send it as a danmaku with `mode=9`, `pool=2`, at the timestamp where it should fire (requires advanced-danmaku permission on the video).
3. One BAS entry carries one script; different timestamps carry different scripts; the extension replays them on seeks just like native mode 8.

## License

Released under the [MIT License](LICENSE).

---

## Android 移植版（本 fork 新增）

本 fork 在 `android/` 下增加了把 M8RE 引擎打包成**纯 APK** 的代码与工具。
上游的扩展代码（`content/`、`background/`、`manifest.json`）保持原样，一行未改。

> **⚠️ 本项目纯 AI 生成，仅做过测试与少量实际使用体验，不代表在你的手机机型上一定能正常工作。**
> 可能出现闪退、卡顿、黑屏或按钮失灵等问题。**如有任何问题，请立即上报给本移植版的作者
> [mawqer123](https://github.com/mawqer123)，不要打扰上游作者 mtdcmz。**
> 上游 M8RE 与此移植版无关，上游作者不承担任何责任。

- 下载：**https://github.com/mawqer123/M8RE/releases/latest**
- 指定版本：**https://github.com/mawqer123/M8RE/releases/tag/v1.0**
- 安装、构建、虚拟手柄键位、许可与免责声明见 [android/README.md](android/README.md)
- 引擎源码位于仓库根的 `content/`，`android/build.sh` 会在构建时自动同步，
  因此不存在两份会不一致的副本
- 有问题请在本 fork 提 issue，**不要打扰上游作者**
