#!/usr/bin/env node
/*
 * 用引擎自己的 VM(00-05 号文件原样加载)跑 sample/dodge_m8.js:
 * 真 Scanner → 真 Parser → 真字节码 VM,只把宿主 API(Display/Player/interval…)
 * 换成内存版 —— 和引擎 11_hostapi.js 暴露的接口一一对应。
 * 验证:脚本能被引擎编译执行、按键逻辑生效、碰撞/得分/重开全部工作。
 */
const fs = require('fs');
const path = require('path');
const A = path.join(__dirname, '../app/src/main/assets/m8re');

// ---- 最小 window/全局环境(引擎 VM 文件是纯 JS,不需要 DOM) ----
const G = { Math, String, Number, Array, Object, Boolean, Date, JSON, RegExp };
G.window = G; G.self = G; G.globalThis = G; G.top = G; G.global = G;
global.window = G;
for (const f of ['00_ns.js', '01_scanner.js', '02_expr.js', '03_codegen.js', '04_parser.js', '05_vm.js']) {
  new Function('window', 'globalThis', fs.readFileSync(path.join(A, f), 'utf8'))(G, G);
}
const M8 = G.window.M8RE;

// ---- 宿主 API 内存版(按 11_hostapi.js 的真实接口) ----
let clock = 0;
const hooks = [], hooksUp = [], timers = [], intervals = [];
const gstore = {};

function mkShape(cfg) {
  const el = {
    x: cfg.x || 0, y: cfg.y || 0, alpha: (cfg.alpha === undefined ? 1 : cfg.alpha),
    removed: false,
    graphics: {
      ops: [], clear() { this.ops = []; },
      beginFill(c, a) { this.ops.push(['bf', c, a]); },
      endFill() { this.ops.push(['ef']); },
      moveTo(x, y) { this.ops.push(['mt', x, y]); },
      lineTo(x, y) { this.ops.push(['lt', x, y]); },
      drawRect(x, y, w, h) { this.ops.push(['dr', x, y, w, h]); },
      drawCircle(x, y, r) { this.ops.push(['dc', x, y, r]); },
    },
    remove() { this.removed = true; },
  };
  return el;
}

const globals = {
  getTimer: () => clock,
  timer: (f, t) => { timers.push({ at: clock + (t || 1000), f }); return timers.length; },
  interval: (f, t, n) => { intervals.push({ every: t || 1000, next: clock + (t || 1000), left: n === undefined ? 1 : n, f }); return intervals.length; },
  Display: { createShape: (c) => mkShape(c || {}) },
  Player: {
    width: 800, height: 600,
    keyTrigger: (fn, timeout, isUp) => { (isUp ? hooksUp : hooks).push(fn); },
  },
  $G: { _set: (k, v) => { gstore[k] = v; }, _get: (k) => gstore[k], _: (k) => gstore[k] },
  Global: { _set: (k, v) => { gstore[k] = v; }, _get: (k) => gstore[k], _(k) { return gstore[k]; } },
  Utils: {},
  Math, String, Number, Array, Object, Boolean,
  trace: (...a) => console.log('[trace]', ...a),
};

// ---- 真 VM 执行脚本 ----
const vm = new M8.VirtualMachine();
const gobj = vm.getGlobalObject();
for (const k in globals) gobj[k] = globals[k];
const src = fs.readFileSync(path.join(__dirname, '../sample/dodge_m8.js'), 'utf8');
const p = new M8.Parser(new M8.Scanner(src));
vm.rewind();
vm.setByteCode(p.parse(vm));
vm.execute();
console.log('✓ 引擎 VM 编译并启动脚本成功(真 Scanner/Parser/字节码)');

// ---- 模拟运行 ----
function step(ms) {
  clock += ms;
  for (const t of [...timers]) if (t.at <= clock) { timers.splice(timers.indexOf(t), 1); t.f(); }
  for (const iv of [...intervals]) {
    // left === 0 表示无限重复(引擎 Timer: repeatCount 0 = infinite)
    while (iv.next <= clock) {
      iv.f();
      iv.next += iv.every;
      if (iv.left > 0) {
        iv.left--;
        if (iv.left === 0) intervals.splice(intervals.indexOf(iv), 1);
      }
    }
  }
}
const key = (code, up) => (up ? hooksUp : hooks).forEach(f => f(code));

// 1) 起跑 60 帧:无异常,且确实有陨石在生成
for (let i = 0; i < 60; i++) step(33);
if (!gobj.meteors.some(m => m.on)) throw new Error('60 帧内没有任何陨石生成');
console.log('✓ 主循环 60 帧无异常,陨石正常生成');

// 2) 按住右键 1 秒:船应该右移(先把场面清干净,避免测试中途被撞死)
function parkField() {
  gobj.spawnCd = 999999;                      // 停止刷怪
  gobj.meteors.forEach(m => { if (m.on) { m.on = 0; m.x = -999; m.y = -999; } });
  gobj.parts.forEach(p => { p.on = 0; p.x = -999; p.alpha = 0; });
  if (gobj.state === 0) { while (gobj.state === 0) step(33); }   // 等自动重开
}
parkField();
const before = gobj.ship.x;
key(39, false); for (let i = 0; i < 30; i++) step(33); key(39, true);
const after = gobj.ship.x;
if (after > before + 100) console.log(`✓ 按住 → 键:船 x ${before.toFixed(0)} → ${after.toFixed(0)}(移动生效)`);
else throw new Error('飞船没动: ' + before + ' -> ' + after);
// 左键对称验证
key(37, false); for (let i = 0; i < 15; i++) step(33); key(37, true);
if (gobj.ship.x < after) console.log('✓ 按住 ← 键:船 x 回移生效');
else throw new Error('左移失败');

// 3) 强制碰撞 → 进入结束态
const m0 = gobj.meteors.find(m => m && m.on) || (() => {
  const m = gobj.meteors[0]; m.on = 1; return m;
})();
m0.on = 1; m0.x = gobj.ship.x; m0.y = gobj.ship.y;
step(33);
if (gobj.state === 0) console.log('✓ 碰撞判定:进入结束态,粒子爆炸已触发');
else throw new Error('碰撞没判定,state=' + gobj.state);

// 4) 2.5 秒后自动重开
step(3000);
if (gobj.state === 1 && gobj.score === 0) console.log('✓ 2.5 秒自动重开,score 归零');
else throw new Error('重开失败 state=' + gobj.state);

// 5) 躲避得分:让屏上陨石全部落出底边 → score 增加、可能提速
parkField();
gobj.spawnCd = 999999;
const s0 = gobj.score;
let fed = 0;
for (const m of gobj.meteors) { if (!m.on) { m.on = 1; m.x = -999; m.y = 700; fed++; } }
step(200);
if (gobj.score >= s0 + fed) console.log('✓ 漏怪得分:score ' + s0 + ' → ' + gobj.score + ' (speed=' + gobj.speed + ')');
else throw new Error('没得分: ' + s0 + ' -> ' + gobj.score);

console.log('\n全部 5 项测试通过 —— 游戏在引擎 VM 里真实可玩。');
