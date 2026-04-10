/* ============================================================
   Claude Java Toolkit — Shared JavaScript
   ============================================================ */

// ── Theme management ─────────────────────────────────────────
// NOTE: data-theme is applied via an inline <script> in each page's <head>
// (before CSS loads) to prevent the dark→light flash on navigation.
// This IIFE only synchronises the toggle button label/icon on DOMContentLoaded.
// ── Accent color (applied before DOMContentLoaded) ─────────────
(function applyAccentColor() {
    var ac = localStorage.getItem('toolkit_accent_v1');
    if (ac) {
        document.documentElement.style.setProperty('--accent', ac);
        document.documentElement.style.setProperty('--accent-hover', ac);
    }
})();

// ── Auto-inject logout button (즉시 실행) ──
(function _injectLogoutBtn() {
    if (window.location.pathname === '/login' || window.location.pathname === '/setup') return;
    var themeToggle = document.getElementById('themeToggle');
    if (!themeToggle || !themeToggle.parentNode || document.getElementById('logoutBtn')) return;

    // themeToggle + logoutBtn을 wrapper div로 감싸서 top-bar 우측에 밀착 배치
    var wrapper = document.createElement('div');
    wrapper.style.cssText = 'display:flex;align-items:center;gap:6px;';
    themeToggle.parentNode.insertBefore(wrapper, themeToggle);
    wrapper.appendChild(themeToggle);

    // 세션 타이머 (60분 = 3600초)
    var SESSION_SECONDS = 3600;
    var _sessionRemain = SESSION_SECONDS;

    var timerSpan = document.createElement('span');
    timerSpan.id = 'sessionTimer';
    timerSpan.style.cssText = 'font-size:.7rem;color:var(--text-muted);font-family:monospace;min-width:38px;text-align:center;';
    timerSpan.textContent = '60:00';
    wrapper.appendChild(timerSpan);

    var refreshBtn = document.createElement('button');
    refreshBtn.title = '세션 갱신';
    refreshBtn.style.cssText = 'background:none;border:none;color:var(--text-muted);cursor:pointer;font-size:.7rem;padding:2px;transition:color .15s;';
    refreshBtn.innerHTML = '<i class="fas fa-sync-alt"></i>';
    refreshBtn.onmouseenter = function(){ this.style.color='#10b981'; };
    refreshBtn.onmouseleave = function(){ this.style.color='var(--text-muted)'; };
    refreshBtn.onclick = function() {
        // 세션 갱신: 서버에 빈 요청 → lastAccessedTime 갱신
        fetch('/actuator/health', {credentials:'same-origin'}).catch(function(){});
        _sessionRemain = SESSION_SECONDS;
        timerSpan.style.color = 'var(--text-muted)';
    };
    wrapper.appendChild(refreshBtn);

    // 알림 벨
    var bellWrap = document.createElement('div');
    bellWrap.style.cssText = 'position:relative;display:inline-flex;';
    var bellBtn = document.createElement('button');
    bellBtn.id = 'notiBell';
    bellBtn.title = '알림';
    bellBtn.style.cssText = 'background:none;border:1px solid var(--border-color);color:var(--text-muted);padding:4px 8px;border-radius:6px;cursor:pointer;font-size:.82rem;transition:all .15s;';
    bellBtn.innerHTML = '<i class="fas fa-bell"></i>';
    bellBtn.onmouseenter = function(){ this.style.borderColor='var(--accent)'; this.style.color='var(--accent)'; };
    bellBtn.onmouseleave = function(){ this.style.borderColor='var(--border-color)'; this.style.color='var(--text-muted)'; };
    bellBtn.onclick = function(e) { e.stopPropagation(); _toggleNotiDropdown(); };
    var bellBadge = document.createElement('span');
    bellBadge.id = 'notiBadge';
    bellBadge.style.cssText = 'display:none;position:absolute;top:-4px;right:-4px;background:#ef4444;color:#fff;font-size:.6rem;min-width:16px;height:16px;border-radius:8px;text-align:center;line-height:16px;font-weight:700;';
    bellWrap.appendChild(bellBtn);
    bellWrap.appendChild(bellBadge);
    // 드롭다운
    var notiDrop = document.createElement('div');
    notiDrop.id = 'notiDropdown';
    notiDrop.style.cssText = 'display:none;position:absolute;top:36px;right:0;width:340px;max-height:400px;overflow-y:auto;background:var(--bg-secondary);border:1px solid var(--border-color);border-radius:10px;box-shadow:0 8px 24px rgba(0,0,0,.25);z-index:9999;padding:8px 0;';
    bellWrap.appendChild(notiDrop);
    wrapper.appendChild(bellWrap);

    // v2.8.0: SSE 실시간 알림 (폴링 fallback 포함)
    _pollNotifications();  // 초기 카운트 로드
    _startNotificationStream();

    // 문서 클릭 시 드롭다운 닫기
    document.addEventListener('click', function() { notiDrop.style.display = 'none'; });

    // 로그아웃 버튼
    var btn = document.createElement('a');
    btn.id = 'logoutBtn';
    btn.href = '/logout';
    btn.title = '로그아웃';
    btn.style.cssText = 'background:none;border:1px solid var(--border-color);color:var(--text-muted);'
        + 'padding:4px 10px;border-radius:6px;cursor:pointer;font-size:.75rem;text-decoration:none;'
        + 'display:inline-flex;align-items:center;gap:4px;transition:all .15s;';
    btn.innerHTML = '<i class="fas fa-sign-out-alt"></i><span>로그아웃</span>';
    btn.onmouseenter = function(){ this.style.borderColor='#ef4444'; this.style.color='#ef4444'; };
    btn.onmouseleave = function(){ this.style.borderColor='var(--border-color)'; this.style.color='var(--text-muted)'; };
    wrapper.appendChild(btn);

    // 매초 카운트다운
    setInterval(function() {
        _sessionRemain--;
        if (_sessionRemain < 0) _sessionRemain = 0;
        var m = Math.floor(_sessionRemain / 60);
        var s = _sessionRemain % 60;
        timerSpan.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
        // 5분 이하: 노랑, 1분 이하: 빨강
        if (_sessionRemain <= 60) timerSpan.style.color = '#ef4444';
        else if (_sessionRemain <= 300) timerSpan.style.color = '#f59e0b';
        else timerSpan.style.color = 'var(--text-muted)';
        // 0초: 세션 만료
        if (_sessionRemain <= 0) {
            window.location.href = '/login?expired=true';
        }
    }, 1000);

    // 사용자 활동 시 타이머 리셋 (페이지 클릭, 키 입력)
    document.addEventListener('click', function() { _sessionRemain = SESSION_SECONDS; });
    document.addEventListener('keydown', function() { _sessionRemain = SESSION_SECONDS; });
})();

// ── 세션 만료 감지 (AJAX 응답이 로그인 페이지 리다이렉트되면 감지) ──
(function _interceptFetchForSession() {
    if (window.location.pathname === '/login' || window.location.pathname === '/setup') return;
    var origFetch = window.fetch;
    window.fetch = function() {
        return origFetch.apply(this, arguments).then(function(response) {
            // 로그인 페이지로 리다이렉트된 경우 (세션 만료)
            if (response.redirected && response.url && response.url.indexOf('/login') >= 0) {
                if (typeof showToast === 'function') {
                    showToast('\uc138\uc158\uc774 \ub9cc\ub8cc\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \ub2e4\uc2dc \ub85c\uadf8\uc778\ud569\ub2c8\ub2e4.', 'warning', 2000);
                }
                setTimeout(function() { window.location.href = '/login?expired=true'; }, 1500);
            }
            return response;
        });
    };
})();

document.addEventListener('DOMContentLoaded', function () {
    var saved = localStorage.getItem('theme') || 'dark';
    _syncThemeUI(saved);
    requestAnimationFrame(function () {
        requestAnimationFrame(function () {
            document.documentElement.classList.add('theme-ready');
        });
    });

    // ── Auto-inject print button when result is visible ──
    var resultEl = document.querySelector('.result-area, .result-box, #resultMd, .result-panel');
    if (resultEl) {
        var printBtn = document.createElement('button');
        printBtn.className = 'btn-print-float';
        printBtn.innerHTML = '<i class="fas fa-print"></i>';
        printBtn.title = '인쇄 / PDF 저장 (Ctrl+P)';
        printBtn.onclick = function() { window.print(); };
        document.body.appendChild(printBtn);
    }
});

function _syncThemeUI(theme) {
    var isLight = theme === 'light';
    var label = document.getElementById('themeLabel');
    var icon  = document.querySelector('#themeToggle i');
    if (label) label.textContent = isLight ? 'Dark' : 'Light';
    if (icon)  icon.className    = isLight ? 'fas fa-moon me-1' : 'fas fa-sun me-1';
}

function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme === 'light' ? 'light' : '');
    _syncThemeUI(theme);
    localStorage.setItem('theme', theme);
}

function toggleTheme() {
    applyTheme(localStorage.getItem('theme') === 'light' ? 'dark' : 'light');
}

// ── History localStorage persistence ────────────────────────
var HISTORY_LS_KEY = 'toolkit_history_v1';
var HISTORY_LS_MAX = 100;

/**
 * Save a history entry to localStorage (called by result pages).
 * @param {string} type  - e.g. 'SQL_REVIEW'
 * @param {string} title - short title/preview of input
 * @param {string} input - full input content
 * @param {string} output - full output content
 */
function saveHistoryLocal(type, title, input, output) {
    try {
        var history = loadHistoryLocal();
        var entry = {
            id:     Date.now(),
            type:   type,
            title:  (title || '').substring(0, 80),
            input:  input  || '',
            output: output || '',
            date:   new Date().toLocaleString('ko-KR')
        };
        history.unshift(entry);
        if (history.length > HISTORY_LS_MAX) history = history.slice(0, HISTORY_LS_MAX);
        localStorage.setItem(HISTORY_LS_KEY, JSON.stringify(history));
    } catch (e) { /* quota exceeded or private browsing */ }
}

function loadHistoryLocal() {
    try {
        var raw = localStorage.getItem(HISTORY_LS_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch (e) { return []; }
}

function clearHistoryLocal() {
    localStorage.removeItem(HISTORY_LS_KEY);
}

function deleteHistoryLocalById(id) {
    try {
        var h = loadHistoryLocal().filter(function(e) { return e.id !== id; });
        localStorage.setItem(HISTORY_LS_KEY, JSON.stringify(h));
    } catch (e) {}
}

// ── Loading spinner with step messages ────────────────────────
var _spinnerInterval = null;

function showSpinner(steps) {
    var steps2 = steps || ['처리 중...'];
    var overlay = document.getElementById('spinner');
    var msgEl   = document.getElementById('spinnerMsg');
    if (overlay) overlay.style.display = 'flex';
    if (msgEl) {
        msgEl.textContent = steps2[0];
        if (steps2.length > 1) {
            var idx = 0;
            _spinnerInterval = setInterval(function () {
                idx = (idx + 1) % steps2.length;
                msgEl.textContent = steps2[idx];
            }, 2400);
        }
    }
}

function hideSpinner() {
    if (_spinnerInterval) { clearInterval(_spinnerInterval); _spinnerInterval = null; }
    var overlay = document.getElementById('spinner');
    if (overlay) overlay.style.display = 'none';
}

// ── Clipboard copy ────────────────────────────────────────────
function copyToClipboard(elementId) {
    var el = document.getElementById(elementId);
    if (!el) return;
    var text = el.textContent || el.innerText || '';
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(function () { showCopyFeedback(); });
    } else {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity  = '0';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        try { document.execCommand('copy'); showCopyFeedback(); } catch (e) {}
        document.body.removeChild(ta);
    }
}

function showCopyFeedback() {
    var btn = document.getElementById('copyBtn');
    if (btn) {
        var orig = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-check me-1"></i>복사됨!';
        btn.style.color = '#22c55e';
        btn.style.borderColor = '#22c55e';
        setTimeout(function () {
            btn.innerHTML = orig;
            btn.style.color = '';
            btn.style.borderColor = '';
        }, 2000);
    }
}

// ── Markdown rendering via marked.js ─────────────────────────
function renderMarkdown(rawText, targetId) {
    var el = document.getElementById(targetId);
    if (!el || !rawText) return;
    if (typeof marked === 'undefined') return;
    marked.setOptions({ breaks: true, gfm: true });
    el.innerHTML = marked.parse(rawText);
}

// ── Severity filtering (SQL Advisor) ─────────────────────────
var _currentSevFilter = 'ALL';

function initSeverityFilter(containerId) {
    var container = document.getElementById(containerId);
    if (!container) return;
    var lis = container.querySelectorAll('li');
    lis.forEach(function (li) {
        var txt = li.textContent || '';
        if (/\[SEVERITY:\s*HIGH\]|\[HIGH\]/i.test(txt)) {
            li.setAttribute('data-sev', 'HIGH');
        } else if (/\[SEVERITY:\s*MEDIUM\]|\[MEDIUM\]/i.test(txt)) {
            li.setAttribute('data-sev', 'MEDIUM');
        } else if (/\[SEVERITY:\s*LOW\]|\[LOW\]/i.test(txt)) {
            li.setAttribute('data-sev', 'LOW');
        }
    });
}

function filterSeverity(level) {
    _currentSevFilter = level;
    var lis = document.querySelectorAll('#resultMd li[data-sev]');
    lis.forEach(function (li) {
        li.style.display = (level === 'ALL' || li.getAttribute('data-sev') === level) ? '' : 'none';
    });
    // Update button states
    document.querySelectorAll('.sev-filter-btn').forEach(function (btn) {
        btn.className = btn.className.replace(/\s*f-\w+/g, '');
    });
    var active = document.getElementById('sevBtn-' + level);
    if (active) active.className += ' f-' + level.toLowerCase();
}

// ── Toggle scan path input ────────────────────────────────────
function toggleScanPathInput(checked) {
    var el = document.getElementById('scanPathGroup');
    if (el) el.style.display = checked ? '' : 'none';
}

// ── Syntax highlighting via Prism.js ─────────────────────────
/**
 * Re-runs Prism highlighting on all <code> blocks inside a container.
 * Call this AFTER renderMarkdown() to highlight fenced code blocks.
 *
 * @param {string|Element} containerIdOrEl - element id string or DOM element
 */
function highlightCode(containerIdOrEl) {
    if (typeof Prism === 'undefined') return;
    var el = (typeof containerIdOrEl === 'string')
        ? document.getElementById(containerIdOrEl)
        : containerIdOrEl;
    if (el) Prism.highlightAllUnder(el);
}

// ── Open result in new browser tab ───────────────────────────
/**
 * Opens the text content of an element in a new browser tab
 * rendered as a simple Markdown → HTML page.
 *
 * @param {string} elementId - id of the element whose text to open
 */
function openInNewTab(elementId) {
    var el = document.getElementById(elementId);
    if (!el) return;
    var text = el.textContent || el.innerText || el.getAttribute('data-content') || '';
    var html = '<!DOCTYPE html><html><head><meta charset="UTF-8">' +
        '<style>body{font-family:sans-serif;max-width:900px;margin:40px auto;padding:0 20px;line-height:1.7;background:#0f172a;color:#e2e8f0;}' +
        'pre{background:#1e293b;padding:14px;border-radius:6px;overflow-x:auto;}' +
        'code{background:rgba(249,115,22,0.12);color:#f97316;padding:2px 5px;border-radius:3px;}' +
        'pre code{background:none;color:#cbd5e1;padding:0;}' +
        'h1,h2,h3{color:#f1f5f9;border-bottom:1px solid #334155;padding-bottom:6px;}' +
        'table{border-collapse:collapse;width:100%;}td,th{border:1px solid #334155;padding:8px;}' +
        'th{background:rgba(249,115,22,0.12);}</style>' +
        '<script src="https://cdnjs.cloudflare.com/ajax/libs/marked/9.1.6/marked.min.js"><\/script>' +
        '</head><body>' +
        '<div id="out"></div>' +
        '<pre id="src" style="display:none"></pre>' +
        '<script>document.getElementById("src").textContent=' + JSON.stringify(text) + ';' +
        'document.getElementById("out").innerHTML=marked.parse(document.getElementById("src").textContent);<\/script>' +
        '</body></html>';
    var blob = new Blob([html], {type: 'text/html'});
    var url  = URL.createObjectURL(blob);
    window.open(url, '_blank');
}

// ── Markdown export (Blob download) ─────────────────────────
function exportMarkdownBlob(text, filename) {
    if (!text) return;
    var blob = new Blob([text], {type: 'text/markdown; charset=UTF-8'});
    var url  = URL.createObjectURL(blob);
    var a    = document.createElement('a');
    a.href     = url;
    a.download = filename || 'result.md';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// ── Auto-draft (localStorage) ────────────────────────────────
var DRAFT_PREFIX = 'toolkit_draft_';

function saveDraft(pageKey, value) {
    try { localStorage.setItem(DRAFT_PREFIX + pageKey, value); } catch (e) {}
}
function loadDraft(pageKey) {
    try { return localStorage.getItem(DRAFT_PREFIX + pageKey) || ''; } catch (e) { return ''; }
}
function clearDraft(pageKey) {
    try { localStorage.removeItem(DRAFT_PREFIX + pageKey); } catch (e) {}
}

/**
 * Attach auto-save to a textarea (saves 2s after last keystroke).
 * Restores draft on page load if textarea is empty.
 * @param {string} textareaId - ID of <textarea>
 * @param {string} pageKey    - unique key for this page (e.g. 'advisor', 'docgen')
 */
function initAutoDraft(textareaId, pageKey) {
    var ta = document.getElementById(textareaId);
    if (!ta) return;
    var draft = loadDraft(pageKey);
    if (draft && !ta.value.trim()) {
        ta.value = draft;
        _showDraftNotice(textareaId, '\uc784\uc2dc\uc800\uc7a5 \ubcf5\uc6d0\ub428');
    }
    var timer = null;
    ta.addEventListener('input', function () {
        clearTimeout(timer);
        timer = setTimeout(function () {
            saveDraft(pageKey, ta.value);
            _showDraftNotice(textareaId, '\uc784\uc2dc\uc800\uc7a5\ub428');
        }, 2000);
    });
}

function _showDraftNotice(textareaId, msg) {
    var id = 'draft_notice_' + textareaId;
    var old = document.getElementById(id);
    if (old) old.remove();
    var span = document.createElement('span');
    span.id = id;
    span.textContent = msg;
    span.style.cssText = 'font-size:0.72rem;color:var(--text-muted);margin-left:8px;transition:opacity 1.5s ease;opacity:1;';
    var ta = document.getElementById(textareaId);
    if (ta && ta.previousElementSibling) ta.previousElementSibling.appendChild(span);
    setTimeout(function () { span.style.opacity = '0'; }, 2500);
    setTimeout(function () { if (span.parentNode) span.parentNode.removeChild(span); }, 4200);
}

// ── Token count estimator ─────────────────────────────────────
/**
 * Rough estimate: ~3.5 chars per token (mix of English/Korean/code).
 * Attach to textarea for live display.
 * @param {string} textareaId - ID of <textarea>
 * @param {string} counterId  - ID of element to show count (e.g. <small id="tokenCount">)
 */
function initTokenCounter(textareaId, counterId) {
    var ta  = document.getElementById(textareaId);
    var el  = document.getElementById(counterId);
    if (!ta || !el) return;
    function update() {
        var n = Math.ceil((ta.value || '').length / 3.5);
        el.textContent = n.toLocaleString() + ' \ud1a0\ud070 \uc608\uc0c1';
        el.style.color = n > 80000 ? '#ef4444' : n > 40000 ? '#f59e0b' : 'var(--text-muted)';
    }
    ta.addEventListener('input', update);
    update();
}

// ── Form double-submit guard ──────────────────────────────────
/**
 * Call in a form's onsubmit handler to disable the submit button.
 * Automatically re-enables after 90 s as a safety fallback.
 * @param {string} buttonId - ID of the submit <button>
 */
function guardSubmit(buttonId) {
    var btn = document.getElementById(buttonId);
    if (!btn) return;
    btn.disabled = true;
    btn.style.opacity = '0.6';
    setTimeout(function () { btn.disabled = false; btn.style.opacity = ''; }, 90000);
}

// ── SSE streaming helper ──────────────────────────────────────
/**
 * POST input to /stream/init, then open EventSource for streaming response.
 * @param {string} feature    - feature key (e.g. 'sql_review', 'doc_gen')
 * @param {object} params     - { input, input2, sourceType } etc.
 * @param {string} targetId   - ID of element to write text into
 * @param {function} onDone   - called with final accumulated text when stream ends
 */
function startStream(feature, params, targetId, onDone) {
    var target = document.getElementById(targetId);
    if (!target) return;
    target.textContent = '';
    target.style.display = '';

    var body = 'feature=' + encodeURIComponent(feature);
    body += '&input=' + encodeURIComponent(params.input || '');
    body += '&input2=' + encodeURIComponent(params.input2 || '');
    body += '&sourceType=' + encodeURIComponent(params.sourceType || '');

    fetch('/stream/init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body
    })
    .then(function (r) { return r.text(); })
    .then(function (streamId) {
        var es = new EventSource('/stream/' + streamId);
        var accumulated = '';
        es.onmessage = function (e) {
            accumulated += e.data;
            target.textContent = accumulated;
        };
        es.addEventListener('done', function () {
            es.close();
            hideSpinner();
            if (onDone) onDone(accumulated);
        });
        es.addEventListener('error_msg', function (e) {
            es.close();
            hideSpinner();
            target.textContent = '\uc624\ub958: ' + (e.data || '\uc2a4\ud2b8\ub9ac\ubc0d \uc2e4\ud328');
        });
        es.onerror = function () { es.close(); hideSpinner(); };
    })
    .catch(function (e) { hideSpinner(); console.error('stream init failed', e); });
}

// ── updateTokenCount (standalone helper) ─────────────────────
/**
 * One-shot token count update (used in inline onchange/oninput handlers).
 * @param {string} textareaId - ID of textarea
 * @param {string} counterId  - ID of counter element
 */
function updateTokenCount(textareaId, counterId) {
    var ta = document.getElementById(textareaId);
    var el = document.getElementById(counterId);
    if (!ta || !el) return;
    var n = Math.ceil((ta.value || '').length / 3.5);
    el.textContent = n.toLocaleString() + ' 토큰 예상';
    el.style.color = n > 80000 ? '#ef4444' : n > 40000 ? '#f59e0b' : 'var(--text-muted)';
}

// ── Markdown-like result renderer ────────────────────────────
/**
 * Renders a plain-text markdown result in a container element.
 * Converts ## headings, **bold**, `code`, and blank lines into HTML.
 * @param {HTMLElement} el - container element whose textContent is raw markdown
 */
function renderMarkdownResult(el) {
    if (!el) return;
    var raw = el.textContent || el.innerText || '';
    if (!raw.trim()) return;

    var lines = raw.split('\n');
    var html  = '';
    var inCode = false;
    var codeLang = '';
    var codeBuf  = '';

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];

        /* Fenced code blocks */
        var fenceMatch = line.match(/^```(\w*)$/);
        if (fenceMatch) {
            if (!inCode) {
                inCode   = true;
                codeLang = fenceMatch[1] || 'plaintext';
                codeBuf  = '';
            } else {
                html += '<pre><code class="language-' + escHtml(codeLang) + '">' + escHtml(codeBuf) + '</code></pre>';
                inCode  = false;
                codeLang = '';
                codeBuf  = '';
            }
            continue;
        }
        if (inCode) { codeBuf += line + '\n'; continue; }

        /* Headings */
        if (/^### /.test(line)) { html += '<h6 class="mt-3 mb-1" style="color:var(--text-sub);">' + escHtml(line.slice(4)) + '</h6>'; continue; }
        if (/^## /.test(line))  { html += '<h5 class="mt-3 mb-1" style="color:var(--text-sub);">' + escHtml(line.slice(3)) + '</h5>'; continue; }
        if (/^# /.test(line))   { html += '<h4 class="mt-3 mb-1" style="color:var(--accent);">'   + escHtml(line.slice(2)) + '</h4>'; continue; }

        /* Horizontal rule */
        if (/^---+$/.test(line.trim())) { html += '<hr style="border-color:var(--border-color);margin:8px 0;">'; continue; }

        /* Bullet lists */
        if (/^[-*] /.test(line)) {
            html += '<li style="margin-bottom:2px;">' + inlineMarkdown(line.slice(2)) + '</li>';
            continue;
        }
        if (/^\d+\. /.test(line)) {
            html += '<li style="margin-bottom:2px;">' + inlineMarkdown(line.replace(/^\d+\.\s+/, '')) + '</li>';
            continue;
        }

        /* Blank line */
        if (!line.trim()) { html += '<br>'; continue; }

        /* Normal paragraph */
        html += '<p style="margin:2px 0;">' + inlineMarkdown(line) + '</p>';
    }

    /* Close open code block */
    if (inCode && codeBuf) {
        html += '<pre><code class="language-' + escHtml(codeLang) + '">' + escHtml(codeBuf) + '</code></pre>';
    }

    el.innerHTML = html;

    /* Re-highlight with Prism if available */
    if (typeof Prism !== 'undefined') {
        el.querySelectorAll('code[class*="language-"]').forEach(function(c) {
            Prism.highlightElement(c);
        });
    }
}

function escHtml(s) {
    return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function inlineMarkdown(s) {
    s = escHtml(s);
    /* **bold** */
    s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    /* *italic* */
    s = s.replace(/\*(.+?)\*/g, '<em>$1</em>');
    /* `code` */
    s = s.replace(/`([^`]+)`/g, '<code style="background:var(--input-bg);padding:1px 5px;border-radius:3px;font-size:0.9em;">$1</code>');
    /* [SEVERITY: HIGH/MEDIUM/LOW] */
    s = s.replace(/\[SEVERITY:\s*(HIGH)\]/gi,   '<span class="badge" style="background:#ef4444;font-size:0.7em;">HIGH</span>');
    s = s.replace(/\[SEVERITY:\s*(MEDIUM)\]/gi, '<span class="badge" style="background:#f59e0b;font-size:0.7em;">MEDIUM</span>');
    s = s.replace(/\[SEVERITY:\s*(LOW)\]/gi,    '<span class="badge" style="background:#22c55e;font-size:0.7em;">LOW</span>');
    return s;
}

// ── Spinner helpers ───────────────────────────────────────────
function hideSpinner() {
    var s = document.getElementById('spinner');
    if (!s) return;
    s.classList.remove('active');  // for pages that use classList.add('active')
    s.style.display = 'none';     // for pages that use style.display = 'flex'
}

// ── Mermaid ERD rendering ────────────────────────────────────
function renderMermaidBlocks() {
    if (typeof mermaid === 'undefined') return;
    var blocks = document.querySelectorAll('code.language-mermaid');
    blocks.forEach(function (block) {
        var div = document.createElement('div');
        div.className = 'mermaid mermaid-container';
        div.textContent = block.textContent;
        var pre = block.parentNode;
        if (pre && pre.parentNode) pre.parentNode.replaceChild(div, pre);
    });
    mermaid.run({ querySelector: '.mermaid' });
}

/* ── Ctrl+Enter: 폼 제출 단축키 ── */
document.addEventListener('keydown', function(e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        // Find the primary submit button (btn-accent) or first submit button
        var accentBtn = document.querySelector('button.btn-accent[type="submit"], .btn-accent[type="submit"]');
        if (accentBtn && !accentBtn.disabled) {
            accentBtn.click();
            return;
        }
        // Fallback: find any visible submit button
        var submitBtns = document.querySelectorAll('button[type="submit"]');
        for (var i = 0; i < submitBtns.length; i++) {
            var btn = submitBtns[i];
            if (btn.offsetParent !== null && !btn.disabled) {
                btn.click();
                return;
            }
        }
    }
});

/* ── v2.8.0: 키보드 단축키 가이드 (? 키로 모달 토글) ───────────── */
document.addEventListener('keydown', function(e) {
    // Only trigger on '?' when not in input field
    if (e.key !== '?') return;
    var active = document.activeElement;
    if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA'
                || active.isContentEditable)) return;
    if (e.ctrlKey || e.altKey || e.metaKey) return;
    e.preventDefault();
    toggleShortcutHelp();
});

function toggleShortcutHelp() {
    var existing = document.getElementById('shortcutHelpModal');
    if (existing) { existing.remove(); return; }

    var overlay = document.createElement('div');
    overlay.id = 'shortcutHelpModal';
    overlay.style.cssText = 'position:fixed;inset:0;z-index:99999;background:rgba(0,0,0,.55);'
        + 'backdrop-filter:blur(3px);display:flex;align-items:center;justify-content:center;';
    overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };

    var panel = document.createElement('div');
    panel.style.cssText = 'width:min(560px,92vw);max-height:80vh;overflow-y:auto;'
        + 'background:var(--bg-primary);border:1px solid var(--border-color);'
        + 'border-radius:12px;box-shadow:0 10px 40px rgba(0,0,0,.5);'
        + 'padding:24px 28px;color:var(--text-primary);';

    var shortcuts = [
        { keys: ['?'],               desc: '이 단축키 가이드 표시/숨기기' },
        { keys: ['Esc'],             desc: '모달 닫기' },
        { keys: ['Ctrl','Enter'],    desc: '현재 페이지 기본 버튼 실행 (분석/저장)' },
        { keys: ['⌘','Enter'],       desc: '현재 페이지 기본 버튼 실행 (Mac)' },
        { keys: ['/'],               desc: '글로벌 검색 포커스 (검색 페이지에서)' },
        { keys: ['Shift','Enter'],   desc: 'AI 채팅 입력 줄바꿈' },
        { keys: ['Enter'],           desc: 'AI 채팅 메시지 전송' }
    ];

    var listHtml = '';
    for (var i = 0; i < shortcuts.length; i++) {
        var s = shortcuts[i];
        var keysHtml = '';
        for (var k = 0; k < s.keys.length; k++) {
            if (k > 0) keysHtml += ' <span style="color:var(--text-muted);margin:0 2px;">+</span> ';
            keysHtml += '<kbd style="background:var(--bg-tertiary);border:1px solid var(--border-color);'
                     + 'border-radius:5px;padding:3px 9px;font-family:monospace;font-size:.82rem;'
                     + 'color:var(--text-primary);box-shadow:0 1px 0 var(--border-color);">'
                     + s.keys[k] + '</kbd>';
        }
        listHtml += '<div style="display:flex;justify-content:space-between;align-items:center;'
                  + 'padding:10px 0;border-bottom:1px solid var(--border-color);">'
                  + '<div>' + keysHtml + '</div>'
                  + '<div style="color:var(--text-sub);font-size:.86rem;">' + s.desc + '</div>'
                  + '</div>';
    }

    panel.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">'
        + '<h5 style="margin:0;font-weight:700;font-size:1.05rem;">'
        + '<i class="fas fa-keyboard me-2" style="color:var(--accent);"></i>키보드 단축키</h5>'
        + '<button id="closeShortcutHelp" style="background:none;border:none;color:var(--text-muted);'
        + 'font-size:1.2rem;cursor:pointer;">&times;</button></div>'
        + '<div>' + listHtml + '</div>'
        + '<div style="margin-top:14px;padding-top:12px;border-top:1px solid var(--border-color);'
        + 'font-size:.76rem;color:var(--text-muted);text-align:center;">'
        + '<kbd style="background:var(--bg-tertiary);border:1px solid var(--border-color);'
        + 'border-radius:4px;padding:2px 6px;font-family:monospace;">Esc</kbd> 또는 바깥 클릭으로 닫기</div>';

    overlay.appendChild(panel);
    document.body.appendChild(overlay);

    document.getElementById('closeShortcutHelp').onclick = function() { overlay.remove(); };

    // Esc 키로 닫기
    var escHandler = function(e) {
        if (e.key === 'Escape') {
            overlay.remove();
            document.removeEventListener('keydown', escHandler);
        }
    };
    document.addEventListener('keydown', escHandler);
}

// ── Print / PDF export ────────────────────────────────────────
function printPage() {
    window.print();
}

// ── Toast notification system (D1) ──────────────────────────
var _toastContainer = null;
var _toastCount = 0;

/**
 * Show a toast notification.
 * @param {string} message - 표시할 메시지
 * @param {string} type    - 'success' | 'error' | 'warning' | 'info' (default: 'info')
 * @param {number} duration - 자동 소멸 시간(ms), 기본 3000
 */
function showToast(message, type, duration) {
    type = type || 'info';
    duration = duration || 3000;

    if (!_toastContainer) {
        _toastContainer = document.createElement('div');
        _toastContainer.className = 'toast-container';
        document.body.appendChild(_toastContainer);
    }

    var icons = { success: 'fa-check-circle', error: 'fa-times-circle', warning: 'fa-exclamation-triangle', info: 'fa-info-circle' };
    var item = document.createElement('div');
    item.className = 'toast-item toast-' + type;
    item.innerHTML = '<i class="fas ' + (icons[type] || icons.info) + '"></i>' +
        '<span>' + escHtml(message) + '</span>' +
        '<button class="toast-close" onclick="this.parentNode.remove()">&times;</button>' +
        '<div class="toast-progress" style="animation-duration:' + duration + 'ms;"></div>';

    _toastContainer.appendChild(item);
    _toastCount++;

    // 최대 5개 유지
    while (_toastContainer.children.length > 5) {
        _toastContainer.removeChild(_toastContainer.firstChild);
    }

    setTimeout(function() {
        if (item.parentNode) {
            item.style.opacity = '0';
            item.style.transform = 'translateX(100%)';
            item.style.transition = 'all 0.3s ease';
            setTimeout(function() { if (item.parentNode) item.parentNode.removeChild(item); }, 300);
        }
    }, duration);
}

// ── Breadcrumb navigation (D2) ──────────────────────────────
(function initBreadcrumb() {
    if (window.location.pathname === '/login' || window.location.pathname === '/setup'
        || window.location.pathname === '/login/2fa') return;

    var pathMap = {
        '': '\ud648', 'advisor': 'SQL \ub9ac\ubdf0', 'sql-translate': 'SQL DB \ubc88\uc5ed',
        'sql-batch': '\ubc30\uce58 SQL', 'erd': 'ERD \ubd84\uc11d', 'complexity': '\ubcf5\uc7a1\ub3c4',
        'explain': '\uc2e4\ud589\uacc4\ud68d', 'compare': '\ube44\uad50', 'dashboard': '\ub300\uc2dc\ubcf4\ub4dc',
        'harness': '\ud558\ub124\uc2a4', 'batch': '\ubc30\uce58', 'dependency': '\uc758\uc874\uc131',
        'codereview': '\ucf54\ub4dc \ub9ac\ubdf0', 'docgen': '\uae30\uc220 \ubb38\uc11c', 'apispec': 'API \uba85\uc138',
        'converter': '\ucf54\ub4dc \ubcc0\ud658', 'mockdata': 'Mock \ub370\uc774\ud130', 'migration': 'DB \ub9c8\uc774\uadf8\ub808\uc774\uc158',
        'depcheck': '\uc758\uc874\uc131 \ubd84\uc11d', 'migrate': 'Spring \ub9c8\uc774\uadf8\ub808\uc774\uc158',
        'testgen': '\ud14c\uc2a4\ud2b8 \uc0dd\uc131', 'javadoc': 'JavaDoc', 'refactor': '\ub9ac\ud329\ud1a0\ub9c1',
        'workspace': '\ud1b5\ud569 \uc6cc\ud06c\uc2a4\ud398\uc774\uc2a4', 'history': '\ub9ac\ubdf0 \uc774\ub825',
        'favorites': '\uc990\uaca8\ucc3e\uae30', 'usage': '\uc0ac\uc6a9\ub7c9', 'roi-report': 'ROI \ub9ac\ud3ec\ud2b8',
        'schedule': '\uc2a4\ucf00\uc904\ub9c1', 'search': '\uac80\uc0c9',
        'loganalyzer': '\ub85c\uadf8 \ubd84\uc11d', 'regex': '\uc815\uaddc\uc2dd', 'commitmsg': '\ucee4\ubc0b \uba54\uc2dc\uc9c0',
        'maskgen': '\ub9c8\uc2a4\ud0b9 \uc2a4\ud06c\ub9bd\ud2b8', 'input-masking': '\ubbfc\uac10\uc815\ubcf4 \ub9c8\uc2a4\ud0b9',
        'github-pr': 'GitHub PR', 'git-diff': 'Git Diff',
        'admin': '\uad00\ub9ac', 'users': '\uc0ac\uc6a9\uc790', 'permissions': '\uad8c\ud55c',
        'backup': '\ubc31\uc5c5', 'team-dashboard': '\ud300 \ub300\uc2dc\ubcf4\ub4dc', 'audit-dashboard': '\uac10\uc0ac \ub85c\uadf8',
        'settings': '\uc124\uc815', 'security': '\ubcf4\uc548', 'prompts': '\ud504\ub86c\ud504\ud2b8',
        'shared': '\uacf5\uc720 \uc124\uc815', 'account': '\uacc4\uc815', 'password': '\ube44\ubc00\ubc88\ud638',
        'chat': 'AI \ucc44\ud305', 'api-docs': 'API Playground', 'db-profiles': 'DB \ud504\ub85c\ud544'
    };

    var parts = window.location.pathname.replace(/^\//, '').replace(/\/$/, '').split('/');
    if (parts.length <= 1 && parts[0] === '') return; // home page

    var topBar = document.querySelector('.top-bar');
    if (!topBar) return;

    var bc = document.createElement('div');
    bc.className = 'breadcrumb-bar';
    bc.innerHTML = '<a href="/"><i class="fas fa-home"></i></a>';

    var href = '';
    for (var i = 0; i < parts.length; i++) {
        href += '/' + parts[i];
        var label = pathMap[parts[i]] || parts[i];
        bc.innerHTML += '<span class="bc-sep">/</span>';
        if (i === parts.length - 1) {
            bc.innerHTML += '<span class="bc-current">' + escHtml(label) + '</span>';
        } else {
            bc.innerHTML += '<a href="' + href + '">' + escHtml(label) + '</a>';
        }
    }

    var title = topBar.querySelector('.top-bar-title');
    if (title) title.parentNode.insertBefore(bc, title.nextSibling);
})();

// ── Form validation utility (D3) ────────────────────────────
/**
 * @param {string} formId - 폼 또는 컨테이너 ID
 * @param {object} rules  - { inputId: { required: true, minLength: 8, pattern: /regex/, message: '...' } }
 * @returns {boolean} 유효하면 true
 */
function validateForm(formId, rules) {
    var form = document.getElementById(formId);
    if (!form) return true;
    var valid = true;
    for (var inputId in rules) {
        if (!rules.hasOwnProperty(inputId)) continue;
        var rule = rules[inputId];
        var input = document.getElementById(inputId);
        if (!input) continue;
        var val = input.value.trim();
        var err = null;

        if (rule.required && !val) err = rule.message || '\ud544\uc218 \ud56d\ubaa9\uc785\ub2c8\ub2e4.';
        else if (rule.minLength && val.length < rule.minLength) err = rule.message || '\ucd5c\uc18c ' + rule.minLength + '\uc790 \uc774\uc0c1 \uc785\ub825\ud558\uc138\uc694.';
        else if (rule.pattern && !rule.pattern.test(val)) err = rule.message || '\ud615\uc2dd\uc774 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4.';

        // 기존 feedback 제거
        var fb = input.parentNode.querySelector('.invalid-feedback');
        if (err) {
            input.classList.add('is-invalid');
            if (!fb) {
                fb = document.createElement('div');
                fb.className = 'invalid-feedback';
                input.parentNode.appendChild(fb);
            }
            fb.textContent = err;
            fb.style.display = 'block';
            valid = false;
        } else {
            input.classList.remove('is-invalid');
            if (fb) fb.style.display = 'none';
        }
    }
    return valid;
}

// 입력 시 자동으로 invalid 상태 해제
document.addEventListener('input', function(e) {
    if (e.target && e.target.classList && e.target.classList.contains('is-invalid')) {
        e.target.classList.remove('is-invalid');
        var fb = e.target.parentNode ? e.target.parentNode.querySelector('.invalid-feedback') : null;
        if (fb) fb.style.display = 'none';
    }
});

// ── Copy all results (D8) ───────────────────────────────────
/**
 * 결과 영역 전체 텍스트를 클립보드에 복사합니다.
 * @param {string} elementId - 복사할 요소 ID (없으면 .result-box 또는 #resultMd)
 */
function copyAllResults(elementId) {
    var el = elementId ? document.getElementById(elementId) : (document.querySelector('.result-box') || document.getElementById('resultMd'));
    if (!el) return;
    var text = el.textContent || el.innerText || '';
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(function() { showToast('\uc804\uccb4 \uacb0\uacfc\uac00 \ud074\ub9bd\ubcf4\ub4dc\uc5d0 \ubcf5\uc0ac\ub418\uc5c8\uc2b5\ub2c8\ub2e4.', 'success'); });
    } else {
        var ta = document.createElement('textarea');
        ta.value = text; ta.style.position='fixed'; ta.style.opacity='0';
        document.body.appendChild(ta); ta.focus(); ta.select();
        try { document.execCommand('copy'); showToast('\uc804\uccb4 \uacb0\uacfc\uac00 \ubcf5\uc0ac\ub418\uc5c8\uc2b5\ub2c8\ub2e4.', 'success'); } catch(e) {}
        document.body.removeChild(ta);
    }
}

// ── Skeleton loading UI (D10) ───────────────────────────────
/**
 * 타겟 요소에 스켈레톤 로딩 UI를 표시합니다.
 * @param {string} targetId - 스켈레톤을 표시할 요소 ID
 * @param {number} lines    - 스켈레톤 라인 수 (기본 5)
 */
function showSkeleton(targetId, lines) {
    var el = document.getElementById(targetId);
    if (!el) return;
    lines = lines || 5;
    var html = '';
    for (var i = 0; i < lines; i++) {
        var w = (i === lines - 1) ? '60%' : (70 + Math.floor(Math.random() * 30)) + '%';
        html += '<div class="skeleton skeleton-line" style="width:' + w + ';"></div>';
    }
    html += '<div class="skeleton skeleton-block"></div>';
    el.innerHTML = html;
    el.setAttribute('data-skeleton', 'true');
}

function hideSkeleton(targetId) {
    var el = document.getElementById(targetId);
    if (!el) return;
    if (el.getAttribute('data-skeleton') === 'true') {
        el.innerHTML = '';
        el.removeAttribute('data-skeleton');
    }
}

// ── Sidebar search (D11) ────────────────────────────────────
(function initSidebarSearch() {
    var nav = document.querySelector('.sidebar-nav');
    if (!nav) return;
    var brand = document.querySelector('.sidebar-brand');
    if (!brand) return;

    var searchDiv = document.createElement('div');
    searchDiv.className = 'sidebar-search';
    searchDiv.style.position = 'relative';
    searchDiv.innerHTML = '<i class="fas fa-search search-icon"></i>' +
        '<input type="text" id="sidebarSearchInput" placeholder="\uae30\ub2a5 \uac80\uc0c9..." autocomplete="off">';
    brand.parentNode.insertBefore(searchDiv, brand.nextSibling);

    var input = document.getElementById('sidebarSearchInput');
    if (!input) return;

    input.addEventListener('keyup', function() {
        var q = this.value.trim().toLowerCase();
        var items = nav.querySelectorAll('.sidebar-item');
        var toggles = nav.querySelectorAll('.sidebar-section-toggle');

        if (!q) {
            // 검색어 비우면 원래 상태 복원
            items.forEach(function(it) { it.style.display = ''; });
            toggles.forEach(function(t) {
                var group = t.nextElementSibling;
                if (group && group.classList.contains('sidebar-group-items')) {
                    // 저장된 상태 복원
                    t.style.display = '';
                }
            });
            return;
        }

        // 모든 항목 필터링
        var visibleGroups = {};
        items.forEach(function(it) {
            var text = (it.textContent || '').toLowerCase();
            var match = text.indexOf(q) >= 0;
            it.style.display = match ? '' : 'none';
            if (match) {
                // 부모 그룹 펼치기
                var group = it.closest('.sidebar-group-items');
                if (group) {
                    group.style.maxHeight = 'none';
                    var toggle = group.previousElementSibling;
                    if (toggle) {
                        visibleGroups[toggle.textContent] = true;
                        var chevron = toggle.querySelector('.sidebar-chevron');
                        if (chevron) chevron.style.transform = 'rotate(0deg)';
                    }
                }
            }
        });

        // 빈 섹션 토글 숨기기
        toggles.forEach(function(t) {
            var group = t.nextElementSibling;
            if (!group) return;
            var hasVisible = false;
            var gItems = group.querySelectorAll('.sidebar-item');
            gItems.forEach(function(gi) { if (gi.style.display !== 'none') hasVisible = true; });
            t.style.display = hasVisible ? '' : 'none';
            if (!hasVisible) group.style.maxHeight = '0';
        });
    });
})();

// ── Notification bell helpers (C12) ──────────────────────────
function _pollNotifications() {
    fetch('/notifications/unread-count', {credentials:'same-origin'})
        .then(function(r) { return r.json(); })
        .then(function(d) {
            var badge = document.getElementById('notiBadge');
            if (!badge) return;
            if (d.count > 0) {
                badge.textContent = d.count > 99 ? '99+' : d.count;
                badge.style.display = '';
            } else {
                badge.style.display = 'none';
            }
        }).catch(function(){});
}

/* ── v2.8.0: SSE 실시간 알림 스트림 (폴링 fallback 포함) ─────────── */
var _notiEventSource = null;
var _notiFallbackTimer = null;

function _startNotificationStream() {
    try {
        if (_notiEventSource) {
            try { _notiEventSource.close(); } catch (e) {}
        }
        _notiEventSource = new EventSource('/notifications/stream');

        _notiEventSource.addEventListener('notification', function(e) {
            // 새 알림 도착 → 배지 즉시 증가
            var badge = document.getElementById('notiBadge');
            if (badge) {
                var current = parseInt(badge.textContent, 10) || 0;
                if (badge.textContent === '99+') current = 99;
                var next = current + 1;
                badge.textContent = next > 99 ? '99+' : next;
                badge.style.display = '';
            }
            // 토스트로도 간단 알림 표시
            try {
                var data = JSON.parse(e.data);
                if (typeof showToast === 'function' && data.title) {
                    showToast('🔔 ' + data.title, 'info', 4000);
                }
            } catch (err) {}
        });

        _notiEventSource.addEventListener('connected', function() {
            // 연결 성공 → 폴링 fallback 중단
            if (_notiFallbackTimer) {
                clearInterval(_notiFallbackTimer);
                _notiFallbackTimer = null;
            }
        });

        _notiEventSource.onerror = function() {
            // 연결 실패/끊김 → 60초 폴링 fallback 활성화
            try { _notiEventSource.close(); } catch (e) {}
            _notiEventSource = null;
            if (!_notiFallbackTimer) {
                _notiFallbackTimer = setInterval(_pollNotifications, 60000);
            }
            // 30초 후 SSE 재연결 시도
            setTimeout(_startNotificationStream, 30000);
        };
    } catch (e) {
        // EventSource 미지원 → 폴링 fallback
        if (!_notiFallbackTimer) {
            _notiFallbackTimer = setInterval(_pollNotifications, 60000);
        }
    }
}

function _toggleNotiDropdown() {
    var drop = document.getElementById('notiDropdown');
    if (!drop) return;
    if (drop.style.display === 'none' || !drop.style.display) {
        drop.style.display = '';
        drop.innerHTML = '<div style="padding:12px;text-align:center;color:var(--text-muted);font-size:.82rem;"><i class="fas fa-spinner fa-spin me-1"></i>로딩 중...</div>';
        fetch('/notifications', {credentials:'same-origin'})
            .then(function(r) { return r.json(); })
            .then(function(list) {
                if (!list || list.length === 0) {
                    drop.innerHTML = '<div style="padding:20px;text-align:center;color:var(--text-muted);font-size:.82rem;"><i class="fas fa-bell-slash me-1"></i>알림이 없습니다</div>';
                    return;
                }
                var html = '<div style="padding:6px 14px;display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid var(--border-color);">' +
                    '<span style="font-size:.82rem;font-weight:600;color:var(--text-primary);">알림</span>' +
                    '<button onclick="_markAllRead()" style="background:none;border:none;color:var(--accent);cursor:pointer;font-size:.75rem;">모두 읽음</button></div>';
                for (var i = 0; i < list.length; i++) {
                    var n = list[i];
                    var bg = n.isRead ? '' : 'background:var(--accent-subtle);';
                    html += '<a href="' + escHtml(n.link || '#') + '" onclick="_markNotiRead(' + n.id + ')" ' +
                        'style="display:block;padding:8px 14px;text-decoration:none;border-bottom:1px solid var(--border-color);' + bg + '">' +
                        '<div style="display:flex;gap:8px;align-items:flex-start;">' +
                        '<i class="fas ' + escHtml(n.typeIcon) + '" style="color:var(--accent);margin-top:2px;font-size:.78rem;"></i>' +
                        '<div style="flex:1;min-width:0;">' +
                        '<div style="font-size:.8rem;color:var(--text-primary);font-weight:' + (n.isRead ? '400' : '600') + ';">' + escHtml(n.title) + '</div>' +
                        '<div style="font-size:.72rem;color:var(--text-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">' + escHtml(n.message || '') + '</div>' +
                        '<div style="font-size:.68rem;color:var(--text-muted);margin-top:2px;">' + escHtml(n.createdAt) + '</div>' +
                        '</div></div></a>';
                }
                drop.innerHTML = html;
            }).catch(function() {
                drop.innerHTML = '<div style="padding:12px;text-align:center;color:#ef4444;font-size:.82rem;">로드 실패</div>';
            });
    } else {
        drop.style.display = 'none';
    }
}

function _markNotiRead(id) {
    fetch('/notifications/' + id + '/read', {method:'POST', credentials:'same-origin'}).catch(function(){});
}

function _markAllRead() {
    fetch('/notifications/read-all', {method:'POST', credentials:'same-origin'})
        .then(function() {
            var badge = document.getElementById('notiBadge');
            if (badge) badge.style.display = 'none';
            _toggleNotiDropdown(); // close and reopen to refresh
            setTimeout(_toggleNotiDropdown, 100);
        }).catch(function(){});
}

// ── CodeMirror editor (C9) ───────────────────────────────────
var _cmInstances = {};
var _cmLoaded = false;
var _cmLoadCallbacks = [];

function _loadCodeMirror(cb) {
    if (_cmLoaded) { cb(); return; }
    _cmLoadCallbacks.push(cb);
    if (_cmLoadCallbacks.length > 1) return; // already loading
    var base = 'https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/';
    var css = document.createElement('link');
    css.rel = 'stylesheet'; css.href = base + 'codemirror.min.css'; document.head.appendChild(css);
    // dark theme
    var cssDark = document.createElement('link');
    cssDark.rel = 'stylesheet'; cssDark.href = base + 'theme/material-darker.min.css'; document.head.appendChild(cssDark);
    var js = document.createElement('script');
    js.src = base + 'codemirror.min.js';
    js.onload = function() {
        // load language modes
        var modes = ['clike', 'sql', 'python', 'javascript', 'xml'];
        var loaded = 0;
        modes.forEach(function(m) {
            var s = document.createElement('script');
            s.src = base + 'mode/' + m + '/' + m + '.min.js';
            s.onload = function() { loaded++; if (loaded === modes.length) { _cmLoaded = true; _cmLoadCallbacks.forEach(function(c){c();}); _cmLoadCallbacks = []; } };
            document.head.appendChild(s);
        });
    };
    document.head.appendChild(js);
}

/**
 * textarea를 CodeMirror 에디터로 변환합니다.
 * @param {string} textareaId - textarea 요소 ID
 * @param {string} language   - 'java', 'sql', 'python', 'javascript', 'xml' (기본: 'java')
 * @returns {object|null} CodeMirror 인스턴스
 */
function initCodeEditor(textareaId, language) {
    var ta = document.getElementById(textareaId);
    if (!ta) return null;
    _loadCodeMirror(function() {
        if (_cmInstances[textareaId]) return;
        var modeMap = {
            'java': 'text/x-java', 'sql': 'text/x-sql', 'python': 'text/x-python',
            'javascript': 'text/javascript', 'xml': 'application/xml',
            'kotlin': 'text/x-kotlin', 'typescript': 'text/javascript'
        };
        var isDark = !document.documentElement.getAttribute('data-theme');
        var cm = CodeMirror.fromTextArea(ta, {
            mode: modeMap[language] || modeMap['java'],
            theme: isDark ? 'material-darker' : 'default',
            lineNumbers: true,
            lineWrapping: true,
            matchBrackets: true,
            indentWithTabs: false,
            indentUnit: 4,
            tabSize: 4,
            viewportMargin: Infinity
        });
        cm.setSize(null, ta.getAttribute('rows') ? (parseInt(ta.getAttribute('rows')) * 22) + 'px' : '300px');
        // sync back to textarea on change
        cm.on('change', function() { cm.save(); });
        _cmInstances[textareaId] = cm;
    });
}

/**
 * CodeMirror 인스턴스의 값을 가져옵니다.
 */
function getCodeEditorValue(textareaId) {
    var cm = _cmInstances[textareaId];
    if (cm) { cm.save(); return cm.getValue(); }
    var ta = document.getElementById(textareaId);
    return ta ? ta.value : '';
}

// ── PDF export (C10) ────────────────────────────────────────
/**
 * 분석 결과를 PDF로 내보냅니다 (인쇄 기반).
 * @param {string} elementId - 인쇄할 콘텐츠 요소 ID
 * @param {string} title     - PDF 제목 (선택)
 */
function exportPdf(elementId, title) {
    var el = document.getElementById(elementId);
    if (!el) { showToast('\ub0b4\ubcf4\ub0bc \ucf58\ud150\uce20\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.', 'warning'); return; }
    var content = el.innerHTML;
    var w = window.open('', '_blank');
    w.document.write('<!DOCTYPE html><html><head><meta charset="UTF-8">' +
        '<title>' + escHtml(title || 'Claude Toolkit Report') + '</title>' +
        '<style>body{font-family:-apple-system,sans-serif;max-width:800px;margin:40px auto;padding:0 20px;' +
        'line-height:1.7;color:#1e293b;}pre{background:#f1f5f9;padding:14px;border-radius:6px;overflow-x:auto;' +
        'font-size:0.85rem;}code{background:#f1f5f9;padding:2px 5px;border-radius:3px;font-size:0.9em;}' +
        'pre code{background:none;}h1,h2,h3,h4{border-bottom:1px solid #e2e8f0;padding-bottom:6px;}' +
        'table{border-collapse:collapse;width:100%;}td,th{border:1px solid #e2e8f0;padding:8px;}' +
        'th{background:#f8fafc;}.print-header{text-align:center;color:#64748b;font-size:0.8rem;margin-bottom:24px;}' +
        '@media print{.no-print{display:none;}}</style></head><body>' +
        '<div class="print-header">Claude Java Toolkit &mdash; ' + new Date().toLocaleDateString('ko-KR') + '</div>' +
        '<div>' + content + '</div>' +
        '<div class="no-print" style="text-align:center;margin-top:40px;">' +
        '<button onclick="window.print()" style="padding:10px 30px;font-size:1rem;cursor:pointer;">PDF \uc800\uc7a5 (Ctrl+P)</button></div>' +
        '</body></html>');
    w.document.close();
}
