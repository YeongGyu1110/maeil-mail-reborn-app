# 앱에 넣은 외부 파일

질문 본문과 메모를 웹 서비스와 똑같이 그리려고, 웹 질문 화면(`questions/index.html`)이 CDN에서 불러오는 파일을 같은 버전 그대로 앱에 넣었다 (`app/src/main/assets/viewer/lib/`). 인터넷 없이도 그릴 수 있다.

| 파일 | 버전 | 라이선스 | 받은 곳 |
|---|---|---|---|
| `marked.min.js` | marked 15.0.12 | MIT | cdn.jsdelivr.net/npm/marked@15.0.12 |
| `prism.min.js`, `prism-*.min.js` (12개) | Prism 1.29.0 | MIT | cdnjs.cloudflare.com/ajax/libs/prism/1.29.0 |
| `res/font/pretendard_*.otf` (Regular·Medium·SemiBold·Bold) | Pretendard 1.3.9 | SIL OFL 1.1 (`assets/licenses/pretendard-OFL.txt`) | cdn.jsdelivr.net/npm/pretendard@1.3.9/dist/public/static |

- 넣을 때 13개 파일 모두 웹 `questions/index.html`의 `integrity`(sha384) 값과 같은지 확인했다 (2026-11-21).
- 글꼴은 웹(`pretendard.min.css`, 1.3.9)과 같은 판이다. 앱 화면(Compose), 답변·메모 본문(WebView, `file:///android_res/font/`), 홈 화면 위젯이 같이 쓴다. 받은 4개 파일과 라이선스 파일의 크기·SHA-256이 jsDelivr 목록(data.jsdelivr.com)의 값과 같은지 확인했다 (2026-11-22). 라이선스(OFL)는 글꼴과 함께 앱에 넣어 두어야 한다.
- 웹에서 버전을 바꾸면 같은 버전으로 다시 받아 같은 방법으로 확인한 뒤 바꾼다.
