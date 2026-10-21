# M8RE

English | [中文](README.zh-CN.md)

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

## Android port (added in this fork)

This fork adds everything needed to package the M8RE engine as a **standalone APK**
under [`android/`](android/). The upstream extension code (`content/`, `background/`,
`manifest.json`) is left untouched.

> **⚠️ This port is entirely AI-generated. It has only been tested and used briefly.
> It is not guaranteed to work on your device or Android/WebView version.**
> It may crash, stutter, show a black screen, or have unresponsive buttons.
> **Please report any issue to the port's author [mawqer123](https://github.com/mawqer123),
> not to the upstream author mtdcmz.** The upstream M8RE project is unrelated to this
> port and its author bears no responsibility for it.

- Download: **https://github.com/mawqer123/M8RE/releases/latest**
- Pinned version: **https://github.com/mawqer123/M8RE/releases/tag/v1.2**
- Installation, build instructions, gamepad key map, license and disclaimer:
  [android/README.md](android/README.md)
- Engine sources live in the repository root `content/` and are synced by
  `android/build.sh` at build time, so there is only one copy in git
- File issues in this fork, **please do not bother the upstream author**
