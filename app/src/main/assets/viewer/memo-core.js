// memo-ui.js가 쓰는 웹 memo-core.js의 일부를 앱 페이지에 맞게 옮겼다 (window.MemoCore).
// 저장·커밋은 앱(Kotlin)이 하므로 여기에는 메모 그리기와 이 기기 저장소 읽고 쓰기만 둔다.
(function () {
  const MEMO_MAX = 3000;

  // 웹 normalizeMemo와 같다: 앞의 빈 줄과 끝의 공백만 걷어 낸다. 첫 줄의 들여쓰기는 남긴다
  const normalizeMemo = (text) => text.replace(/^(?:[ \t]*\n)+/, '').trimEnd();

  function readStore(type, key) {
    try { return JSON.parse(window[type].getItem(key)); } catch { return null; }
  }

  // 저장 공간이 부족하거나 저장소를 쓸 수 없으면 false
  function writeStore(type, key, value) {
    try {
      window[type].setItem(key, JSON.stringify(value));
      return true;
    } catch {
      return false;
    }
  }

  function removeStore(type, key) {
    try { window[type].removeItem(key); } catch { /* 저장소를 쓸 수 없는 환경 */ }
  }

  // marked와 Prism은 viewer.html이 이미 불러 두었다
  const loadMarkdown = () => (window.marked ? Promise.resolve() : Promise.reject(new Error('marked가 없어요')));

  const escapeHtml = (text) => text.replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);

  // 링크·이미지 주소는 http(s)·mailto와 상대 주소만 남긴다 (javascript: 같은 주소로 코드가 실행되지 않게)
  function isSafeUrl(url) {
    const scheme = url.replace(/[\u0000- \u007f]/g, '').match(/^([a-z][a-z0-9+.-]*):/i)?.[1].toLowerCase();
    return !scheme || ['http', 'https', 'mailto'].includes(scheme);
  }

  function sanitizeMarkdown(root) {
    for (const el of root.querySelectorAll('*')) {
      for (const { name, value } of [...el.attributes]) {
        if (name.startsWith('on') || ((name === 'href' || name === 'src') && !isSafeUrl(value))) el.removeAttribute(name);
      }
    }
  }

  let memoMarked = null;

  // GitHub에서 .md 파일을 보여주는 방식(GFM, 한 줄 바꿈은 이어 붙임)에 맞춰 그린다 (웹 renderMarkdown과 같다).
  // 링크는 앱이 브라우저로 연다 (MarkdownView의 shouldOverrideUrlLoading)
  function renderMarkdown(container, text) {
    memoMarked ??= new window.marked.Marked({
      gfm: true,
      // 메모 안의 HTML 태그는 실행하지 않고 글자 그대로 보여준다
      renderer: { html: ({ text: html }) => escapeHtml(html) },
    });
    const template = document.createElement('template');
    template.innerHTML = memoMarked.parse(text);
    sanitizeMarkdown(template.content);
    container.replaceChildren(template.content);
    window.Prism?.highlightAllUnder?.(container);
  }

  window.MemoCore = { MEMO_MAX, normalizeMemo, readStore, writeStore, removeStore, loadMarkdown, renderMarkdown };
})();
