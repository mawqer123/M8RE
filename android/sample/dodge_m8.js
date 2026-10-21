//M8v1
// 《陨石带》 — M8 代码弹幕小游戏
// 操作:← → (或 A D) 移动飞船;躲陨石,吃分数;每 10 分升一级,速度加快。
// 写法说明:全部使用引擎支持的 ES3 子集(11_hostapi 的 globals),无 DOM/网络。
var W = Player.width || 800;
var H = Player.height || 600;
var LX = W / 2, RY = H - 70;            // 飞船默认位置
var SW = 40, SH = 26;                   // 飞船尺寸
var MAXC = 24;                          // 顶部计分格数
var MAXM = 12;                          // 陨石池大小
var MAXP = 24;                          // 爆炸粒子池
var speed = 3, score = 0, state = 1;    // 1=进行 0=结束
var t0 = getTimer();
var left = 0, right = 0;

// ---------- 飞船 ----------
var ship = Display.createShape({ x: LX, y: RY, lifeTime: 999999 });
ship.graphics.beginFill(0x66CCFF);
ship.graphics.moveTo(0, -SH / 2);
ship.graphics.lineTo(SW / 2, SH / 2);
ship.graphics.lineTo(-SW / 2, SH / 2);
ship.graphics.lineTo(0, -SH / 2);
ship.graphics.endFill();
ship.graphics.beginFill(0xFFFFFF, 0.9);
ship.graphics.drawRect(-3, -SH / 2 - 8, 6, 10);
ship.graphics.endFill();

// ---------- 计分格 ----------
var cells = [];
for (var i = 0; i < MAXC; i = i + 1) {
  var c = Display.createShape({ x: 16 + i * 22, y: 22, lifeTime: 999999 });
  c.graphics.beginFill(0x113311);
  c.graphics.drawRect(0, 0, 18, 12);
  c.graphics.endFill();
  cells[i] = c;
}

// ---------- 陨石池 ----------
var meteors = [];
for (var j = 0; j < MAXM; j = j + 1) {
  var m = Display.createShape({ x: -999, y: -999, lifeTime: 999999 });
  m.r = 10 + Math.random() * 12;
  m.on = 0;
  m.graphics.beginFill(0xFF8844);
  m.graphics.drawCircle(0, 0, m.r);
  m.graphics.endFill();
  m.graphics.beginFill(0xAA5522, 0.6);
  m.graphics.drawCircle(-m.r * 0.3, -m.r * 0.3, m.r * 0.4);
  m.graphics.endFill();
  meteors[j] = m;
}

// ---------- 粒子池(爆炸) ----------
var parts = [];
for (var k = 0; k < MAXP; k = k + 1) {
  var p = Display.createShape({ x: -999, y: -999, lifeTime: 999999 });
  p.vx = 0; p.vy = 0; p.on = 0; p.life = 0;
  p.graphics.beginFill(0xFFDD66);
  p.graphics.drawRect(-3, -3, 6, 6);
  p.graphics.endFill();
  parts[k] = p;
}

// ---------- 绘制辅助 ----------
function setFill(el, color, a) {
  el.graphics.clear();
  el.graphics.beginFill(color, a);
}
function drawScore() {
  var full = score - (score % 10);
  var n = full / 10;
  for (var i2 = 0; i2 < MAXC; i2 = i2 + 1) {
    setFill(cells[i2], (i2 < n) ? 0x66FF88 : 0x113311, 1);
    cells[i2].graphics.drawRect(0, 0, 18, 12);
    cells[i2].graphics.endFill();
  }
}
function hide(m2) { m2.on = 0; m2.x = -999; m2.y = -999; }

function spawn() {
  for (var i3 = 0; i3 < MAXM; i3 = i3 + 1) {
    if (!meteors[i3].on) {
      var mm = meteors[i3];
      mm.on = 1;
      mm.r = 10 + Math.random() * (12 + speed * 2);
      mm.y = -40;
      mm.x = 30 + Math.random() * (W - 60);
      setFill(mm, 0xFF8844, 1);
      mm.graphics.drawCircle(0, 0, mm.r);
      mm.graphics.endFill();
      return 1;
    }
  }
  return 0;
}
function boom(x, y) {
  for (var i4 = 0; i4 < MAXP; i4 = i4 + 1) {
    var p2 = parts[i4];
    if (!p2.on) {
      p2.on = 1;
      p2.life = 40;
      p2.x = x;
      p2.y = y;
      p2.vx = (Math.random() - 0.5) * 12;
      p2.vy = (Math.random() - 0.5) * 12;
      p2.alpha = 1;
    }
  }
}
function gameOver() {
  state = 0;
  boom(ship.x, ship.y);
  ship.alpha = 0;
  timer(reset, 2500);
}
function reset() {
  state = 1;
  score = 0;
  speed = 3;
  ship.x = LX; ship.y = RY; ship.alpha = 1;
  for (var i5 = 0; i5 < MAXM; i5 = i5 + 1) hide(meteors[i5]);
  for (var i6 = 0; i6 < MAXP; i6 = i6 + 1) {
    parts[i6].on = 0; parts[i6].x = -999; parts[i6].alpha = 0;
  }
  drawScore();
}

// ---------- 按键 ----------
Player.keyTrigger(function (code) {
  if (code == 37 || code == 65) left = 1;
  if (code == 39 || code == 68) right = 1;
}, 9999999, false);
Player.keyTrigger(function (code2) {
  if (code2 == 37 || code2 == 65) left = 0;
  if (code2 == 39 || code2 == 68) right = 0;
}, 9999999, true);

// ---------- 主循环 ----------
var spawnCd = 30;
interval(function () {
  if (state == 1) {
    if (left) ship.x = ship.x - 8;
    if (right) ship.x = ship.x + 8;
    if (ship.x < SW / 2) ship.x = SW / 2;
    if (ship.x > W - SW / 2) ship.x = W - SW / 2;

    spawnCd = spawnCd - 1;
    if (spawnCd <= 0) {
      spawn();
      spawnCd = 40 - (speed - 3) * 4;
      if (spawnCd < 10) spawnCd = 10;
    }

    for (var i7 = 0; i7 < MAXM; i7 = i7 + 1) {
      var m3 = meteors[i7];
      if (!m3.on) continue;
      m3.y = m3.y + speed + m3.r * 0.12;
      if (m3.y > H + 40) {
        hide(m3);
        score = score + 1;
        if (score % 10 == 0) speed = speed + 1;
        drawScore();
        continue;
      }
      var dx = m3.x - ship.x;
      var dy = m3.y - ship.y;
      if (dx < 0) dx = -dx;
      if (dy < 0) dy = -dy;
      if (dx < m3.r + SW / 2 - 6 && dy < m3.r + SH / 2 - 4) {
        hide(m3);
        gameOver();
      }
    }
  }

  for (var i8 = 0; i8 < MAXP; i8 = i8 + 1) {
    var p3 = parts[i8];
    if (!p3.on) continue;
    p3.life = p3.life - 1;
    p3.x = p3.x + p3.vx;
    p3.y = p3.y + p3.vy;
    p3.alpha = p3.life / 40;
    if (p3.life <= 0) { p3.on = 0; p3.x = -999; p3.alpha = 0; }
  }
}, 33, 0);

drawScore();
