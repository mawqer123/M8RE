(function () {
  var W = typeof window !== 'undefined' ? window : this;
  if (W.__moMouseInstalled) return;
  W.__moMouseInstalled = true;
  if (typeof W.__moMouseTakeover === "undefined") W.__moMouseTakeover = true;

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

  var active = false;
  var downX = 0, downY = 0;
  var prev = null;
  var downEl = null;
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
