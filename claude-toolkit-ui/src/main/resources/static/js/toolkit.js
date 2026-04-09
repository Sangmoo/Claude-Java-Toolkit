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

// ── Print / PDF export ────────────────────────────────────────
function printPage() {
    window.print();
}
