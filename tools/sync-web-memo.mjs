// 웹 서비스 저장소의 메모 화면 파일을 앱의 질문 화면(assets/viewer)으로 옮긴다. 웹에서 이 파일들을 고치면 다시 돌린다.
//   node tools/sync-web-memo.mjs [웹 저장소 경로]
// - memo-ui.js, memo-merge.js: ES 모듈의 import/export만 바꾼다. 받는 것은 window.MemoCore(앱의 memo-core.js)·window.MemoMerge에서,
//   내보내는 것은 window.MemoUI·window.MemoMerge로 한다 (WebView의 file:// 페이지에서는 모듈을 쓰지 않는다).
// - memo.css: 맨 위에 옮겨 왔다는 안내만 붙인다.
// - memo-core.js는 옮기지 않는다. 저장·커밋은 앱(Kotlin)이 하므로, 앱의 memo-core.js는 화면에 필요한 일부만 따로 옮겨 쓴 파일이다.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const WEB = process.argv[2] || 'C:/.workspace/projects/service/maeil-meil-reborn';
const OUT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../app/src/main/assets/viewer');

const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8').replace(/\r\n/g, '\n');
const write = (name, text) => fs.writeFileSync(path.join(OUT, name), text.replace(/\n/g, '\r\n'));

// import { a, b } from './memo-core.js'; → const { a, b } = window.MemoCore;
const GLOBALS = { './memo-core.js': 'window.MemoCore', './memo-merge.js': 'window.MemoMerge' };

function unmodule(name, global, note) {
  const exported = [];
  let code = read(name)
    .replace(/^import \{([^}]*)\} from '([^']+)';$/gm, (line, names, from) => {
      if (!GLOBALS[from]) throw new Error(`${name}: 옮길 수 없는 import ${line}`);
      return `const {${names}} = ${GLOBALS[from]};`;
    })
    .replace(/^export (const|function) (\w+)/gm, (_, kind, id) => {
      exported.push(id);
      return `${kind} ${id}`;
    });
  if (/^\s*(import|export)\b/m.test(code)) throw new Error(`${name}: 남은 import/export가 있어요`);
  code = code.replace(/\n+$/, '');
  return `${note}\n(function () {\n${code}\n\n  ${global} = { ${exported.join(', ')} };\n})();\n`;
}

write('memo-ui.js', unmodule(
  'memo-ui.js',
  'window.MemoUI',
  '// 웹 서비스 저장소의 memo-ui.js를 옮겼다 (메모 보기, 메모 편집기, 겹친 곳 고르기). 웹에서 고치면 tools/sync-web-memo.mjs로 다시 옮긴다.\n'
    + '// 바꾼 곳: ES 모듈 import/export 대신 window.MemoCore(memo-core.js)·window.MemoMerge(memo-merge.js)에서 받고 window.MemoUI로 내보낸다.',
));

write('memo-merge.js', unmodule(
  'memo-merge.js',
  'window.MemoMerge',
  '// 웹 서비스 저장소의 memo-merge.js를 옮겼다 (같은 메모를 두 곳에서 고쳤을 때 세 갈래로 합치기). 웹에서 고치면 tools/sync-web-memo.mjs로 다시 옮긴다.\n'
    + '// 바꾼 곳: ES 모듈 export 대신 window.MemoMerge로 내보낸다.',
));

write('memo.css', '/* 웹 서비스 저장소의 memo.css를 그대로 옮겼다 (메모 보기·편집기). 웹에서 고치면 tools/sync-web-memo.mjs로 다시 옮긴다.\n'
  + '   바탕 변수와 버튼(.btn)은 viewer.css에 있다. */\n\n' + read('memo.css'));

console.log(`옮겼어요: ${WEB} → ${OUT}`);
