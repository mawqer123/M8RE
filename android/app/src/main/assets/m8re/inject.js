(function () {
  'use strict';
  if (window.__M8RE_INJECTED) return;
  window.__M8RE_INJECTED = true;

  function post(data) {
    try {
      data.source = 'm8re-inject';
      window.postMessage(data, location.origin);
    } catch (e) { }
  }

  var lastOpen = null;
  setInterval(function () {
    var open = null;
    try {
      var p = window.player;
      if (p && p.danmaku && typeof p.danmaku.isOpen === 'function') {
        open = !!p.danmaku.isOpen();
      }
    } catch (e) { }
    if (open !== null && open !== lastOpen) {
      lastOpen = open;
      post({ type: 'dm-state', open: open });
    }
  }, 400);

  try {
    var origSetItem = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      try { origSetItem.call(this, key, value); } catch (e) { }
      if (key === 'bpx_player_profile' || key === 'bilibili_player_settings') {
        post({ type: 'profile-write', key: key });
      }
    };
  } catch (e) { }
})();
