/* destiny 运行时功能层
 * ------------------------------------------------------------------
 * 设计稿（mibot-v7.html）只负责画界面，这个文件负责让界面真的能干活。
 * 由 _build_app.js 注入到 destiny-app.html 末尾，不改动设计稿一个字符。
 *
 * 当前接通：对话（输入 / 请求 / 回复 / 历史 / 配置）
 * 尚未接通：SSH 服务器、终端、Tailscale、登录（仍为界面占位）
 *
 * 约定：
 *  - 配置全部来自用户在设置页的输入，存 localStorage，代码里不含任何密钥
 *  - 接口按 OpenAI 兼容格式 POST {baseUrl}/chat/completions
 *  - 4xx 不重试，5xx / 网络失败最多重试 3 次，15 秒超时
 *  - 错误一律映射成中文可读文案，不把堆栈抛给用户
 */
(function () {
  'use strict';

  var CFG_KEY = 'destiny.api.v1';
  var CHAT_KEY = 'destiny.chat.v1';
  var MAX_HISTORY = 50;
  var history = [];
  var busy = false;

  /* ---------- 存储 ---------- */
  function readJSON(key, fallback) {
    try {
      var v = localStorage.getItem(key);
      return v ? JSON.parse(v) : fallback;
    } catch (e) { return fallback; }
  }
  function writeJSON(key, val) {
    try { localStorage.setItem(key, JSON.stringify(val)); return true; }
    catch (e) { return false; }
  }
  function getConfig() {
    var c = readJSON(CFG_KEY, {});
    return {
      baseUrl: String(c.baseUrl || '').trim().replace(/\/+$/, ''),
      apiKey: String(c.apiKey || ''),
      model: String(c.model || '').trim()
    };
  }
  function configReady(c) { return !!(c.baseUrl && c.apiKey && c.model); }

  /* ---------- 小工具 ---------- */
  function $(sel, root) { return (root || document).querySelector(sel); }
  function toast(msg) {
    var t = document.getElementById('dzToast');
    if (!t) {
      t = document.createElement('div');
      t.id = 'dzToast';
      t.style.cssText = 'position:fixed;left:50%;bottom:78px;transform:translateX(-50%);'
        + 'z-index:999;max-width:82%;padding:10px 15px;border-radius:12px;'
        + 'background:#24242A;color:#F2F0EB;font:500 12px/1.5 system-ui,sans-serif;'
        + 'box-shadow:0 10px 30px rgba(0,0,0,.45);opacity:0;transition:opacity .18s;'
        + 'pointer-events:none;text-align:center';
      document.body.appendChild(t);
    }
    t.textContent = msg;
    t.style.opacity = '1';
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { t.style.opacity = '0'; }, 2600);
  }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  /* ---------- 设置页：接口配置 ---------- */
  function buildSettings() {
    var scr = $('.sc[data-id="16"] .scr');
    if (!scr || document.getElementById('dzApiCard')) return;

    var card = el('div', 'card');
    card.id = 'dzApiCard';
    card.style.cssText = 'margin:16px 0 26px;padding:15px';

    var h = el('div', '', '模型接口');
    h.style.cssText = 'font-size:13px;font-weight:600;margin-bottom:4px';
    card.appendChild(h);

    var p = el('div', '', '填自己的网关地址和 Key，只存在本机，不会上传。支持 OpenAI 兼容格式。');
    p.style.cssText = 'font-size:11.5px;line-height:1.6;color:var(--tx3);margin-bottom:14px';
    card.appendChild(p);

    var fields = [
      { k: 'baseUrl', label: 'Base URL', ph: 'https://your-gateway.example.com/v1', type: 'text' },
      { k: 'apiKey', label: 'API Key', ph: 'sk-…', type: 'password' },
      { k: 'model', label: '模型名', ph: '例如 longcat-flash-chat', type: 'text' }
    ];
    var inputs = {};
    fields.forEach(function (f) {
      var lb = el('div', '', f.label);
      lb.style.cssText = 'font-size:10.5px;color:var(--tx3);margin:10px 0 5px';
      card.appendChild(lb);
      var inp = document.createElement('input');
      inp.type = f.type;
      inp.placeholder = f.ph;
      inp.autocomplete = 'off';
      inp.setAttribute('data-k', f.k);
      inp.style.cssText = 'width:100%;box-sizing:border-box;padding:10px 11px;border-radius:9px;'
        + 'border:1px solid var(--ln);background:var(--sf);color:var(--tx);'
        + 'font:400 12px/1.4 system-ui,sans-serif;outline:none';
      card.appendChild(inp);
      inputs[f.k] = inp;
    });

    var bar = el('div');
    bar.style.cssText = 'display:flex;gap:8px;margin-top:14px;align-items:center';
    var save = el('button', '', '保存');
    save.type = 'button';
    save.style.cssText = 'flex:1;padding:9px;border:0;border-radius:9px;background:var(--cu);'
      + 'color:#1A120B;font:600 12px system-ui,sans-serif;cursor:pointer';
    var test = el('button', '', '测试连接');
    test.type = 'button';
    test.style.cssText = 'flex:1;padding:9px;border:1px solid var(--ln);border-radius:9px;'
      + 'background:transparent;color:var(--tx);font:600 12px system-ui,sans-serif;cursor:pointer';
    bar.appendChild(save);
    bar.appendChild(test);
    card.appendChild(bar);

    var status = el('div');
    status.id = 'dzApiStatus';
    status.style.cssText = 'font-size:11px;line-height:1.6;margin-top:10px;color:var(--tx3)';
    card.appendChild(status);
    scr.appendChild(card);

    var c = getConfig();
    fields.forEach(function (f) { inputs[f.k].value = c[f.k] || ''; });
    status.textContent = configReady(c) ? '已配置，可以去对话屏发消息了。' : '还没配置，填完点保存。';

    save.addEventListener('click', function () {
      var next = {};
      fields.forEach(function (f) { next[f.k] = inputs[f.k].value.trim(); });
      if (next.baseUrl && !/^https?:\/\//i.test(next.baseUrl)) {
        status.style.color = '#E8A15C';
        status.textContent = 'Base URL 要以 http:// 或 https:// 开头。';
        return;
      }
      if (!writeJSON(CFG_KEY, next)) {
        status.style.color = '#E8A15C';
        status.textContent = '这台设备不允许本地存储，配置没能保存。';
        return;
      }
      status.style.color = configReady(next) ? '#7FC8A0' : '#E8A15C';
      status.textContent = configReady(next) ? '已保存，可以去对话屏发消息了。' : '已保存，但还缺字段。';
      toast('接口配置已保存');
    });

    test.addEventListener('click', function () {
      var c2 = getConfig();
      if (!configReady(c2)) {
        status.style.color = '#E8A15C';
        status.textContent = '先填完整三项并保存，再测试。';
        return;
      }
      status.style.color = 'var(--tx3)';
      status.textContent = '正在测试…';
      request([{ role: 'user', content: 'ping' }]).then(function (txt) {
        status.style.color = '#7FC8A0';
        status.textContent = '连接正常，模型回了 ' + txt.length + ' 个字符。';
      }).catch(function (e) {
        status.style.color = '#E8A15C';
        status.textContent = '测试失败：' + humanError(e);
      });
    });
  }

  /* ---------- 请求层 ---------- */
  function buildUrl(base) {
    return /\/chat\/completions$/.test(base) ? base : base + '/chat/completions';
  }
  function endpointOf(base) { return buildUrl(base); }

  function once(messages, signal) {
    var c = getConfig();
    if (!configReady(c)) {
      var e0 = new Error('未配置接口'); e0.status = 0; throw e0;
    }
    return fetch(endpointOf(c.baseUrl), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + c.apiKey
      },
      body: JSON.stringify({ model: c.model, messages: messages, stream: false }),
      signal: signal
    }).then(function (res) {
      if (!res.ok) {
        var err = new Error('HTTP ' + res.status);
        err.status = res.status;
        return res.json().catch(function () { return null; }).then(function (j) {
          if (j && j.error && j.error.message) err.message = j.error.message;
          throw err;
        });
      }
      return res.json();
    }).then(function (j) {
      var out = j && j.choices && j.choices[0] && j.choices[0].message
        && j.choices[0].message.content;
      if (!out && j) out = j.content || j.text || (j.data && j.data.content);
      if (out == null || out === '') {
        var e1 = new Error('响应里没有内容'); e1.status = 0; throw e1;
      }
      return String(out);
    });
  }

  function request(messages) {
    var ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, 15000) : null;
    var attempt = 0;
    function run() {
      attempt++;
      return once(messages, ctrl ? ctrl.signal : undefined).catch(function (e) {
        // 4xx 是配置/权限问题，重试没有意义；超时也不重试
        if ((e.status >= 400 && e.status < 500) || e.name === 'AbortError') throw e;
        if (attempt >= 3) throw e;
        return new Promise(function (r) { setTimeout(r, 300 * attempt); }).then(run);
      });
    }
    return run().then(function (v) {
      if (timer) clearTimeout(timer);
      return v;
    }, function (e) {
      if (timer) clearTimeout(timer);
      throw e;
    });
  }

  function humanError(e) {
    if (e.message === '未配置接口') return '还没填接口配置';
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return '手机当前离线';
    if (e.name === 'AbortError') return '请求超时（15 秒）';
    if (e.status === 401) return 'API Key 不对或被拒绝';
    if (e.status === 403) return '这个 Key 没有权限';
    if (e.status === 404) return '接口地址不对，检查 Base URL';
    if (e.status === 429) return '被限流了，等一下再发';
    if (e.status >= 500) return '服务端出错，重试 3 次仍失败';
    return e.message || '连不上接口';
  }

  /* ---------- 对话屏：真输入 + 消息 ---------- */
  var chatScr = null, chatInput = null, sendBtn = null;

  function appendBubble(role, text) {
    if (!chatScr) return null;
    var b = el('div', role === 'user' ? 'me' : 'ai', text);
    b.style.marginTop = '14px';
    b.setAttribute('data-dz', '1');
    if (role === 'error') {
      b.className = 'ai';
      b.style.color = '#E8A15C';
    }
    chatScr.appendChild(b);
    scrollBottom();
    return b;
  }
  function scrollBottom() {
    if (chatScr) chatScr.scrollTop = chatScr.scrollHeight;
  }
  function saveHistory() {
    writeJSON(CHAT_KEY, history.slice(-MAX_HISTORY));
  }
  function context() {
    return history.slice(-10).map(function (m) {
      return { role: m.role, content: m.content };
    });
  }

  function send(text) {
    if (busy) return Promise.resolve(false);
    text = String(text == null ? (chatInput ? chatInput.value : '') : text).trim();
    if (!text) return Promise.resolve(false);

    if (!configReady(getConfig())) {
      toast('还没配置模型接口，去「设置 → 模型接口」填一下');
      if (window.__show) window.__show('16', true);
      return Promise.resolve(false);
    }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      appendBubble('error', '手机当前离线，联网后再发一次。');
      return Promise.resolve(false);
    }

    busy = true;
    if (chatInput) { chatInput.value = ''; chatInput.blur(); }
    if (sendBtn) sendBtn.style.opacity = '.5';
    appendBubble('user', text);

    var wait = appendBubble('ai', '正在回复…');
    if (wait) wait.style.opacity = '.6';

    var messages = context().concat([{ role: 'user', content: text }]);
    return request(messages).then(function (reply) {
      if (wait) { wait.textContent = reply; wait.style.opacity = '1'; }
      history.push({ role: 'user', content: text });
      history.push({ role: 'assistant', content: reply });
      if (history.length > MAX_HISTORY) history = history.slice(-MAX_HISTORY);
      saveHistory();
      scrollBottom();
      busy = false;
      if (sendBtn) sendBtn.style.opacity = '1';
      return reply;
    }).catch(function (e) {
      if (wait) { wait.textContent = '发送失败：' + humanError(e); wait.style.color = '#E8A15C'; wait.style.opacity = '1'; }
      toast('发送失败：' + humanError(e));
      busy = false;
      if (sendBtn) sendBtn.style.opacity = '1';
      return false;
    });
  }

  function buildChat() {
    var sc = $('.sc[data-id="02"]');
    if (!sc || document.getElementById('dzChatInput')) return;
    chatScr = $('.scr', sc);
    var comp = $('.comp', sc);
    if (!chatScr || !comp) return;

    // 假的占位文字换成真输入框，保留原来那一格的位置和样式
    var holder = comp.firstElementChild;
    if (!holder) return;
    holder.innerHTML = '';
    holder.style.cssText = 'flex:1;min-width:0;padding:2px 0 4px';
    var inp = document.createElement('input');
    inp.id = 'dzChatInput';
    inp.type = 'text';
    inp.placeholder = '发消息给 Destiny…';
    inp.autocomplete = 'off';
    inp.setAttribute('aria-label', '消息内容');
    inp.style.cssText = 'width:100%;box-sizing:border-box;border:0;outline:0;background:transparent;'
      + 'color:var(--tx);font:400 13px/1.4 system-ui,sans-serif;padding:4px 0';
    inp.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.preventDefault(); send(inp.value); }
    });
    holder.appendChild(inp);
    chatInput = inp;

    // 右下角那个圆圈就是发送键
    sendBtn = $('.go', comp);
    if (sendBtn) {
      sendBtn.style.cursor = 'pointer';
      sendBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        send(inp.value);
      });
    }

    // 恢复本机会话
    history = readJSON(CHAT_KEY, []);
    if (!Array.isArray(history)) history = [];
    history.forEach(function (m) {
      if (m && m.role && m.content) appendBubble(m.role, m.content);
    });
    if (history.length) scrollBottom();
  }

  /* ---------- 占位能力：点了要看得见反馈，不能静默 ---------- */
  function wireNativePlaceholders() {
    document.addEventListener('click', function (e) {
      var n = e.target && e.target.closest ? e.target.closest('[data-native]') : null;
      if (!n) return;
      var label = (n.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 14);
      toast((label || '这个动作') + '还没接后端，当前只是界面占位');
    }, false);
  }

  /* ---------- 启动 ---------- */
  function init() {
    try { buildChat(); } catch (e) { /* 单块失败不拖垮整页 */ }
    try { buildSettings(); } catch (e) { /* 同上 */ }
    try { wireNativePlaceholders(); } catch (e) { /* 同上 */ }
    window.destinyRuntime = {
      setConfig: function (c) { writeJSON(CFG_KEY, c || {}); return getConfig(); },
      getConfig: getConfig,
      clearChat: function () { history = []; localStorage.removeItem(CHAT_KEY); },
      getChat: function () { return history.slice(); },
      send: send,
      endpointOf: endpointOf,
      humanError: humanError,
      isBusy: function () { return busy; }
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
