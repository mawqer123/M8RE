# M8RE

**bilibili Mode 8 弹幕 JS 运行时环境（或模拟器）。**

M8RE 是一个 Chrome 扩展，用于在现代 bilibili HTML5 播放器上复活 Flash 时代的
**M8 代码弹幕**（mode 8，又称 *biliscript*）。它用 JavaScript 从零重新实现了当年的
M8 虚拟机，并作为浮层运行在页面上。

[English](README.md) | 中文

## 背景

约 2012 至 2017 年间，bilibili 的 Flash 播放器支持「代码弹幕」：弹幕内容不是文字，
而是一整段用 *biliscript*（一种 ECMAScript 方言）编写的脚本（mode 8），由播放器内的
沙盒 VM 执行。作者们用它做出在弹幕层里运行的小游戏、粒子特效、互动故事和音乐可视化。
Flash 播放器退役后，这些作品就失效了。

M8RE 让它们在今天的播放器上重新跑起来。

## 工作原理

M8RE 的 content script 先取回弹幕 protobuf 数据（分段包、`dm/web/view` 元数据以及
specialDm 包），再用 biliscript VM 执行其中的 mode 8 条目 —— 这个 VM 是从旧版 AS3
播放器移植过来的字节码解释器，覆盖 61 条指令集、作用域链与错误语义。脚本通过宿主 API
（`$`、`Player`、`Utils`、`ScriptManager`、`$G` 等）交互，其行为遵循旧播放器，并渲染到
一个复刻 Flash 显示列表的 DOM/canvas 浮层上：注册点变换、`Graphics` 绘图、
`BitmapData` 像素操作、帧同步定时器与补间动画。

原生弹幕会作为隐藏载体挂在浮层根节点上，因此那些监听
`$.root.addEventListener("added", ...)` 的脚本 —— 大多数 M8 游戏赖以工作的接管范式 ——
无需修改即可运行。

M8RE 也会读取内容以 `//M8v1` 开头的 mode 9 BAS 条目，并在该弹幕自身的时间戳处将其作为
M8 脚本执行。（`//` 在 biliscript 中是注释，对官方 BAS 渲染器则是词法错误，因此这个载体
对其他人不可见。）这意味着今天就可以直接通过 bilibili 的弹幕系统发布新的 M8 作品。

## 兼容性：尚不理想

**本模拟器仍处于早期阶段，兼容性目前并不好。** 经典作品中相当一部分可以运行，但不少脚本
仍会表现异常或直接失败。请把它当作一个可用的原型，而不是已经完成的保存工具。

已知不足：

- Mode 7 高级弹幕的 3D 渲染可用，但并非像素级精确（源码中标记为 `TODO`）。
- Flash 设备字体文本度量（GDI 网格拟合）为近似实现。
- 部分 Flash 滤镜（模糊、高级发光）为近似实现或缺失。
- 少数作品依赖播放器的「黑科技」特性或外部加载器，而这些在 Flash 退役之前就已失效。

非常欢迎提交兼容性反馈与测试用例。

## 安装（开发者模式）

1. 克隆或下载本仓库。
2. 打开 `chrome://extensions`，启用 **开发者模式**。
3. 点击 **加载已解压的扩展程序**，选择扩展目录。
4. 打开任意 bilibili 视频页。经典 mode 8 脚本会自动运行；带 `//M8v1` BAS 载体的代码弹幕
   会从 BAS 通道被识别并执行。

## 调试开关

| localStorage 键 | 作用 |
|---|---|
| `m8re_debug = '0'` | 关闭引擎日志（默认：详细输出） |
| `m8re_badge = '1'` | 显示页面内状态角标 |

## 写给作者：通过 BAS 发布 M8

M8 脚本可以作为不可见的 BAS 载体，通过 bilibili 的弹幕服务器发布：

1. 把你的 M8 脚本的第一行写成恰好是 `//M8v1`。
2. 以 `mode=9`、`pool=2` 发送一条弹幕，时间戳设为脚本应当触发的位置
   （需要该视频的高级弹幕权限）。
3. 一个 BAS 条目承载一个脚本；不同时间戳承载不同脚本；扩展在拖动进度条时会像原生
   mode 8 一样重放它们。

## 许可

基于 [MIT 许可证](LICENSE) 发布。

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
- 安装、构建、虚拟手柄键位、许可与免责声明见 [android/README.zh-CN.md](android/README.zh-CN.md)
- 引擎源码位于仓库根的 `content/`，`android/build.sh` 会在构建时自动同步，
  因此不存在两份会不一致的副本
- 有问题请在本 fork 提 issue，**不要打扰上游作者**
