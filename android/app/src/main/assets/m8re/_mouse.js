/* ============================================================
   MoBrowser 附加层:触摸 -> 鼠标事件翻译
   ------------------------------------------------------------
   为什么需要:M8RE 的交互全部是鼠标事件
     - 舞台鼠标位置: container 上的 mousemove(触摸时永远不触发)
     - 可交互元素  : mousedown / mouseup / click / mouseover
   手机浏览器触摸后不一定合成这些事件(页面 preventDefault、覆盖层
   pointer-events:none 等),所以这里在捕获阶段自己翻译一遍。

   只接管 M8RE 覆盖层所在的那块视频画面;其它区域(地址栏、评论区、
   视频外)完全不碰。可通过 window.__moMouseTakeover=false 关闭。
   ============================================================ */
(function () {
  var W = typeof window !== 'undefined' ? window : this;
  if (W.__moMouseInstalled) return;
  W.__moMouseInstalled = true;
  if (typeof W.__moMouseTakeover === "undefined") W.__moMouseTakeover = true;   // 本应用默认开:点击层

  function enabled() { return W.__moMouseTakeover !== false; }

  function overlay() { try { return document.getElementById('m8re-overlay'); } catch (e) { return null; } }

  function rect() {
    var ov = overlay();
    if (!ov) return null;
    try {
      var r = ov.getBoundingClientRect();
      if (!r || r.width < 4 || r.height < 4) return null;
      return r;
    } catch (e) { return null; }
  }

  function inside(r, x, y) {
    return !!r && x >= r.left - 2 && x <= r.right + 2 && y >= r.top - 2 && y <= r.bottom + 2;
  }

  function hit(x, y) {
    var el = null;
    try { el = document.elementFromPoint(x, y); } catch (e) { el = null; }
    return el || document.documentElement;
  }

  function mk(type, x, y, buttons, detail) {
    try {
      return new MouseEvent(type, {
        bubbles: true, cancelable: true, composed: true, view: W,
        clientX: x, clientY: y, screenX: x, screenY: y,
        button: 0, buttons: buttons || 0, detail: detail || 0
      });
    } catch (e) { return null; }
  }

  function fire(el, ev) {
    if (!el || !ev) return;
    try { el.dispatchEvent(ev); } catch (e) { }
  }

  // 一次手势的状态
  var active = false;      // 本次触摸是否由我们接管
  var downX = 0, downY = 0;
  var prev = null;         // 上一次 mouseover 的元素
  var downEl = null;       // mousedown 的目标(click 必须发给同一个元素)
  var lastTapT = 0, lastTapX = 0, lastTapY = 0, lastTapEl = null;

  function hover(el, x, y) {
    if (el === prev) return;
    fire(prev, mk('mouseout', x, y, 0));
    fire(prev, mk('mouseleave', x, y, 0));
    fire(el, mk('mouseover', x, y, 0));
    fire(el, mk('mouseenter', x, y, 0));
    prev = el;
  }

  function onStart(e) {
    if (!enabled() || e.touches.length !== 1) { active = false; return; }
    var r = rect();
    var t = e.touches[0];
    if (!inside(r, t.clientX, t.clientY)) { active = false; return; }
    active = true;
    downX = t.clientX; downY = t.clientY;
    var el = hit(downX, downY);
    downEl = el;
    hover(el, downX, downY);
    fire(el, mk('mousemove', downX, downY, 1));
    fire(el, mk('mousedown', downX, downY, 1));
    e.preventDefault();
    e.stopPropagation();
  }

  function onMove(e) {
    if (!active || !enabled() || e.touches.length !== 1) return;
    var t = e.touches[0];
    var el = hit(t.clientX, t.clientY);
    downX = t.clientX; downY = t.clientY;
    hover(el, downX, downY);
    fire(el, mk('mousemove', downX, downY, 1));
    e.preventDefault();
    e.stopPropagation();
  }

  function onEnd(e) {
    if (!active) return;
    active = false;
    if (!enabled()) return;
    var t = (e.changedTouches && e.changedTouches[0]) || null;
    if (!t) return;
    var x = t.clientX, y = t.clientY;
    var el = downEl || hit(x, y);
    fire(el, mk('mouseup', x, y, 0));
    fire(el, mk('click', x, y, 0, 1));
    // 双击:同一点附近、320ms 内连点两次
    var now = Date.now();
    if (now - lastTapT < 320 && Math.abs(x - lastTapX) < 28 && Math.abs(y - lastTapY) < 28
        && lastTapEl === el) {
      fire(el, mk('dblclick', x, y, 0, 2));
      lastTapT = 0;
    } else {
      lastTapT = now; lastTapX = x; lastTapY = y; lastTapEl = el;
    }
    e.preventDefault();
    e.stopPropagation();
  }

  function onCancel() {
    if (!active) return;
    active = false;
    if (!enabled()) return;
    var el = downEl || prev;
    fire(el, mk('mouseup', downX, downY, 0));
  }

  // 捕获阶段:比页面自己的 touch 监听更早拿到事件
  try {
    document.addEventListener('touchstart', onStart, { capture: true, passive: false });
    document.addEventListener('touchmove', onMove, { capture: true, passive: false });
    document.addEventListener('touchend', onEnd, { capture: true, passive: false });
    document.addEventListener('touchcancel', onCancel, { capture: true, passive: false });
  } catch (e) {
    document.addEventListener('touchstart', onStart, true);
    document.addEventListener('touchmove', onMove, true);
    document.addEventListener('touchend', onEnd, true);
    document.addEventListener('touchcancel', onCancel, true);
  }

  console.log('[M8RE/MoBrowser] touch->mouse layer ready');
})();
