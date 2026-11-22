// 앱(MarkdownView.kt)이 render()로 질문을 넘겨 그린다.
// 본문은 웹 질문 화면과 같은 방식(marked 기본값 + Prism)으로 그린다.
// 본문 끝의 학습 기록(이해도·내 메모)은 웹 questions/index.html의 화면 코드를 옮겼다.
// 메모 보기·편집기는 웹과 같은 memo-ui.js가 그리고, 저장·GitHub 커밋·확인 창은 앱이 한다 (call()로 부탁한다).
(function () {
  const meta = document.getElementById('meta');
  const title = document.getElementById('title');
  const content = document.getElementById('content');
  const Core = window.MemoCore;
  const UI = window.MemoUI;

  // ---------- 앱에 부탁하기: MaeilApp.request(이름, 번호, JSON) → 앱이 __reply(번호, 결과)로 답한다 ----------
  let requestSeq = 0;
  const waiting = new Map();

  function call(name, payload = {}) {
    return new Promise((resolve) => {
      const id = ++requestSeq;
      waiting.set(id, resolve);
      window.MaeilApp.request(name, id, JSON.stringify(payload));
    });
  }

  // 답을 기다리지 않는 알림 (번호 0)
  const tell = (name, payload = {}) => window.MaeilApp?.request(name, 0, JSON.stringify(payload));

  window.__reply = function (id, result) {
    const resolve = waiting.get(id);
    waiting.delete(id);
    resolve?.(result);
  };

  const confirmDialog = ({ title: heading, body, yes, no, tone = 'danger' }) =>
    call('confirm', { title: heading, body, yes, no, tone }).then((r) => Boolean(r?.yes));

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

  // payload: { id, meta, title, markdown, dark }
  window.render = function (payload) {
    document.documentElement.dataset.theme = payload.dark ? 'dark' : 'light';
    if (payload.id !== question.id) resetMemo();
    question = { id: payload.id || '', title: payload.title || '' };
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

  // ---------- 학습 기록 ----------
  const LEVEL_LABELS = { new: '미학습', review: '복습 필요', done: '완벽 이해' };
  const studySection = document.getElementById('study');
  const docStatus = document.getElementById('doc-status');
  const statusLevel = document.getElementById('status-level');
  const statusMemo = document.getElementById('status-memo');
  const options = [...document.querySelectorAll('.level-option')];
  const memoBody = document.getElementById('memo-body');
  const memoEdit = document.getElementById('memo-edit');

  let question = { id: '', title: '' };
  // 이 질문의 학습 기록 (앱이 setStudy로 넘긴다). null이면 로그인하지 않음
  // { level, memo, memoAt, uncommitted, canCommit, repo }
  let study = null;
  const memoText = () => study?.memo || '';

  function showLevel(level) {
    for (const option of options) option.setAttribute('aria-checked', String(option.dataset.level === level));
    statusLevel.dataset.level = level;
    statusLevel.textContent = LEVEL_LABELS[level];
    statusLevel.hidden = level === 'new';
  }

  // 제목 아래: 지금 이해도와 메모가 있는지. 처음 여는 질문은 거의 다 미학습이라 그때는 아무것도 보이지 않는다
  function renderStatus() {
    statusMemo.hidden = !memoText();
    docStatus.hidden = !study || (statusLevel.hidden && statusMemo.hidden);
  }

  window.setStudy = function (state) {
    study = state;
    studySection.hidden = !state;
    showLevel(state?.level || 'new');
    renderStatus();
    if (!state) {
      resetMemo();
      return;
    }
    // 고치던 메모가 있으면 편집기는 그대로 둔다. 고치지 않은 편집기는 새로 읽은 메모로 바꾼다 (웹 renderRecords)
    if (!editor) renderMemo();
    else if (!isEditorDirty()) {
      editorBase = memoText();
      editor.setText(editorBase);
    }
  };

  for (const option of options) {
    option.addEventListener('click', () => {
      showLevel(option.dataset.level); // 앱이 저장하기 전에 먼저 바꿔 보인다
      renderStatus();
      window.MaeilApp?.setLevel(option.dataset.level);
    });
  }

  const scrollTo = (el) => el.scrollIntoView({ behavior: 'smooth', block: 'start' });

  statusLevel.addEventListener('click', (e) => {
    e.preventDefault();
    scrollTo(studySection);
  });

  statusMemo.addEventListener('click', (e) => {
    e.preventDefault();
    scrollTo(document.getElementById('memo'));
  });

  // ---------- 메모 초안: 저장하지 않고 떠나도 이 기기에 남겨 두었다가 이어 쓴다 ----------
  const DRAFT_DELAY = 500;
  let draftTimer = 0;
  let view = null;
  let editor = null;
  let editorBase = ''; // 편집기를 열 때 저장돼 있던 메모 (그사이 다른 기기에서 바뀌었는지 비교한다)
  let saving = false;
  let tempSaving = null;
  let conflictPending = false;

  const draftKey = () => `maeil-memo-draft-${question.id}`;

  function readDraft() {
    const draft = Core.readStore('localStorage', draftKey());
    if (!draft || typeof draft.text !== 'string' || !draft.text.trim()) return null;
    return Core.normalizeMemo(draft.text) === memoText() ? null : draft;
  }

  function keepDraft(text) {
    clearTimeout(draftTimer);
    tell('editing', { dirty: isEditorDirty() });
    draftTimer = setTimeout(() => {
      if (!editor) return;
      if (isEditorDirty() && text.trim()) {
        const kept = Core.writeStore('localStorage', draftKey(), { text, at: Date.now() });
        editor.setDraftState(kept ? '초안 자동 보관됨' : '');
      } else {
        Core.removeStore('localStorage', draftKey());
        editor.setDraftState('');
      }
    }, DRAFT_DELAY);
  }

  function dropDraft() {
    clearTimeout(draftTimer);
    Core.removeStore('localStorage', draftKey());
  }

  // ---------- 메모 보기 ----------

  const shortDateFormat = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' });

  // 최근이면 '3분 전'처럼, 오래됐으면 날짜로 보여준다
  function formatWhen(time) {
    const minutes = Math.floor((Date.now() - time) / 60000);
    if (minutes < 1) return '방금';
    if (minutes < 60) return `${minutes}분 전`;
    if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}시간 전`;
    if (minutes < 7 * 24 * 60) return `${Math.floor(minutes / (24 * 60))}일 전`;
    return shortDateFormat.format(time);
  }

  function smallButton(label, action, primary = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `btn btn-sm${primary ? ' btn-primary' : ''}`;
    button.dataset.action = action;
    button.textContent = label;
    return button;
  }

  function draftNotice(draft) {
    const box = document.createElement('div');
    box.className = 'memo-draft';
    box.setAttribute('role', 'status');
    const text = document.createElement('p');
    text.className = 'memo-draft-text';
    text.style.margin = '0';
    const strong = document.createElement('strong');
    strong.textContent = '저장하지 않은 초안이 있어요';
    text.append(strong, ` · ${formatWhen(draft.at)} 쓰던 내용`);
    const buttons = document.createElement('div');
    buttons.className = 'memo-draft-buttons';
    buttons.append(smallButton('버리기', 'draft-discard'), smallButton('이어 쓰기', 'draft-resume', true));
    box.append(text, buttons);
    return box;
  }

  // 메모가 없으면 빈 글쓰기 칸처럼 보이는 버튼 하나를 둔다. 누르면 같은 자리에 같은 테두리의 편집기가 열린다
  function emptyMemo() {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'memo-empty';
    button.dataset.action = 'memo-write';
    button.setAttribute('aria-label', '메모 쓰기');
    button.innerHTML = `
      <span class="memo-empty-text">핵심을 한 문장으로, 헷갈렸던 점과 나만의 예시까지 내 언어로 정리해 보세요</span>
      <span class="memo-empty-hint">마크다운으로 쓸 수 있어요</span>`;
    return button;
  }

  function memoFoot() {
    const foot = document.createElement('div');
    foot.className = 'memo-foot';
    if (study?.memoAt) {
      const when = document.createElement('time');
      when.dateTime = new Date(study.memoAt).toISOString();
      when.textContent = `${formatWhen(study.memoAt)} 저장`;
      foot.append(when);
    }
    // 임시저장했거나 커밋에 실패해 아직 GitHub에 없는 메모
    if (study?.uncommitted) {
      const warn = document.createElement('span');
      warn.className = 'memo-foot-warn';
      warn.textContent = '아직 GitHub에 올리지 않았어요';
      const retry = document.createElement('button');
      retry.type = 'button';
      retry.className = 'link-btn';
      retry.dataset.action = 'memo-recommit';
      retry.textContent = '올리기';
      foot.append(warn, retry);
    }
    return foot;
  }

  function renderMemo({ fresh = false } = {}) {
    view?.destroy();
    view = null;
    const memo = memoText();
    const nodes = [];
    const draft = readDraft();
    if (draft) nodes.push(draftNotice(draft));
    if (memo) {
      view = UI.createMemoView(memo, { id: question.id });
      if (fresh) view.el.classList.add('is-fresh');
      nodes.push(view.el, memoFoot());
    } else {
      nodes.push(emptyMemo());
    }
    memoBody.replaceChildren(...nodes);
    memoEdit.hidden = !memo;
  }

  // 다른 질문으로 넘어가거나 로그아웃하면 열린 편집기를 닫는다 (쓰던 글은 초안으로 남아 있다)
  function resetMemo() {
    clearTimeout(draftTimer);
    editor?.destroy();
    editor = null;
    view?.destroy();
    view = null;
    saving = false;
    conflictPending = false;
    memoBody.replaceChildren();
    tell('editing', { dirty: false });
  }

  // ---------- 메모 편집 ----------

  function isEditorDirty() {
    return Boolean(editor) && Core.normalizeMemo(editor.getText()) !== memoText();
  }

  function openEditor() {
    if (editor) {
      editor.focus();
      return;
    }
    const memo = memoText();
    const draft = readDraft();
    editorBase = memo;
    conflictPending = false;
    view?.destroy();
    view = null;
    editor = UI.createMemoEditor({
      text: draft ? draft.text : memo,
      hasMemo: Boolean(memo),
      commitNote: study?.canCommit ? `저장하면 ${study.repo}의 ${question.id}.md로 커밋돼요` : '',
      draftRestored: Boolean(draft),
      ariaLabel: `${question.id.toUpperCase()} 메모`,
      canTempSave: Boolean(study?.canCommit),
      onInput: keepDraft,
      onSave: save,
      onTempSave: tempSave,
      onCancel: cancel,
    });
    memoBody.replaceChildren(editor.el);
    memoEdit.hidden = true;
    editor.mount();
    editor.focus();
    editor.el.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    tell('editing', { dirty: isEditorDirty() });
  }

  function closeEditor({ fresh = false } = {}) {
    editor?.destroy();
    editor = null;
    renderMemo({ fresh });
    renderStatus();
    tell('editing', { dirty: false });
  }

  // 앱이 저장 결과와 함께 넘긴 최신 기록으로 바꾼다 (화면 상태가 바뀌어 setStudy가 오기 전에 바로 그리려고)
  const take = (result) => {
    if (result?.study) study = result.study;
    return result || {};
  };

  async function save() {
    if (tempSaving) await tempSaving; // 방금 누른 임시저장이 끝난 뒤에 저장한다
    if (!editor || saving) return;
    const text = editor.getText();
    const memo = memoText();
    if (!text.trim()) {
      // 비우고 저장하면 지우는 것이므로 지울지 묻는다
      if (memo) remove();
      else closeEditor();
      return;
    }
    // 바뀐 것이 없어도 아직 올리지 않은 메모(임시저장)면 저장 버튼으로 커밋한다
    if (!isEditorDirty() && !study?.uncommitted) {
      dropDraft();
      closeEditor();
      return;
    }
    if (!(await confirmNoConflict(text))) return;
    saving = true;
    editor.setBusy(true);
    const result = take(await call('saveMemo', { id: question.id, text, commit: true }));
    saving = false;
    if (result.saved) dropDraft();
    closeEditor({ fresh: true });
  }

  // 임시저장: GitHub에 커밋하지 않고 계정에만 저장하고, 편집기는 열어 둔 채 계속 쓴다
  function tempSave() {
    if (!editor || saving || tempSaving) return;
    const state = () => (study?.canCommit ? '임시저장됨 · 아직 커밋 전' : '저장됨');
    const text = editor.getText();
    if (!text.trim()) return; // 빈 글을 임시저장하면 메모를 지우게 되므로 하지 않는다
    if (!isEditorDirty()) {
      editor.setDraftState(study?.uncommitted || !study?.canCommit ? state() : '바뀐 내용 없음');
      return;
    }
    tempSaving = (async () => {
      if (!(await confirmNoConflict(text))) return;
      editor?.setDraftState('저장 중…');
      const result = take(await call('saveMemo', { id: question.id, text, commit: false }));
      if (!editor) return;
      if (result.saved) {
        editorBase = memoText();
        // 저장한 뒤에 더 쓰지 않았으면 초안은 필요 없다 (더 썼으면 초안이 그 글을 계속 보관한다)
        if (!isEditorDirty()) dropDraft();
        editor.setDraftState(state());
        editor.setHasMemo(true);
      } else {
        editor.setDraftState('임시저장하지 못했어요');
      }
      renderStatus();
      tell('editing', { dirty: isEditorDirty() });
    })().finally(() => {
      tempSaving = null;
    });
  }

  // 저장 직전에 최신 기록을 본다 (앱은 기록을 읽은 지 5분이 지났을 때만 다시 읽는다).
  // 그사이 다른 곳에서 이 메모를 저장했으면 덮어쓸지 묻고, '덮어쓰기'를 고를 때까지 저장할 때마다 다시 묻는다
  async function confirmNoConflict(text) {
    const latest = (await call('latestMemo', { id: question.id }))?.memo || '';
    if (!editor) return false;
    if (!conflictPending && (latest === editorBase || latest === Core.normalizeMemo(text))) return true;
    if (latest === Core.normalizeMemo(text)) {
      conflictPending = false;
      return true;
    }
    const overwrite = await confirmDialog({
      title: '다른 곳에서 이 메모가 바뀌었어요',
      body: '편집기를 연 뒤 다른 기기에서 이 메모를 저장했어요. 지금 쓴 내용으로 덮어쓸까요?',
      yes: '덮어쓰기',
      no: '계속 쓰기',
    });
    if (!overwrite) {
      conflictPending = true;
      editor?.focus({ keepSelection: true });
      return false;
    }
    conflictPending = false;
    editorBase = latest;
    return Boolean(editor);
  }

  async function cancel() {
    if (!editor || saving) return;
    if (isEditorDirty()) {
      const discard = await confirmDialog({
        title: '쓰던 내용을 버릴까요?',
        body: '저장하지 않은 변경은 사라져요.',
        yes: '버리기',
        no: '계속 쓰기',
      });
      if (!discard) {
        editor?.focus({ keepSelection: true });
        return;
      }
    }
    dropDraft();
    closeEditor();
  }

  // 메모 지우기: 작성 칸을 비운 채 저장하면 묻는다 (따로 삭제 버튼은 없다)
  async function remove() {
    if (!editor || saving) return;
    const yes = await confirmDialog({
      title: '메모를 삭제할까요?',
      body: study?.canCommit
        ? `이 질문의 메모가 지워지고, ${study.repo}의 ${question.id}.md 파일도 삭제돼요. 지난 커밋 기록은 GitHub에 남아요.`
        : '이 질문의 메모가 지워지고 되돌릴 수 없어요.',
      yes: '삭제',
      no: '취소',
    });
    if (!yes) {
      editor?.focus({ keepSelection: true });
      return;
    }
    saving = true;
    editor?.setBusy(true);
    take(await call('saveMemo', { id: question.id, text: '', commit: true }));
    saving = false;
    dropDraft();
    closeEditor();
  }

  // GitHub 커밋만 실패했던(또는 임시저장한) 메모를 올린다
  async function recommit() {
    const memo = memoText();
    if (!memo) return;
    take(await call('saveMemo', { id: question.id, text: memo, commit: true }));
    if (!editor) renderMemo();
  }

  // 앱이 화면을 떠나며 쓰던 메모를 버리기로 했을 때 (뒤로 가기, 이전·다음 질문)
  window.discardMemo = function () {
    dropDraft();
    if (editor) closeEditor();
  };

  memoBody.addEventListener('click', (e) => {
    const action = e.target.closest('[data-action]')?.dataset.action;
    if (action === 'memo-write' || action === 'draft-resume') openEditor();
    else if (action === 'memo-recommit') recommit();
    else if (action === 'draft-discard') {
      dropDraft();
      renderMemo();
    }
  });

  memoEdit.addEventListener('click', openEditor);
})();
