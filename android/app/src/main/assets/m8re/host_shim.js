(function () {
  'use strict';
  var W = typeof window !== 'undefined' ? window : this;
  if (W.__M8RE_SHIM__) return;
  W.__M8RE_SHIM__ = true;

  var IS_ANDROID = !!(W.M8Native && W.M8Native.getUserAgent);
  W.__M8RE_ANDROID = IS_ANDROID ? 1 : 0;

  function log(msg) {
    try { if (W.M8NativeLog) W.M8NativeLog.log('[shim] ' + msg); } catch (e) { }
    try { console.log('%c[M8RE-shim]', 'color:#fff;background:#e11;padding:0 4px', msg); } catch (e) { }
  }

  try {
    if (W.chrome && W.chrome.runtime && W.chrome.runtime.id) {
      try { W.chrome.runtime.id = undefined; } catch (e) { }
      try { delete W.chrome.runtime.id; } catch (e) { }
    }
  } catch (e) { }

  (function hookConsole() {
    var MAX = 220, sent = 0;
    function fmt(a) {
      try {
        if (a === null) return 'null';
        if (a === undefined) return 'undefined';
        if (typeof a === 'string') return a;
        if (a instanceof Error) return (a.stack || a.message || String(a)).split('\n')[0];
        if (typeof a === 'object') {
          if (a.nodeType === 1) return '<' + (a.tagName || 'el') + '>';
          var j = JSON.stringify(a);
          return j && j.length > 400 ? j.slice(0, 400) + '…' : j;
        }
        return String(a);
      } catch (e) { return '[obj]'; }
    }
    ['log', 'warn', 'error', 'info'].forEach(function (k) {
      var orig = console[k] ? console[k].bind(console) : function () { };
      console[k] = function () {
        try { orig.apply(null, arguments); } catch (e) { }
        try {
          if (sent < MAX && W.M8NativeLog) {
            sent++;
            var parts = [];
            for (var i = 0; i < arguments.length; i++) parts.push(fmt(arguments[i]));
            var line = parts.join(' ');
            if (line.length > 500) line = line.slice(0, 500) + '…';
            W.M8NativeLog.log('[console.' + k + '] ' + line);
          }
        } catch (e) { }
      };
    });
  })();

  var _pending = {};
  var _seq = 0;
  W.__M8NativeDeliver = function (id, b64, err) {
    var p = _pending[id];
    if (!p) return;
    delete _pending[id];
    if (err) { p.reject(new Error(err)); return; }
    if (b64 == null) { p.reject(new Error('native fetch returned null')); return; }
    try {
      var bin = atob(b64);
      var bytes = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      p.resolve(bytes.buffer);
    } catch (e) { p.reject(e); }
  };

  function nativeFetch(url) {
    return new Promise(function (resolve, reject) {
      if (!IS_ANDROID) { reject(new Error('no native bridge')); return; }
      var id = 'n' + (++_seq);
      _pending[id] = { resolve: resolve, reject: reject };
      try { W.M8Native.get(String(url), id); }
      catch (e) { delete _pending[id]; reject(e); return; }
      setTimeout(function () {
        if (_pending[id]) { delete _pending[id]; reject(new Error('native fetch timeout')); }
      }, 25000);
    });
  }
  W.__M8NativeFetch = nativeFetch;

  var _origFetch = W.fetch ? W.fetch.bind(W) : null;

  function isBili(u) {
    u = String(u || '');
    if (/hdslb\.com|bilivideo\.com|bilivideo\.cn/i.test(u)) return true;
    if (/api\.bilibili\.com\/|api\.live\.bilibili\.com\//i.test(u)) return true;
    return false;
  }

  function makeResponse(buf, url) {
    return {
      ok: true, status: 200, statusText: 'OK', url: url,
      headers: {
        get: function (k) {
          return String(k).toLowerCase() === 'content-type' ? 'application/octet-stream' : null;
        }
      },
      arrayBuffer: function () { return Promise.resolve(buf); },
      blob: function () { return Promise.resolve(new Blob([buf])); },
      text: function () {
        return Promise.resolve(new TextDecoder('utf-8').decode(new Uint8Array(buf)));
      },
      json: function () {
        return Promise.resolve(JSON.parse(new TextDecoder('utf-8').decode(new Uint8Array(buf))));
      },
      clone: function () { return makeResponse(buf, url); }
    };
  }

  W.fetch = function (input, init) {
    var url = (typeof input === 'string') ? input : (input && input.url) || '';
    var attempt = _origFetch ? _origFetch(input, init)
      : Promise.reject(new Error('no fetch'));
    return attempt.catch(function (e1) {
      var aborted = (e1 && (e1.name === 'AbortError'
        || /aborted|abort/i.test(e1.message || ''))
        || (init && init.signal && init.signal.aborted));
      if (aborted) throw e1;
      if (isBili(url) && IS_ANDROID) {
        log('direct fetch failed → native: ' + url + ' (' + e1 + ')');
        return nativeFetch(url).then(function (buf) { return makeResponse(buf, url); });
      }
      throw e1;
    });
  };

  try {
    if (W.chrome && W.chrome.runtime && W.chrome.runtime.id) {
      if (!W.chrome.runtime.sendMessage) {
        W.chrome.runtime.sendMessage = function (msg, cb) {
          if (!msg || msg.type !== 'm8re-fetch') { if (cb) cb({ ok: false }); return; }
          nativeFetch(msg.url).then(function (buf) {
            var bin = '';
            var bytes = new Uint8Array(buf);
            for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
            if (cb) cb({ ok: true, b64: btoa(bin) });
          }).catch(function (e) { if (cb) cb({ ok: false, error: String(e) }); });
        };
      }
      W.chrome.runtime.lastError = null;
    }
  } catch (e) { }

  var KEY_NAMES = {
    27: 'Escape', 37: 'ArrowLeft', 38: 'ArrowUp', 39: 'ArrowRight', 40: 'ArrowDown',
    65: 'a', 68: 'd', 83: 's', 87: 'w',
    96: '0', 97: '1', 98: '2', 99: '3', 100: '4',
    101: '5', 102: '6', 103: '7', 104: '8', 105: '9'
  };
  try {
    document.addEventListener('keydown', function () {
      W.__moKeySeenDown = (W.__moKeySeenDown || 0) + 1;
    }, true);
    document.addEventListener('keyup', function () {
      W.__moKeySeenUp = (W.__moKeySeenUp || 0) + 1;
    }, true);
  } catch (e) { }

  try {
    var _origAddEL = document.addEventListener.bind(document);
    W.__moKeyHooks = 0;
    document.addEventListener = function (type, fn, opts) {
      if ((type === 'keydown' || type === 'keyup') && W.__moKeyHooks >= 0) {
        W.__moKeyHooks++;
        try {
          if (W.M8NativeLog) {
            W.M8NativeLog.log('[hook] game registered ' + type + ' hook #' + W.__moKeyHooks);
          }
        } catch (e) { }
      }
      return _origAddEL(type, fn, opts);
    };
  } catch (e) { }

  W.__moKey = function (type, code, quiet) {
    try {
      var name = KEY_NAMES[code] || '';
      var ev = new KeyboardEvent(type, {
        bubbles: true, cancelable: true, composed: true,
        key: name, code: name, view: W
      });
      try { Object.defineProperty(ev, 'keyCode', { get: function () { return code; } }); } catch (e) { }
      try { Object.defineProperty(ev, 'which', { get: function () { return code; } }); } catch (e) { }
      try { Object.defineProperty(ev, 'charCode', { get: function () { return code; } }); } catch (e) { }
      var tgt = null;
      try {
        tgt = document.querySelector('#m8re-overlay')
           || document.querySelector('.bpx-player-container')
           || document.querySelector('#bilibili-player') || document.body || document;
      } catch (e3) { tgt = document; }
      tgt.dispatchEvent(ev);
      if (!quiet) {
        try {
          if (W.M8NativeLog) W.M8NativeLog.log('[key] ' + type + ' ' + code
              + ' seen=' + (W.__moKeySeenDown || 0) + '/' + (W.__moKeySeenUp || 0)
              + ' hooks=' + (W.__moKeyHooks || 0));
        } catch (e2) { }
      }
      return true;
    } catch (e) {
      log('key dispatch failed: ' + e);
      return false;
    }
  };

  var _errCount = 0;
  function jslog(tag, a) {
    if (_errCount++ > 40) return;
    try {
      var s = tag;
      if (a) {
        if (a.message) s += ' ' + a.message;
        if (a.filename) s += ' @' + a.filename + ':' + (a.lineno || 0);
        else s += ' ' + a;
      }
      log(s);
    } catch (e) { }
  }
  W.addEventListener('error', function (e) { jslog('JS error:', e); });
  W.addEventListener('unhandledrejection', function (e) {
    jslog('unhandled rejection:', e && e.reason ? (e.reason.message || e.reason) : e);
  });

  log('host shim installed (android=' + IS_ANDROID + ')');
})();
