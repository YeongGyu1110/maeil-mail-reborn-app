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

  // ---------- 학습 기록 ----------
  const LEVEL_LABELS = { new: '미학습', review: '복습 필요', done: '완벽 이해' };
  const study = document.getElementById('study');
  const status = document.getElementById('doc-status');
  const statusLevel = document.getElementById('status-level');
  const options = [...document.querySelectorAll('.level-option')];

  function showLevel(level) {
    for (const option of options) option.setAttribute('aria-checked', String(option.dataset.level === level));
    statusLevel.dataset.level = level;
    statusLevel.textContent = LEVEL_LABELS[level];
    // 미학습이면 머리에 표시하지 않는다 (웹과 같다)
    status.hidden = study.hidden || level === 'new';
  }

  // state: { signedIn, level } 또는 null(로그인하지 않음: 학습 기록을 감춘다)
  window.setStudy = function (state) {
    study.hidden = !state || !state.signedIn;
    showLevel(state?.level || 'new');
  };

  for (const option of options) {
    option.addEventListener('click', () => {
      showLevel(option.dataset.level); // 앱이 저장하기 전에 먼저 바꿔 보인다
      window.MaeilApp?.setLevel(option.dataset.level);
    });
  }

  statusLevel.addEventListener('click', (e) => {
    e.preventDefault();
    study.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  window.setTheme = function (dark) {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  };
})();
