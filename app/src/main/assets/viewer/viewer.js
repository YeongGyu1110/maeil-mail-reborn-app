// 앱(MarkdownView.kt)이 render()로 질문을 넘겨 그린다.
// 본문은 웹 질문 화면과 같은 방식(marked 기본값 + Prism)으로 그린다.
(function () {
  const meta = document.getElementById('meta');
  const title = document.getElementById('title');
  const content = document.getElementById('content');

  function addCopyButtons() {
    content.querySelectorAll('pre').forEach((pre) => {
      const wrapper = document.createElement('div');
      wrapper.className = 'code-wrapper';
      pre.parentNode.insertBefore(wrapper, pre);
      wrapper.appendChild(pre);

      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'code-copy-btn';
      button.textContent = '복사';
      button.addEventListener('click', () => {
        const code = pre.querySelector('code')?.innerText || pre.innerText;
        // 클립보드는 앱이 쓴다 (WebView의 navigator.clipboard는 파일에서 연 페이지에서 막힌다)
        window.MaeilApp?.copy(code);
        button.textContent = '복사됨';
        button.classList.add('copied');
        setTimeout(() => {
          button.textContent = '복사';
          button.classList.remove('copied');
        }, 1800);
      });
      wrapper.appendChild(button);
    });
  }

  // payload: { meta, title, markdown, dark }
  window.render = function (payload) {
    document.documentElement.dataset.theme = payload.dark ? 'dark' : 'light';
    meta.textContent = payload.meta || '';
    title.textContent = payload.title || '';
    content.innerHTML = marked.parse(payload.markdown || '');
    Prism.highlightAllUnder(content);
    addCopyButtons();
    window.scrollTo(0, 0);
  };

  window.setTheme = function (dark) {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  };
})();
