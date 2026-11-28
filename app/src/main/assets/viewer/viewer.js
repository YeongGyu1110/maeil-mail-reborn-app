// 앱(MarkdownView.kt)이 render()로 질문을 넘겨 그린다.
// 본문은 웹 질문 화면과 같은 방식(marked 기본값 + Prism)으로 그린다.
// 본문 끝의 학습 기록(이해도 → 내 메모)은 웹 questions/index.html의 화면 코드를 옮겼다. 이해도의 저장은 앱이 한다.
// 메모는 본문에서 고치지 않고 메모 창에서 고친다: 본문 위에 겹쳐 여는 한 화면으로, Gmail의 답장 창처럼 질문 화면의 막대를 가린다.
// 메모 보기·편집기·겹친 곳 고르기는 웹과 같은 memo-ui.js가 그리고, 저장·GitHub 커밋·확인 창·알림은 앱이 한다 (call()로 부탁한다).
(function () {
  const meta = document.getElementById('meta');
  const title = document.getElementById('title');
  const content = document.getElementById('content');
  const Core = window.MemoCore;
  const UI = window.MemoUI;
  const { mergeMemo } = window.MemoMerge;

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

  // 확인 창은 앱이 띄운다. alt(세 번째 버튼)를 주면 'yes' | 'no' | 'alt'를, 아니면 확인했는지(true/false)를 돌려준다
  function confirmDialog({ title: heading, body, yes, no, alt = '', tone = 'danger' }) {
    return call('confirm', { title: heading, body, yes, no, alt, tone }).then((r) => (alt ? r?.answer || 'no' : r?.answer === 'yes'));
  }

  // 화면 아래 알림 (앱의 토스트)
  const toast = (text) => tell('toast', { text });

  function addCopyButtons(root) {
    root.querySelectorAll('pre').forEach((pre) => {
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

  // 답변 본문: 웹과 같은 marked 기본값 + Prism, 코드마다 복사 버튼
  function drawMarkdown(el, markdown) {
    el.innerHTML = marked.parse(markdown || '');
    Prism.highlightAllUnder(el);
    addCopyButtons(el);
  }

  // 본문 끝의 이전·다음 질문. near: { prev: { id, title } | null, next: … }
  function drawNear(root, near) {
    for (const which of ['prev', 'next']) {
      const card = root.querySelector(`.near-${which}`);
      const target = near?.[which];
      card.hidden = !target;
      card.dataset.id = target?.id || '';
      card.querySelector('.near-title').textContent = target?.title || '';
    }
    root.querySelector('.near').hidden = !near?.prev && !near?.next;
  }

  // 읽던 자리: 질문 ID → scrollY. 옆 질문으로 넘겼다가 돌아오면 읽던 자리에서 다시 보인다 (이 화면을 연 동안만 기억한다)
  const positions = new Map();
  // 새 질문을 그린 뒤 돌아갈 자리. 학습 기록(setStudy)이 늦게 와 문서가 길어지면 한 번 더 맞춘다. 손을 대면 그만둔다
  let pendingScroll = null;

  function restoreScroll() {
    if (pendingScroll !== null) window.scrollTo(0, pendingScroll);
  }

  // 지금 보는 질문의 분류와 본문 (넘긴 뒤 이 질문이 옆 질문이 될 때 그대로 쓴다)
  let shown = { meta: '', markdown: '' };

  // 문서에 한 질문을 그린다 (앱의 render와 넘기기가 함께 쓴다). 다른 질문이면 읽던 자리를 기억하고 그 질문의 자리로 간다.
  // 같은 질문을 새로 받으면(저장소와 맞춤) 본문이 바뀌었을 때만 다시 그리고 읽던 자리를 그대로 둔다
  function showQuestion(data) {
    const id = data.id || '';
    const moved = id !== question.id;
    if (moved) {
      if (question.id) positions.set(question.id, window.scrollY);
      resetMemo();
      studySig = ''; // 다른 질문의 기록은 같은 내용이어도 새로 그린다 (메모 칸을 비웠다)
    }
    question = { id, title: data.title || '' };
    meta.textContent = data.meta || '';
    title.textContent = data.title || '';
    if (moved || (data.markdown || '') !== shown.markdown) drawMarkdown(content, data.markdown);
    shown = { meta: data.meta || '', markdown: data.markdown || '' };
    if (moved) {
      pendingScroll = positions.get(id) || 0;
      restoreScroll();
    }
  }

  // 넘기기로 이 문서가 앱보다 먼저 바꾼 질문 (질문 ID → 시각). 빠르게 여러 번 넘기면 앱이 그 사이의 질문을 뒤따라 render()로
  // 넘기는데, 이미 지나간 질문이라 그리지 않는다 (잠시 동안만. 그 뒤에 오는 것은 앱이 따로 연 질문이다)
  const passed = new Map();
  const PASSED_MS = 3000;
  const isPassed = (id) => Date.now() - (passed.get(id) || 0) < PASSED_MS;

  // payload: { id, meta, title, markdown, dark, iconScale }. 학습 기록은 setStudy로, 이전·다음 질문은 setNeighbors로 따로 온다
  window.render = function (payload) {
    document.documentElement.dataset.theme = payload.dark ? 'dark' : 'light';
    // 글자 옆 아이콘은 휴대폰 글자 크기를 따라 바뀐다 (앱의 scaledIcon과 같은 비율)
    document.documentElement.style.setProperty('--icon-scale', String(payload.iconScale || 1));
    const id = payload.id || '';
    if (id !== question.id && isPassed(id)) return;
    if (id === question.id) passed.clear(); // 앱이 따라잡았다
    showQuestion(payload);
  };

  document.getElementById('near').addEventListener('click', (e) => {
    const card = e.target.closest('.near-card');
    if (card?.dataset.id) go(card.classList.contains('near-prev') ? 'prev' : 'next');
  });

  // ---------- 좌우로 밀어 옆 질문으로 넘기기 (Gmail처럼) ----------
  // 손가락을 따라 문서가 옆으로 움직이고, 옆 질문이 함께 들어온다. 화면 폭의 35%를 넘게 밀었거나 빠르게 튕기면 넘기고, 아니면 제자리로 돌아온다.
  // 가로로 분명하게 밀 때만 넘긴다: 가로로 움직인 거리가 세로의 두 배를 넘어야 한다 (안드로이드 ViewPager와 같은 기준). 위아래로 읽다가 넘어가지 않게 한다.
  // 넘기지 않는 때: 메모 창이 열려 있을 때, 글자를 고르는 중일 때, 옆으로 넘치는 코드·표 위에서 밀 때(그 코드를 민다), 손가락이 둘일 때.
  // 옆 질문의 그림은 이 문서의 틀(#page)을 복사해 같은 코드로 그린다. 보기만 하는 그림이라 누를 수 없다.
  // 넘기면 앱을 기다리지 않고 이 문서에 옆 질문을 바로 그리고(가지고 있는 본문과 기록으로), 앱에는 뒤따라 넘기라고 알린다(navigate).
  // 그래서 넘긴 직후에 바로 다시 밀 수 있다. 넘어가는 움직임 중에 다시 닿으면 그 움직임을 바로 끝내고 새로 민다.
  // 다음 다음 질문까지(앞뒤 두 개씩) 받아 두어, 앱이 따라잡기 전에 한 번 더 넘겨도 옆 질문이 있다.
  const page = document.getElementById('page');
  const peeks = { prev: document.getElementById('peek-prev'), next: document.getElementById('peek-next') };
  // 아무것도 그리기 전의 문서 틀 (id는 지워 옆 질문의 그림과 겹치지 않게 한다)
  const pageTemplate = page.cloneNode(true);
  pageTemplate.removeAttribute('id');
  pageTemplate.querySelectorAll('[id]').forEach((el) => el.removeAttribute('id'));

  const SLOP = 10; // 이만큼 움직인 뒤에 가로인지 세로인지 정한다 (px)
  const COMMIT = 0.35; // 화면 폭의 이만큼을 넘게 밀면 넘긴다
  const FLING = 0.4; // 이보다 빠르게(px/ms) 튕기면 조금만 밀어도 넘긴다
  let swipe = { state: 'idle' }; // idle | dragging | settling (finish: 움직임을 바로 끝낸다)
  let gesture = null; // 지금 손가락 하나의 움직임
  // 앞뒤 질문: { id, title, meta, markdown, study, near } (앱이 setNeighbors로 넘긴다). prev2·next2는 그 너머의 질문
  let neighbors = { prev2: null, prev: null, next: null, next2: null };
  let peekTimer = 0;

  window.setNeighbors = function (payload) {
    // 빠르게 넘기는 동안 앱이 뒤따라 넘기는 지나간 질문의 이웃은 버린다
    if (payload?.id && payload.id !== question.id) return;
    neighbors = {
      prev2: payload?.prev2 || null,
      prev: payload?.prev || null,
      next: payload?.next || null,
      next2: payload?.next2 || null,
    };
    drawNear(page, neighbors);
    // 지금 보는 문서를 먼저 그리고, 옆 질문은 잠시 뒤에 미리 그려 둔다 (밀기 시작할 때 아직이면 그때 그린다)
    if (swipe.state === 'idle') schedulePeeks(150);
  };

  // 지금 질문을 옆 질문의 모양으로 (넘긴 뒤 이 질문이 옆 질문이 된다)
  const hereAsNeighbor = () => ({
    id: question.id,
    title: question.title,
    meta: shown.meta,
    markdown: shown.markdown,
    study,
    near: { prev: neighbors.prev && { id: neighbors.prev.id, title: neighbors.prev.title }, next: neighbors.next && { id: neighbors.next.id, title: neighbors.next.title } },
  });

  // first 쪽 옆 질문부터 미리 그린다 (넘긴 방향으로 이어 넘기기 쉽다)
  function schedulePeeks(delay, first = 'next') {
    clearTimeout(peekTimer);
    peekTimer = setTimeout(() => {
      if (swipe.state !== 'idle') return;
      ensurePeek(first);
      ensurePeek(first === 'next' ? 'prev' : 'next');
    }, delay);
  }

  // 옆 질문의 그림이 지금의 이웃과 같은지 보고, 다르면 새로 그린다. 읽던 자리가 있으면 그 자리를 보인다
  function ensurePeek(which) {
    const panel = peeks[which];
    const data = neighbors[which];
    const sig = data ? JSON.stringify(data) : '';
    if (panel.sig !== sig) {
      drawPeek(panel, data);
      panel.sig = sig;
    }
    panel.scrollTop = data ? positions.get(data.id) || 0 : 0;
  }

  function drawPeek(panel, data) {
    panel.views?.forEach((view) => view.destroy());
    panel.views = [];
    panel.dataset.id = data?.id || '';
    if (!data) {
      panel.replaceChildren();
      return;
    }
    const doc = pageTemplate.cloneNode(true);
    doc.querySelector('.doc-meta').textContent = data.meta || '';
    doc.querySelector('.doc-title').textContent = data.title || '';
    drawMarkdown(doc.querySelector('.doc-content'), data.markdown);
    const record = data.study || null;
    drawStatus(doc, record);
    doc.querySelector('.study').hidden = !record;
    if (record) {
      drawLevel(doc, record.level || 'new');
      const nodes = [];
      if (record.draft) nodes.push(draftNotice(record.draft));
      if (record.memo) {
        const view = UI.createMemoView(record.memo, { id: `peek-${data.id}` });
        panel.views.push(view);
        nodes.push(view.el, memoFoot(record));
      } else if (!record.draft) {
        nodes.push(emptyMemo());
      }
      doc.querySelector('.memo-body').replaceChildren(...nodes);
      doc.querySelector('.memo-edit').hidden = !record.memo || Boolean(record.draft);
      doc.querySelector('.memo-repo').hidden = Boolean(record.canCommit);
    }
    drawNear(doc, data.near);
    panel.replaceChildren(doc);
  }

  // 시험할 때만: window.__swipeLog = []를 넣으면 넘기기를 정한 까닭을 남긴다
  const trace = (...args) => window.__swipeLog?.push(args.join(' '));

  const hasSelection = () => {
    const selection = window.getSelection();
    return Boolean(selection && selection.rangeCount && !selection.isCollapsed);
  };

  // 손가락 아래에서 옆으로 넘치는 코드·표 (그 위에서는 질문을 넘기지 않고 코드를 민다)
  function scrollerAt(target) {
    for (let el = target; el && el !== page; el = el.parentElement) {
      if (el.scrollWidth > el.clientWidth + 1 && /auto|scroll/.test(getComputedStyle(el).overflowX)) return el;
    }
    return null;
  }

  // 문서를 x만큼 옆으로 옮기고, side 쪽 옆 질문을 그 옆에 붙인다
  function place(x, side) {
    const width = window.innerWidth;
    page.style.transform = x ? `translate3d(${x}px, 0, 0)` : '';
    for (const which of ['prev', 'next']) {
      const panel = peeks[which];
      const shownHere = which === side;
      panel.classList.toggle('is-shown', shownHere);
      panel.style.transform = shownHere ? `translate3d(${which === 'prev' ? x - width : x + width}px, 0, 0)` : '';
    }
  }

  // 지금 자리에서 x로 미끄러져 간 뒤 done을 부른다. 그 사이에 다시 닿으면 swipe.finish()로 바로 끝낸다
  function settle(x, side, done) {
    const animated = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let finished = false;
    const finish = (e) => {
      // 문서 안 다른 것(이해도 칸 등)의 움직임이 끝난 것은 거른다
      if (finished || (e?.type === 'transitionend' && e.target !== page)) return;
      finished = true;
      page.removeEventListener('transitionend', finish);
      for (const el of [page, peeks.prev, peeks.next]) el.classList.remove('is-settling');
      swipe = { state: 'idle' };
      done();
    };
    swipe = { state: 'settling', finish };
    if (animated) {
      for (const el of [page, peeks[side] || page]) el.classList.add('is-settling');
      page.addEventListener('transitionend', finish);
      setTimeout(finish, 320); // 움직이지 않아(이미 그 자리) transitionend가 오지 않을 때
    }
    place(x, side);
    if (!animated) finish();
  }

  function settleBack(side) {
    settle(0, side, () => place(0, null));
  }

  // side 쪽 질문으로 넘긴다: 끝까지 미끄러진 뒤 그 질문을 이 문서에 그리고 옆 질문의 그림을 걷는다 (같은 순간이라 이어서 보인다)
  function commit(side) {
    settle(side === 'prev' ? window.innerWidth : -window.innerWidth, side, () => {
      advance(side);
      place(0, null);
      schedulePeeks(0, side);
    });
  }

  // 옆 질문을 지금 질문으로: 가지고 있는 본문과 기록으로 바로 그리고, 앞뒤 질문을 한 칸 옮긴 뒤 앱에 알린다
  function advance(side) {
    const target = neighbors[side];
    const here = hereAsNeighbor();
    showQuestion(target);
    applyStudy(target.study || null);
    studySig = JSON.stringify(target.study || null);
    neighbors = side === 'next'
      ? { prev2: neighbors.prev, prev: here, next: neighbors.next2, next2: null }
      : { prev2: null, prev: neighbors.prev2, next: here, next2: neighbors.next };
    drawNear(page, neighbors);
    passed.set(target.id, Date.now());
    trace('advance', target.id);
    tell('navigate', { id: target.id });
  }

  // 본문 끝의 이전·다음 질문을 누르면 민 것처럼 넘긴다
  function go(side) {
    if (swipe.state === 'settling') swipe.finish();
    if (swipe.state !== 'idle' || !neighbors[side] || !memoWindow.hidden) return;
    ensurePeek(side);
    place(0, side);
    // 옆 질문이 화면 밖 제자리에 놓인 것을 먼저 그리게 한다 (같은 순간에 옮기면 미끄러지지 않고 바로 나타난다)
    void peeks[side].offsetWidth;
    commit(side);
  }

  // 손가락의 움직임은 처음 닿은 요소로 간다. 넘기면서 그 요소가 문서에서 빠지면(새 질문을 그림) 문서까지 올라오지 않으므로,
  // 처음 닿은 요소에도 같은 처리를 붙인다. 두 곳에서 받으면 한 번만 처리한다
  function onTouchStart(e) {
    pendingScroll = null;
    if (gesture?.mode === 'swipe' && e.touches.length > 1) {
      // 밀던 중에 손가락이 하나 더 닿으면 그만둔다
      const side = gesture.side;
      endTracking();
      settleBack(neighbors[side] ? side : null);
      return;
    }
    // 넘어가는 중에 다시 닿으면 그 움직임을 바로 끝내고 새로 민다 (빠르게 여러 번 넘길 때 손가락을 놓치지 않는다)
    if (swipe.state === 'settling' && e.touches.length === 1) swipe.finish();
    if (e.touches.length !== 1 || swipe.state !== 'idle' || !memoWindow.hidden || memoWindow.contains(e.target) || hasSelection()) {
      trace('start-skip', e.touches.length, swipe.state, memoWindow.hidden, hasSelection());
      endTracking();
      return;
    }
    const touch = e.touches[0];
    endTracking();
    gesture = {
      x0: touch.clientX,
      y0: touch.clientY,
      mode: scrollerAt(e.target) ? 'native' : 'undecided',
      samples: [[e.timeStamp, touch.clientX]],
      x: 0,
      side: null,
      target: e.target,
    };
    gesture.target.addEventListener('touchmove', onTouchMove, { passive: false });
    gesture.target.addEventListener('touchend', onTouchEnd);
    gesture.target.addEventListener('touchcancel', onTouchEnd);
    trace('start', gesture.mode);
  }

  function endTracking() {
    if (!gesture) return;
    gesture.target.removeEventListener('touchmove', onTouchMove);
    gesture.target.removeEventListener('touchend', onTouchEnd);
    gesture.target.removeEventListener('touchcancel', onTouchEnd);
    gesture = null;
  }

  // 같은 움직임을 처음 닿은 요소와 문서에서 두 번 받지 않는다
  const firstTime = (e) => {
    if (e.swipeSeen) return false;
    e.swipeSeen = true;
    return true;
  };

  function onTouchMove(e) {
    if (!gesture || e.touches.length !== 1 || !firstTime(e)) return;
    const touch = e.touches[0];
    const dx = touch.clientX - gesture.x0;
    const dy = touch.clientY - gesture.y0;
    if (gesture.mode === 'undecided') {
      const ax = Math.abs(dx);
      const ay = Math.abs(dy);
      if (ax > SLOP && ax * 0.5 > ay) {
        // 브라우저가 이미 위아래로 넘기기 시작했으면(취소할 수 없는 움직임) 넘기지 않는다
        if (!e.cancelable || hasSelection() || !memoWindow.hidden) {
          trace('move-native', e.cancelable, hasSelection());
          gesture.mode = 'native';
          return;
        }
        trace('swipe', Math.round(dx), Math.round(dy));
        gesture.mode = 'swipe';
        gesture.x0 += Math.sign(dx) * SLOP; // 정하느라 움직인 만큼 튀지 않게 그 자리에서 시작한다
        swipe = { state: 'dragging' };
        ensurePeek(dx < 0 ? 'next' : 'prev');
        ensurePeek(dx < 0 ? 'prev' : 'next');
      } else if (ay > SLOP) {
        trace('vertical', Math.round(dx), Math.round(dy));
        gesture.mode = 'native';
      }
    }
    if (gesture.mode !== 'swipe') return;
    e.preventDefault();
    const raw = touch.clientX - gesture.x0;
    const side = raw > 0 ? 'prev' : raw < 0 ? 'next' : gesture.side;
    // 그쪽에 질문이 없으면(목록의 처음·끝, 또는 아직 받지 못함) 덜 따라오게 해서 끝임을 알린다
    gesture.x = neighbors[side] ? raw : raw / 3;
    gesture.side = side;
    gesture.samples.push([e.timeStamp, touch.clientX]);
    if (gesture.samples.length > 8) gesture.samples.shift();
    place(gesture.x, neighbors[side] ? side : null);
  }

  function onTouchEnd(e) {
    const ended = gesture;
    if (!ended || e.touches.length || !firstTime(e)) return;
    endTracking();
    if (ended.mode !== 'swipe') return;
    // 마지막 0.1초 동안의 빠르기 (px/ms)
    const [lastTime, lastX] = ended.samples[ended.samples.length - 1];
    const [startTime, startX] = ended.samples.find(([time]) => lastTime - time <= 100) || ended.samples[0];
    const velocity = lastTime > startTime ? (lastX - startX) / (lastTime - startTime) : 0;
    const side = ended.side;
    const far = Math.abs(ended.x) > window.innerWidth * COMMIT;
    const flung = Math.abs(velocity) > FLING && Math.sign(velocity) === Math.sign(ended.x) && Math.abs(ended.x) > 24;
    trace('end', e.type, Math.round(ended.x), velocity.toFixed(2), far, flung);
    if (e.type === 'touchend' && neighbors[side] && (far || flung)) commit(side);
    else settleBack(neighbors[side] ? side : null);
  }

  // 문서에 붙인 touchmove가 있어야 브라우저가 움직임마다 이 처리를 기다린다 (가로로 밀 때 화면이 위아래로 넘어가지 않게 막을 수 있다)
  document.addEventListener('touchstart', onTouchStart, { passive: true });
  document.addEventListener('touchmove', onTouchMove, { passive: false });
  document.addEventListener('touchend', onTouchEnd);
  document.addEventListener('touchcancel', onTouchEnd);

  window.setTheme = function (dark) {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  };

  // 본문이 화면 아래 끝(시스템 손잡이 뒤)까지 닿으므로, 본문의 끝을 그만큼 더 띄운다 (앱이 dp로 알려 준다)
  window.setInsets = function ({ bottom = 0 } = {}) {
    document.documentElement.style.setProperty('--inset-bottom', `${bottom}px`);
  };

  // ---------- 학습 기록: 이해도 · 내 메모 ----------
  const studySection = document.getElementById('study');
  const memoSection = document.getElementById('memo');
  const statusLevel = document.getElementById('status-level');
  const statusMemo = document.getElementById('status-memo');
  const levelOptions = [...document.querySelectorAll('#level-control .level-option')];
  const memoRepo = document.getElementById('memo-repo');
  const memoBody = document.getElementById('memo-body');
  const memoEdit = document.getElementById('memo-edit');
  const memoWindow = document.getElementById('memo-window');

  let question = { id: '', title: '' };
  // 이 질문의 학습 기록 (앱이 setStudy로 넘긴다). null이면 로그인하지 않음
  // { level: 'new' | 'review' | 'done', memo, memoAt, uncommitted, canCommit, repo, draft: { text, at, base } | null }
  let study = null;
  const memoText = () => study?.memo || '';
  const draftOf = () => study?.draft || null;
  const levelOf = () => study?.level || 'new';
  // 이 글을 메모로 저장하면 GitHub에 커밋하는지 (앱의 Records.saveMemo와 같은 조건)
  const willCommit = (text) => Boolean(study?.canCommit) && Boolean(text) && (text !== memoText() || Boolean(study?.uncommitted));
  const LEVEL_LABELS = { new: '미학습', review: '복습 필요', done: '완벽 이해' };

  // 제목 아래: 지금 이해도와 메모(초안)가 있는지. 처음 여는 질문은 거의 다 미학습이라 그때는 수준을 보이지 않는다 (웹과 같다).
  // root는 이 문서(#page) 또는 옆 질문의 그림, record는 그 질문의 학습 기록
  function drawStatus(root, record) {
    const level = record?.level || 'new';
    const badge = root.querySelector('.level-badge');
    const memo = root.querySelector('.status-memo');
    badge.dataset.level = level;
    badge.textContent = LEVEL_LABELS[level];
    badge.hidden = level === 'new';
    memo.hidden = !record?.memo && !record?.draft;
    root.querySelector('.doc-status').hidden = !record || (badge.hidden && memo.hidden);
  }

  function drawLevel(root, level) {
    for (const option of root.querySelectorAll('.level-option')) {
      const checked = option.dataset.level === level;
      option.setAttribute('aria-checked', String(checked));
      option.tabIndex = checked ? 0 : -1;
    }
  }

  const renderStatus = () => drawStatus(page, study);
  const renderLevel = () => drawLevel(page, levelOf());

  // 고르면 바로 바꿔 그리고 저장은 앱에 맡긴다 (앱이 바뀐 기록을 setStudy로 다시 넘긴다)
  function setLevel(level) {
    if (!study || levelOf() === level) return;
    study = { ...study, level };
    renderLevel();
    renderStatus();
    tell('setLevel', { id: question.id, level });
  }

  document.getElementById('level-control').addEventListener('click', (e) => {
    const option = e.target.closest('.level-option');
    if (option) setLevel(option.dataset.level);
  });

  // 라디오 묶음처럼 화살표로 옮겨 고른다 (웹과 같다)
  document.getElementById('level-control').addEventListener('keydown', (e) => {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
    if (!step) return;
    e.preventDefault();
    const index = levelOptions.findIndex((option) => option.dataset.level === levelOf());
    const next = levelOptions[(index + step + levelOptions.length) % levelOptions.length];
    setLevel(next.dataset.level);
    next.focus();
  });

  // 마지막으로 그린 학습 기록 (같은 기록이 다시 오면 메모를 새로 그리지 않는다. 넘긴 뒤 앱이 같은 기록을 뒤따라 넘긴다)
  let studySig = '';

  // id: 이 기록의 질문. 빠르게 넘기는 동안 앱이 뒤따라 넘기는 지나간 질문의 기록은 버린다
  window.setStudy = function (state, id) {
    if (id !== undefined && id !== question.id) return;
    const sig = JSON.stringify(state);
    if (sig === studySig) return;
    studySig = sig;
    applyStudy(state);
  };

  function applyStudy(state) {
    study = state;
    studySection.hidden = !state;
    memoRepo.hidden = !state || state.canCommit;
    renderStatus();
    if (!state) {
      resetMemo();
    } else {
      renderLevel();
      if (!editor) renderMemo();
      else adoptRecords();
      moveOldDrafts();
    }
    // 새 질문을 그린 직후면, 학습 기록까지 그려 길어진 문서에서 읽던 자리로 다시 맞춘다
    if (pendingScroll !== null) {
      restoreScroll();
      pendingScroll = null;
    }
  }

  // 메모 창을 연 뒤 새로 읽은 기록이 오면(메모 창을 열 때 앱이 다시 읽는다): 아직 쓰기 시작하지 않았으면 최신 초안(없으면 메모)으로
  // 바꾸고 알린다. 쓰기 시작했으면 그대로 두고, 저장할 때 새로 읽어 합친다 (웹 renderRecords와 같다)
  function adoptRecords() {
    editor.setHasMemo(Boolean(memoText()));
    if (hasUnsaved() || conflict || saving || draftSaving) return;
    const previous = session;
    const next = sessionFromRecords();
    if (next.saved === previous.saved && next.draftAt === previous.draftAt) return;
    session = next;
    editor.setText(next.saved);
    renderDraftState();
    renderSaveButton();
    toast(next.draftAt && next.draftAt !== previous.draftAt
      ? '다른 곳에서 저장한 초안으로 바꿨어요.'
      : !next.draftAt && previous.draftAt
        ? '다른 곳에서 이 초안을 메모로 저장했거나 지워서, 지금 메모로 바꿨어요.'
        : '다른 곳에서 바뀐 메모로 바꿨어요.');
  }

  // 앱이 저장 결과와 함께 넘긴 이 질문의 최신 기록으로 바꾼다 (화면 상태가 바뀌어 setStudy가 오기 전에 바로 그리려고).
  // 그사이 다른 질문으로 넘어갔으면 버린다 (예전 질문의 기록으로 새 질문의 메모를 그리지 않게)
  function take(result, id) {
    if (result?.study && id === question.id) {
      study = result.study;
      studySig = ''; // 앱이 뒤이어 넘기는 같은 기록도 그대로 다시 그린다 (예전과 같게)
    }
    return result || {};
  }

  statusLevel.addEventListener('click', (e) => {
    e.preventDefault();
    studySection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  statusMemo.addEventListener('click', (e) => {
    e.preventDefault();
    memoSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  document.getElementById('memo-repo-pick').addEventListener('click', () => tell('pickRepo'));

  // ---------- 예전 기기 보관 초안 옮기기 ----------
  // 예전 앱은 쓰던 메모를 이 화면의 저장소(localStorage)에 저절로 보관했다 (maeil-memo-draft-{id}).
  // 남아 있으면 한 번만 계정의 초안으로 옮긴다. 너무 긴 글은 옮기지도 지우지도 않는다 (앱이 done에 넣지 않는다)
  const OLD_DRAFT_PREFIX = 'maeil-memo-draft-';
  let oldDrafts = 'unchecked'; // 'unchecked' | 'moving' | 'done'

  async function moveOldDrafts() {
    if (oldDrafts !== 'unchecked') return;
    let keys = [];
    try {
      keys = Object.keys(localStorage).filter((key) => key.startsWith(OLD_DRAFT_PREFIX));
    } catch {
      keys = [];
    }
    if (!keys.length) {
      oldDrafts = 'done';
      return;
    }
    const drafts = keys.map((key) => {
      const old = Core.readStore('localStorage', key);
      return { id: key.slice(OLD_DRAFT_PREFIX.length), text: typeof old?.text === 'string' ? old.text : '', at: Math.trunc(Number(old?.at)) || 0 };
    });
    oldDrafts = 'moving';
    const result = await call('moveOldDrafts', { drafts });
    // 기록을 아직 읽지 않았으면 다음에 기록이 오면(setStudy) 다시 한다
    if (!result?.ready) {
      oldDrafts = 'unchecked';
      return;
    }
    oldDrafts = 'done';
    for (const id of result.done || []) Core.removeStore('localStorage', OLD_DRAFT_PREFIX + id);
    if (result.moved?.length) toast(`이 기기에 남아 있던 쓰던 메모 ${result.moved.length}개를 초안으로 옮겼어요.`);
  }

  // ---------- 메모 보기 ----------

  let view = null;
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

  function make(tag, className, text) {
    const el = document.createElement(tag);
    if (className) el.className = className;
    if (text) el.textContent = text;
    return el;
  }

  function smallButton(label, action, primary = false) {
    const button = make('button', `btn btn-sm${primary ? ' btn-primary' : ''}`, label);
    button.type = 'button';
    button.dataset.action = action;
    return button;
  }

  // 메모와 따로 저장해 둔 초안이 있다 (메모는 그대로다). 이어 쓰거나 지운다
  function draftNotice(draft) {
    const box = make('div', 'memo-draft');
    const text = make('p', 'memo-draft-text');
    text.style.margin = '0';
    const when = make('time', '', `${formatWhen(draft.at)} 저장`);
    when.dateTime = new Date(draft.at).toISOString();
    text.append(make('strong', '', '초안이 있어요'), ' · ', when);
    const buttons = make('div', 'memo-draft-buttons');
    buttons.append(smallButton('초안 삭제', 'draft-delete'), smallButton('이어 쓰기', 'draft-resume', true));
    box.append(text, buttons);
    return box;
  }

  // 메모가 없으면 빈 글쓰기 칸처럼 보이는 버튼 하나를 둔다. 누르면 메모 창이 열린다
  function emptyMemo() {
    const button = make('button', 'memo-empty');
    button.type = 'button';
    button.dataset.action = 'memo-write';
    button.setAttribute('aria-label', '메모 쓰기');
    button.innerHTML = `
      <span class="memo-empty-text">핵심을 한 문장으로, 헷갈렸던 점과 나만의 예시까지 내 언어로 정리해 보세요</span>
      <span class="memo-empty-hint">마크다운으로 쓸 수 있어요</span>`;
    return button;
  }

  // record: 그 질문의 학습 기록 (옆 질문의 그림도 같은 모양으로 그린다)
  function memoFoot(record = study) {
    const foot = make('div', 'memo-foot');
    if (record?.memoAt) {
      const when = make('time', '', `${formatWhen(record.memoAt)} 저장`);
      when.dateTime = new Date(record.memoAt).toISOString();
      foot.append(when);
    }
    // 커밋에 실패했거나 예전 임시저장으로 저장해 아직 GitHub에 없는 메모
    if (record?.uncommitted) {
      const retry = make('button', 'link-btn', '올리기');
      retry.type = 'button';
      retry.dataset.action = 'memo-recommit';
      foot.append(make('span', 'memo-foot-warn', '아직 GitHub에 올리지 않았어요'), retry);
    }
    return foot;
  }

  function renderMemo({ fresh = false } = {}) {
    view?.destroy();
    view = null;
    const memo = memoText();
    const draft = draftOf();
    const nodes = [];
    if (draft) nodes.push(draftNotice(draft));
    if (memo) {
      view = UI.createMemoView(memo, { id: question.id });
      if (fresh) view.el.classList.add('is-fresh');
      nodes.push(view.el, memoFoot());
    } else if (!draft) {
      nodes.push(emptyMemo());
    }
    memoBody.replaceChildren(...nodes);
    // 초안이 있으면 초안 알림의 '이어 쓰기'로 연다 (같은 버튼을 두 번 두지 않는다)
    memoEdit.hidden = !memo || Boolean(draft);
  }

  // 다른 질문으로 넘어가거나 로그아웃하면 메모 창을 닫는다 (앱이 먼저 떠나도 되는지 묻는다: requestLeave)
  function resetMemo() {
    closeMenu();
    conflict = null;
    editor?.destroy();
    editor = null;
    session = null;
    saving = false;
    view?.destroy();
    view = null;
    memoBody.replaceChildren();
    if (!memoWindow.hidden) hideWindow({ instant: true });
  }

  // ---------- 메모 창 ----------
  // 메모는 바로 고치지 않고 초안에서 고친다. 초안 저장(더보기, 닫을 때 묻는 창)은 메모를 그대로 두고 초안만 계정에 저장하고,
  // 저장은 초안을 메모로 만들어 커밋한다. 초안은 계정에 두어 PC에서도 이어 쓴다. 저절로 저장하지 않는다 (웹과 같다).

  let editor = null;
  let session = null; // { base: 이 글을 쓰기 시작할 때의 메모, draftAt: 이어 쓰는 초안의 저장 시각(없으면 0), saved: 마지막으로 저장한 글 }
  let conflict = null;
  let menu = null;
  let saving = false;

  const editorText = () => Core.normalizeMemo(editor.getText());
  const hasUnsaved = () => Boolean(editor) && editorText() !== session.saved;
  // 지울 초안이 있는지: 이 기기의 기록에 있거나, 다른 곳에서 저장한 초안을 메모 창에서 합쳤다 (그 초안은 이 기기의 기록에 없다)
  const hasDraft = () => Boolean(session?.draftAt || draftOf());
  const saveButton = () => editor?.el.querySelector('[data-action="save"]');

  // 메모 창에 연 글: 초안이 있으면 그 초안, 없으면 메모
  function sessionFromRecords() {
    const memo = memoText();
    const draft = draftOf();
    return draft ? { base: draft.base, draftAt: draft.at, saved: draft.text } : { base: memo, draftAt: 0, saved: memo };
  }

  // 메모와 초안은 MEMO_MAX자까지다 (firestore.rules도 같은 길이에서 초안을 거부한다).
  // 다른 곳의 글과 합쳐 길어진 글은 편집기의 글자 수 제한(maxLength)을 거치지 않으므로, 저장할 때 막고 줄이라고 알린다
  function blockTooLong(text) {
    if (text.length <= Core.MEMO_MAX) return false;
    toast(`${Core.MEMO_MAX.toLocaleString()}자를 넘었어요(${text.length.toLocaleString()}자). 줄인 뒤 저장해 주세요.`);
    editor.focus({ keepSelection: true });
    return true;
  }

  // 합친 글을 편집기에 넣었다고 알릴 때 덧붙인다 (합쳐서 MEMO_MAX자를 넘었으면 줄여야 저장된다)
  const overLimitNote = (text) => (text.length > Core.MEMO_MAX ? ` 합친 글이 ${Core.MEMO_MAX.toLocaleString()}자를 넘어, 줄여야 저장할 수 있어요.` : '');

  // 글 아래: 지금 쓰는 글이 초안인지, 저장하지 않은 변경이 있는지
  function renderDraftState() {
    if (!editor) return;
    const unsaved = hasUnsaved();
    if (session.draftAt) editor.setDraftState(unsaved ? '초안 · 저장하지 않은 변경 있음' : `초안 · ${formatWhen(session.draftAt)} 저장`);
    else editor.setDraftState(unsaved ? '저장하지 않은 변경 있음' : '');
  }

  // 저장은 할 것이 있을 때만 초록으로 켠다: 바뀐 글, 지울 초안, 아직 올리지 않은 메모 (없으면 저장은 그냥 닫는 것이라 흐리게 둔다)
  function renderSaveButton() {
    const button = saveButton();
    if (!button || saving) return;
    button.disabled = !(editorText() !== memoText() || hasDraft() || study?.uncommitted);
  }

  const ICON = (body) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
  const BACK_ICON = ICON('<path d="M19 12H5"></path><path d="m12 19-7-7 7-7"></path>');
  const MORE_ICON = '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="10.5" y="3.5" width="3" height="3" rx="0.6"></rect><rect x="10.5" y="10.5" width="3" height="3" rx="0.6"></rect><rect x="10.5" y="17.5" width="3" height="3" rx="0.6"></rect></svg>';

  // 편집기(memo-ui.js)의 부품을 그대로 쓰고 자리만 옮긴다: 위 막대에 닫기·보기 전환·저장·더보기, 지면에 질문 제목과 글, 아래 막대에 서식
  function openEditor() {
    if (editor) return;
    session = sessionFromRecords();
    editor = UI.createMemoEditor({
      text: session.saved,
      hasMemo: Boolean(memoText()),
      commitNote: study?.canCommit ? `저장하면 ${study.repo}의 ${question.id}.md로 커밋돼요` : '',
      ariaLabel: `${question.id.toUpperCase()} 메모`,
      onInput: () => {
        renderDraftState();
        renderSaveButton();
      },
      onSave: save,
      onSaveDraft: () => saveDraftNow(),
      onCancel: windowBack,
      onMore: toggleMenu,
    });
    const el = editor.el;
    const $ = (selector) => el.querySelector(selector);
    el.classList.add('is-window');

    const bar = make('header', 'win-bar');
    const back = make('button', 'win-icon');
    back.type = 'button';
    back.setAttribute('aria-label', '닫기');
    back.innerHTML = BACK_ICON;
    back.addEventListener('click', windowBack);
    const buttons = $('.memo-editor-buttons');
    const more = $('[data-action="more"]');
    more.className = 'win-icon';
    more.innerHTML = MORE_ICON;
    buttons.append(more); // 저장 다음, 막대의 오른쪽 끝
    bar.append(back, $('.memo-modes'), buttons);

    const paper = make('div', 'win-paper');
    const head = make('div', 'win-head');
    head.append(make('div', 'doc-meta', meta.textContent), make('h2', 'win-title', title.textContent));
    const tail = make('div', 'win-tail');
    tail.append($('.memo-count'), $('.memo-draft-state'), $('.memo-hint'), $('.memo-commit-note'));
    paper.append(head, $('.memo-panes'), tail);
    // 글 칸 아래 빈 곳을 눌러도 이어 쓴다
    paper.addEventListener('click', (e) => {
      if (e.target === paper && el.dataset.mode !== 'preview') editor.focus();
    });

    // 서식 버튼이나 더보기를 눌러도 글 칸의 초점(자판)이 풀리지 않게 한다
    const tools = $('.memo-toolbar');
    for (const keep of [tools, more]) keep.addEventListener('mousedown', (e) => e.preventDefault());
    $('.memo-editor-foot').remove();
    el.prepend(bar, paper);
    el.append(tools);

    memoWindow.replaceChildren(el);
    showWindow();
    editor.mount();
    // 늘 작성으로 연다 (미리보기는 위 막대에서 바꾼다)
    $('.memo-mode[data-mode="write"]').click();
    editor.focus();
    renderDraftState();
    renderSaveButton();
    // 앱은 기록을 열 때 1분이 지났을 때만 다시 읽어, 다른 기기에서 방금 저장한 초안이나 메모가 아직 없을 수 있다.
    // 메모 창을 열면 앱이 새로 읽고, 바뀌었으면 setStudy로 넘긴다 (adoptRecords)
    tell('refreshRecords');
  }

  // 메모 창을 연다: 질문 화면의 위아래 막대를 가리고(앱이 한다) 옆에서 조금 밀려 들어오며 나타난다 (앱의 화면 옮기기와 같다)
  let turn = 0; // 여닫는 중에 다시 여닫으면 앞의 것은 버린다
  const still = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

  function showWindow() {
    ++turn;
    for (const animation of memoWindow.getAnimations()) animation.cancel();
    memoWindow.hidden = false;
    document.documentElement.classList.add('is-writing');
    tell('memoWindow', { open: true });
    if (still()) return;
    memoWindow.animate(
      [{ opacity: 0, transform: 'translateX(34px)' }, { opacity: 1, transform: 'none' }],
      { duration: 200, easing: 'ease-out' },
    );
  }

  function hideWindow({ instant = false } = {}) {
    const mine = ++turn;
    for (const animation of memoWindow.getAnimations()) animation.cancel();
    document.documentElement.classList.remove('is-writing');
    tell('memoWindow', { open: false });
    const done = () => {
      if (mine !== turn) return;
      memoWindow.hidden = true;
      memoWindow.replaceChildren();
    };
    if (instant || still()) {
      done();
      return;
    }
    memoWindow.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 100 }).finished.then(done, done);
  }

  function closeEditor({ fresh = false } = {}) {
    closeMenu();
    conflict = null;
    editor?.destroy();
    editor = null;
    session = null;
    saving = false;
    document.activeElement?.blur();
    hideWindow();
    renderMemo({ fresh });
    renderStatus();
  }

  // 저장 직전에 이 초안과 메모를 새로 읽어, 메모 창을 연 뒤 다른 곳(PC 등)에서 바뀌었는지 본다 (웹 catchUp과 같다).
  // 바뀌었으면 그쪽 변경과 합쳐 편집기에 넣고 false를 돌려준다 (저장하지 않는다. 합친 글을 확인하고 다시 저장한다).
  // 겹친 곳이 있으면 고르는 화면을 연다. 그대로면 true. 읽지 못하면(연결 끊김) 확인 없이 true (변경은 기기에 보관했다가 보낸다)
  // publish: 메모로 저장하려는지 (초안만 저장할 때는 메모가 바뀌었어도 상관없다. 초안은 메모를 덮지 않는다)
  async function catchUp(text, { publish }) {
    const id = question.id;
    const latest = await call('latest', { id });
    if (!editor || id !== question.id) return false;
    if (!latest || latest.failed) return true;
    const theirs = latest.draft || null;
    if ((theirs?.at || 0) !== session.draftAt) {
      // 다른 곳에서 이 초안을 저장했다
      if (theirs) return mergeIn(session.saved, text, theirs.text, '다른 곳에서 저장한 초안', { base: theirs.base, draftAt: theirs.at, saved: theirs.text });
      // 다른 곳에서 이 초안을 메모로 저장했다 (초안은 메모가 되며 지워진다)
      if (latest.memo !== session.base) return mergeIn(session.saved, text, latest.memo, '다른 곳에서 저장한 메모', { base: latest.memo, draftAt: 0, saved: latest.memo });
      // 다른 곳에서 이 초안을 지웠다 (메모는 그대로다). 초안 저장이면 초안을 다시 만들고, 저장이면 메모를 바꾸므로 다르게 묻는다
      const again = await confirmDialog({
        title: '다른 곳에서 이 초안을 지웠어요',
        body: !publish
          ? '지금 쓴 글로 초안을 다시 저장할까요? 저장한 메모는 그대로예요.'
          : willCommit(text)
            ? `지금 쓴 글을 메모로 저장하고 ${study.repo}에 커밋할까요?`
            : '지금 쓴 글을 메모로 저장할까요?',
        yes: publish ? '메모로 저장' : '초안 다시 저장',
        no: '계속 쓰기',
        tone: 'primary',
      });
      if (!again || !editor) {
        editor?.focus({ keepSelection: true });
        return false;
      }
      session = { ...session, draftAt: 0, saved: memoText() };
      return true;
    }
    if (publish && latest.memo !== session.base) {
      return mergeIn(session.base, text, latest.memo, '다른 곳에서 저장한 메모', { ...session, base: latest.memo });
    }
    return true;
  }

  // 세 갈래로 합친다 (memo-merge.js). next: 합친 뒤의 세션 (다른 쪽 글이 새 기준이 된다)
  function mergeIn(base, mine, theirs, theirsLabel, next) {
    const result = mergeMemo(base, mine, theirs);
    const previous = session;
    session = next;
    if (result.clean) {
      if (result.text === mine) return true; // 다른 쪽 변경이 이미 들어 있다. 그대로 저장한다
      editor.setText(result.text);
      renderDraftState();
      renderSaveButton();
      editor.focus({ keepSelection: true });
      toast(`다른 곳에서 고친 내용을 합쳤어요. 확인한 뒤 다시 저장해 주세요.${overLimitNote(result.text)}`);
      return false;
    }
    showConflict(result.parts, theirsLabel, previous);
    return false;
  }

  // 겹친 곳 고르기: 메모 창의 글 자리에 잠깐 연다. 취소하면(뒤로 가기도) 합치기 전의 세션으로 돌아간다
  // (그대로 다시 저장하면 다시 겹친 것을 알려준다)
  function showConflict(parts, theirsLabel, previous) {
    closeMenu();
    const el = editor.el;
    const hide = () => {
      conflict?.el.remove();
      conflict = null;
      el.classList.remove('is-resolving');
    };
    conflict = UI.createConflictView({
      parts,
      theirsLabel,
      onDone(text) {
        hide();
        editor.setText(text);
        renderDraftState();
        renderSaveButton();
        editor.focus();
        toast(`고른 글을 합쳐 편집기에 넣었어요. 다듬은 뒤 다시 저장해 주세요.${overLimitNote(text)}`);
      },
      onCancel() {
        hide();
        session = previous;
        renderDraftState();
        renderSaveButton();
        editor?.focus({ keepSelection: true });
      },
    });
    el.classList.add('is-resolving');
    document.activeElement?.blur(); // 고르는 동안에는 자판을 내린다
    el.querySelector('.win-head').after(conflict.el);
    el.querySelector('.win-paper').scrollTop = 0;
    conflict.focus();
  }

  // 초안 저장 (더보기, 닫을 때 묻는 창, 자판의 Ctrl+S): 메모는 그대로 두고 초안만 계정에 저장한다. 메모 창은 열어 둔다.
  // 저장했거나 저장할 것이 없으면 true
  let draftSaving = null;

  function saveDraftNow() {
    draftSaving ??= (async () => {
      if (!editor || saving || conflict) return false;
      const text = editorText();
      if (!text) {
        editor.setDraftState('빈 글은 초안으로 저장하지 않아요');
        return false;
      }
      if (text === session.saved) {
        editor.setDraftState(session.draftAt ? '초안 · 바뀐 내용 없음' : '바뀐 내용 없음');
        return true;
      }
      if (blockTooLong(text)) return false;
      editor.setDraftState('초안 저장 중…');
      if (!(await catchUp(text, { publish: false }))) return false;
      const id = question.id;
      const result = take(await call('saveDraft', { id, text, base: session.base }), id);
      if (!result.draft) {
        // 글은 위에서 확인했으니, 기준이 된 저장된 메모가 MEMO_MAX자를 넘는다 (이 제한이 생기기 전에 저장한 긴 메모).
        // 메모로 저장하면 기준이 지금 글로 바뀌어 다시 초안을 저장할 수 있다
        renderDraftState();
        toast(`저장된 메모가 ${Core.MEMO_MAX.toLocaleString()}자를 넘어 초안으로 저장할 수 없어요. 메모로 저장해 주세요.`);
        return false;
      }
      if (!editor || id !== question.id) return result.saved;
      session = { ...session, draftAt: result.draft.at, saved: result.draft.text };
      editor.setDraftState(result.saved ? '초안을 저장했어요' : '초안 · 이 기기에 보관했어요 · 연결되면 올려요');
      renderStatus();
      renderSaveButton();
      return true;
    })().finally(() => {
      draftSaving = null;
    });
    return draftSaving;
  }

  // 저장: 초안을 메모로 만들고 GitHub에 커밋한다 (초안은 지워진다).
  // shortcut: 자판의 Ctrl+Enter로 저장했는지 (저장 버튼이면 false)
  async function save({ shortcut = false } = {}) {
    if (draftSaving) await draftSaving; // 방금 누른 초안 저장이 끝난 뒤에 저장한다
    if (!editor || saving || conflict) return;
    closeMenu();
    const text = editorText();
    const memo = memoText();
    const draft = hasDraft();
    if (!text) {
      // 비우고 저장하면 지우는 것이므로 지울지 묻는다
      if (memo) removeMemo();
      else if (draft) removeDraft();
      else closeEditor();
      return;
    }
    // 바뀐 것도, 지울 초안도 없으면 닫는다. 아직 올리지 않은 메모면 저장으로 커밋한다
    if (text === memo && !draft && !study?.uncommitted) {
      closeEditor();
      return;
    }
    if (blockTooLong(text)) return;
    // Ctrl+Enter는 쓰다가 잘못 누르기 쉬우므로 GitHub에 커밋할 때는 한 번 더 묻는다 (저장 버튼은 일부러 누르는 것이라 묻지 않는다)
    if (shortcut && study?.canCommit && !(await confirmCommit())) return;
    saving = true;
    editor.setBusy(true);
    const ok = await catchUp(text, { publish: true });
    if (!ok || !editor) {
      saving = false;
      editor?.setBusy(false);
      renderSaveButton();
      return;
    }
    const id = question.id;
    // 초안을 메모로 저장했으니 초안은 지운다 (다른 곳의 초안을 합쳤다면 그 초안도)
    take(await call('saveMemo', { id, text: editorText(), dropDraft: hasDraft() }), id);
    saving = false;
    if (id === question.id) closeEditor({ fresh: true });
  }

  async function confirmCommit() {
    const yes = await confirmDialog({
      title: 'GitHub에 커밋할까요?',
      body: `${study.repo}의 ${question.id}.md로 커밋하고 메모 창을 닫아요. 메모는 그대로 두고 초안으로만 저장하려면 ${UI.MOD}+S를 누르세요.`,
      yes: '커밋',
      no: '계속 쓰기',
      tone: 'primary',
    });
    if (!yes) {
      editor?.focus({ keepSelection: true });
      return false;
    }
    return Boolean(editor);
  }

  // 메모 창을 닫아도 되는지 묻고 닫는다 (←, 뒤로 가기, 앱이 다른 화면을 열려 할 때).
  // 저장하지 않은 변경이 있으면 초안으로 저장할지, 버릴지 묻는다 (브라우저의 페이지 이탈 경고와 같다). 닫았으면 true
  async function leaveWindow() {
    if (!editor) return true;
    if (saving) return false;
    closeMenu();
    if (conflict) conflict.el.querySelector('[data-action="conflict-cancel"]').click(); // 고르던 것은 버리고 합치기 전의 글로 묻는다
    if (!hasUnsaved()) {
      closeEditor();
      return true;
    }
    if (!editorText()) {
      const discard = await confirmDialog({ title: '쓰던 내용을 버릴까요?', body: '저장하지 않은 변경은 사라져요.', yes: '버리기', no: '계속 쓰기' });
      if (!discard || !editor) return !editor;
      closeEditor();
      return true;
    }
    const answer = await confirmDialog({
      title: '저장하지 않은 변경이 있어요',
      body: '초안으로 저장하면 메모는 그대로 두고, 다음에 이 기기나 PC에서 이어 쓸 수 있어요.',
      yes: '초안 저장하고 닫기',
      no: '계속 쓰기',
      alt: '변경 버리기',
      tone: 'primary',
    });
    if (!editor) return true;
    if (answer === 'alt' || (answer === 'yes' && (await saveDraftNow()))) {
      closeEditor();
      return true;
    }
    return false;
  }

  async function requestClose() {
    if (!editor || saving) return;
    if (!(await leaveWindow())) editor?.focus({ keepSelection: true });
  }

  // ←와 뒤로 가기: 펼친 목록을 닫고, 겹친 곳을 고르던 중이면 고르기를 그만두고, 아니면 메모 창을 닫는다
  function windowBack() {
    if (!editor) return;
    if (menu) closeMenu();
    else if (conflict) conflict.el.querySelector('[data-action="conflict-cancel"]').click();
    else requestClose();
  }

  window.memoBack = windowBack;

  // 앱이 질문 화면을 떠나려 할 때 (알림으로 다른 질문 열기 등) 메모 창부터 닫아도 되는지 묻는다. 답은 leave로 알린다
  window.requestLeave = async function (seq) {
    const left = await leaveWindow();
    if (!left) editor?.focus({ keepSelection: true });
    tell('leave', { seq, left });
  };

  // 초안 지우기 (더보기, 본문의 '초안 삭제'): 메모는 그대로다
  async function removeDraft() {
    if (saving) return;
    closeMenu();
    const yes = await confirmDialog({
      title: '초안을 삭제할까요?',
      body: '이 초안이 지워져요. 저장한 메모는 그대로예요.',
      yes: '삭제',
      no: '취소',
    });
    if (!yes) {
      editor?.focus({ keepSelection: true });
      return;
    }
    const id = question.id;
    saving = true;
    editor?.setBusy(true);
    take(await call('deleteDraft', { id }), id);
    saving = false;
    if (id !== question.id) return;
    if (editor) closeEditor();
    else renderMemo();
    renderStatus();
    toast('초안을 삭제했어요.');
  }

  // 메모 지우기 (더보기, 작성 칸을 비운 채 저장): 초안도 함께 지운다.
  // GitHub에 커밋하는 계정이면 저장소의 {id}.md 파일도 지운다. 지난 커밋은 저장소 기록에 남는다
  async function removeMemo() {
    if (!editor || saving) return;
    closeMenu();
    const draftToo = hasDraft() ? ' 초안도 함께 지워져요.' : '';
    const yes = await confirmDialog({
      title: '메모를 삭제할까요?',
      body: study?.canCommit
        ? `이 질문의 메모가 지워지고, ${study.repo}의 ${question.id}.md 파일도 삭제돼요. 지난 커밋 기록은 GitHub에 남아요.${draftToo}`
        : `이 질문의 메모가 지워지고 되돌릴 수 없어요.${draftToo}`,
      yes: '삭제',
      no: '취소',
    });
    if (!yes) {
      editor?.focus({ keepSelection: true });
      return;
    }
    const id = question.id;
    saving = true;
    editor?.setBusy(true);
    take(await call('saveMemo', { id, text: '', dropDraft: hasDraft() }), id);
    saving = false;
    if (id === question.id) closeEditor();
  }

  // GitHub 커밋만 실패했던 메모를 다시 올린다. 메모만 올리고, 쓰던 초안은 그대로 둔다 (dropDraft를 주지 않는다)
  async function recommit() {
    const memo = memoText();
    if (!memo) return;
    const id = question.id;
    take(await call('saveMemo', { id, text: memo }), id);
    if (id === question.id && !editor) renderMemo();
  }

  // ---------- 메모 창의 더보기 ----------
  // 가끔 쓰는 것: 초안 저장, 초안 삭제, 메모 삭제. 위 막대의 ⋮ 아래로 펼친다 (앱의 펼침 목록과 같은 모양)

  function toggleMenu(button) {
    if (menu) {
      closeMenu();
      return;
    }
    const text = editorText();
    const items = [
      { label: '초안 저장', hint: '메모는 그대로 두고 저장해요', enabled: Boolean(text) && hasUnsaved(), run: saveDraftNow },
      { label: '초안 삭제', hint: '저장한 메모는 그대로예요', enabled: hasDraft(), run: removeDraft, danger: true },
      {
        label: '메모 삭제',
        hint: study?.canCommit ? 'GitHub의 파일과 초안도 함께 지워져요' : '초안도 함께 지워져요',
        enabled: Boolean(memoText()),
        run: removeMemo,
        danger: true,
      },
    ];
    const list = make('div', 'memo-more win-menu');
    list.setAttribute('role', 'menu');
    list.setAttribute('aria-label', '더보기');
    for (const entry of items) {
      const option = make('button', `memo-more-item${entry.danger ? ' is-danger' : ''}`);
      option.type = 'button';
      option.setAttribute('role', 'menuitem');
      option.disabled = !entry.enabled;
      option.append(make('span', 'memo-more-label', entry.label), make('span', 'memo-more-hint', entry.hint));
      option.addEventListener('click', () => {
        closeMenu();
        entry.run();
      });
      list.append(option);
    }
    // 목록 밖을 누르면 닫는다. 목록을 눌러도 글 칸의 초점(자판)은 그대로 둔다
    const shade = make('div', 'win-shade');
    shade.addEventListener('click', closeMenu);
    for (const el of [list, shade]) el.addEventListener('mousedown', (e) => e.preventDefault());
    editor.el.append(shade, list);
    button.setAttribute('aria-expanded', 'true');
    menu = { list, shade, button };
  }

  function closeMenu() {
    if (!menu) return;
    menu.list.remove();
    menu.shade.remove();
    menu.button.setAttribute('aria-expanded', 'false');
    menu = null;
  }

  // ---------- 본문의 메모 칸 ----------

  memoBody.addEventListener('click', (e) => {
    const action = e.target.closest('[data-action]')?.dataset.action;
    if (action === 'memo-write' || action === 'draft-resume') openEditor();
    else if (action === 'memo-recommit') recommit();
    else if (action === 'draft-delete') removeDraft();
    else if (!action && e.target.closest('.memo-view.is-collapsed') && !e.target.closest('a, button') && String(getSelection()) === '') {
      // 접힌 메모는 어디를 눌러도 펼친다 (펼치기 버튼을 조금 빗나가도 된다). 메모 창은 '편집'으로만 연다
      memoBody.querySelector('.memo-view-toggle')?.click();
    }
  });

  memoEdit.addEventListener('click', openEditor);
})();
