# Changelog

All notable changes to **Claude Java Toolkit** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> 🤖 **v4.4.0+ 부터는 [release-please](https://github.com/googleapis/release-please-action) 가 자동 갱신합니다.** Conventional Commits (`feat:`/`fix:`/`docs:`) 형식으로 push 하면 master 브랜치의 이 파일과 GitHub Releases 페이지가 자동으로 업데이트됩니다. 직접 편집은 v4.3.x 이전 항목에만 적용하세요.

릴리즈별 상세 배경 + 기술적 의사결정은 [README.md](./README.md) "버전 히스토리"
섹션을 참조하세요. 이 파일은 변경사항의 **빠른 참조용 요약**입니다.

---

## [4.4.0](https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.3.0...v4.4.0) (2026-04-22)


### ✨ Features

* [#8](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/8) 감사 로그 페이지네이션 + [#10](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/10) API Rate Limiting ([11345bb](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/11345bbb615b00cf5c29a41e1f25f2a5e3d65b21))
* 11개 이슈 일괄 수정 — 사용자 관리, 권한, 프롬프트, 필터, 비교 UI ([35409e0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/35409e0cafa4983061549f3e856340e8a86a4aa1))
* 2FA 로그인 검증 — ADMIN 계정 Google OTP 강제 적용 ([54e2ff0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/54e2ff04fec5cc20580fae20805ddcebd12889e3))
* **batch:** persist batch history to H2 DB + history section in batch page ([1db91b9](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/1db91b9fc0eb9118b822a0d8696a38b568b24df0))
* **batch:** 배치 이력 상세에서 항목별 분석 결과 직접 조회 + 문서 최신화 ([0020a60](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/0020a600843781e95c86b630ca8085528b8ed44e))
* **chat:** also grep Oracle SP/Func/Pkg/Trigger sources via ALL_SOURCE ([51bb36e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/51bb36e43b583a7627bc5e7786e3b872a84e95f0))
* **chat:** live progress indicator while AI context grep runs ([c7bacb7](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c7bacb74bf5983c8cf991adb8430b6f20a3b9503))
* DB 자동이관 UI + 감사로그/사용자관리 API 수정 ([8c94a96](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/8c94a96a0ce3712b8f90a690d56ae93070143c78))
* dependency progress bar + batch source selector + multi-email + batch log ([095d4ab](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/095d4ab9ba8ced912cd70ba9d715ec3327d7ea25))
* Docker에 호스트 프로젝트 폴더 마운트 지원 ([74cab25](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/74cab2520b64626badcceed86647790551ba0d05))
* Docker에서 Windows 경로 자동 변환 — Settings에 원래 경로 그대로 입력 ([540b8aa](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/540b8aaf8e9910b26d8b0a16d3ac74c35f20f7a9))
* **flow:** multi-DML filter + Phase 4 (history/share) + Phase 5 (metrics/patterns/docs) ([29d9a9a](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/29d9a9af036d2aa6ae89390268bd9db6240c9f24))
* **flow:** Phase 1 — backend data-flow trace engine (table/SP → UI) ([b1a2c44](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/b1a2c445c6f57f5d1bf490b768c0d158c1632b59))
* **flow:** Phase 2 — LLM narrative + SSE streaming over Phase 1 trace ([0254149](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/025414953c768db7b0494fea3d9b58cedb6f65fc))
* **flow:** Phase 3 — frontend page with ReactFlow + node detail drawer ([f90841d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/f90841d5e801eddf5e29f96871c2d8d7c6910556))
* **flow:** warmup MiPlatform on boot + always describe both ERP & DB SP paths ([16c5813](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/16c5813d18cfbdb3f3ecafea9c99dbd0b8b9521d))
* GitHub Pages 랜딩 페이지 추가 (docs/index.html) ([fa60284](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/fa60284afb6f2fd8cf27ff1bc128742915ad4c8f))
* Group 5 — 멀티유저 RBAC + 팀 설정 공유 + 공유 링크 ([724c7d1](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/724c7d1ff587d3b03f63ab31b0cbd65a77b91d5f))
* Group 6 — Docker/외부DB/설치마법사/헬스체크 + 감사로그 개선 ([feaa64e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/feaa64e9366a30bf67d36501ab9c26f92e70feef))
* Group 7 — 외부 연동 (Slack/Teams, GitHub PR, Jira, Git Diff) ([9e0aa58](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/9e0aa589bcbe39a11652b1eac2effd09d869fe47))
* Group 8 사용량 API + 스캔 Docker 경로 수정 + README/index 갱신 ([3078aae](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3078aae841b4319fa6075d01a5bc4eb5452a252d))
* **home:** Hero 위젯에 ERP / DB 상태 표시 (Server / API Key 와 동일 스타일) ([9680147](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/968014713ec266f13307edf3cf5cbb097f3dac3d))
* REST API Playground 페이지 추가 (/api-docs) ([886265c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/886265c348c55d43a05c747525c2f70bc60e255c))
* Settings 페이지에서 Claude API 키 런타임 입력/저장 지원 ([6f1d5a8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6f1d5a89a20e6297a38cfb444ef7a29850aaea7d))
* Settings에 Slack/Teams 웹훅 + Jira 연동 설정 추가 ([fc03700](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/fc037008553656c94789b6f0240643895e8b652e))
* SQL 인덱스 시뮬레이션 권한 분리 + Workspace 통합 ([5fd08a8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/5fd08a827faa99a34cfbf4ecb27bedc14c92a6ef))
* **v0.8.0:** Oracle 실행계획 트리 시각화 기능 추가 ([8363373](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/836337311399ccfaa4939cafbdbd9fdc7868b070))
* v0.9.0 — ERD→DDL 역변환, 실행계획 Before/After 비교, 이력 재실행 ([c9f8718](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c9f87187f120d72f23f233bf2ccb871dfb3e11a0))
* v1.0.0 GitHub Actions CI/CD 파이프라인 추가 + README 정리 ([f96d21c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/f96d21c0245816ff09b52f22c166e748c3fa25a0))
* v1.0.0 REST API 모드 구현 — 외부 CI/CD 파이프라인 연동용 JSON API ([750e844](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/750e84467ac5b11e309d22a250ac0c334a748aa0))
* v1.1.0 프롬프트 템플릿 관리 + 분석 결과 내보내기 ([4baf58e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4baf58e3ad12f2fc7a006e4aa44b103775128b17))
* v1.2.0 — SQL 성능 히스토리 대시보드 + 배치 SQL 분석 ([d3ad7c4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d3ad7c44c37cb90719634daa7b90a1f0231a5420))
* v1.3.0 — 테마 색상, 인쇄/PDF, 공유 링크, 사용량 모니터링, 분석 스케줄링 ([e7d747f](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e7d747f3c543a1b9801d6892cd497709bb88b679))
* v1.4.0 — 실행계획 즐겨찾기, SQL최적화 적용, 이메일 알림, DB프로필, 홈 위젯, 글로벌 검색 ([e2f4faa](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e2f4faa8472d077e3ac54dccee37d6def2bd2fce))
* v1.5.0 — 실시간 스트리밍 실행계획 분석 + SQL 리팩터링 Diff 뷰 ([fb357f8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/fb357f8c2445b93059fc25fcbaa23650d1a39f84))
* v1.6.0 — 코드 리뷰 하네스 (Analyst→Builder→Reviewer 파이프라인 + Diff 뷰) ([6fe7d0e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6fe7d0e831b75edb1e31ecd1f1498c279cd4b7e7))
* v1.6.0 — 하네스 소스 선택기 추가 (Java 파일 브라우저 + DB 오브젝트 브라우저 + WAS 시작 캐시) ([c5af724](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c5af724a52c8f054e49d026b731d4978a42cadfd))
* **v1.7.0:** implement features 3-11 — quality scores, batch analysis, dependency, dashboard, templates, export, schedule ([4b4a232](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4b4a232cb2011965244687a630cd016a9382a2ca))
* **v1.9.0:** SQL번역·ROI리포트·보안설정·민감정보마스킹·Harness Verifier 완성 ([2bad29d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/2bad29dcd807487d2e7bc5ea7cc104fa37ff9519))
* **v2.0.0-Group4:** 통합 워크스페이스 + 플러그인 아키텍처 ([c1cedde](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c1ceddee8e6b909ac261e8df6ce356978a0718f6))
* v2.2.0 — 비밀번호변경/세션타임아웃/입력검증/백업복원/API키분리 ([4730953](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4730953005f57cd837de85eff72ee7efc90919cd))
* v2.3.0 — 대시보드 위젯 + 2FA Google OTP + 워크스페이스 세션 타이머 ([e70f1b7](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e70f1b76a0a24367a620a14598b72b032fb353da))
* v2.4.0 Phase 1 — 보안 강화 (AES 암호화, 비밀번호 정책, 세션 보안) ([38d8d1d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/38d8d1db14c3e3085285ccaade3b4002b4bc3e94))
* v2.4.0 Phase 2 — 안정성/버그 수정 (Exception Handler, Logger, 타임아웃) ([8778725](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/8778725b07ccd30028c378a72e3634a3e5f79f2c))
* v2.4.0 Phase 3 — UI/UX 대규모 개선 (토스트, breadcrumb, 검색, 스켈레톤 등 9개) ([10a7ee9](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/10a7ee9ed91dcb371ab5b8adccd5328b18be6b9f))
* v2.4.0 Phase 4 — 기능 개선 (CodeMirror, 댓글, 알림, 캐싱) ([1720a99](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/1720a99dd7773fd579cf7652f3f0f7e9e754c5fe))
* v2.4.0 Phase 5 — 새 기능 (AI 채팅, 팀 대시보드, 감사 시각화, 비용 추적) ([7abdc45](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/7abdc45dc8034fe6ffee0eb395cb78cb17c32ad8))
* v2.5.0 안정성 + v2.6.0 보안 강화 + 감사 로그 KST/기능 메뉴 ([5f74adb](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/5f74adbd64c6d41b1cd7b7de45d887ef40634559))
* v2.7.0 — AI 채팅 Markdown/세션 관리 + WebJars 번들링 + 캐시 DB 영속화 ([b27ee18](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/b27ee189598fb42d5f96271dfe2b9c8812343815))
* v2.8.0 — 모니터링 및 UX (SSE 알림/헬스 대시보드/단축키/하단 네비) ([afbcfd7](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/afbcfd7794db73c001c4d924a406ae2a2179dde3))
* v2.9.0 — 협업 & 아키텍처 (분석→채팅 연계 + 팀 리뷰 + DB 마이그레이션) ([d14258f](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d14258fddeb3b232dafad1a4ef3c29e7250cd298))
* v2.9.5 — 분석 파이프라인 오케스트레이터 + DB 자동 마이그레이션 ([f7e1775](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/f7e1775a9c56ed77217dd2cdac4c9d671ec82157))
* v3.0.0 — 파이프라인 고도화 (스케줄링 + Monaco + 플로우차트 + 병렬 실행) ([47e052e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/47e052e6a45586016ba06133d69a9984c89b7635))
* v4.0.0 — React 프론트엔드 전환 (Thymeleaf → React 18 + TypeScript + Vite) ([73ee510](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/73ee5109f11914ec40d2a275d2a489e37563b001))
* v4.0.1 — Thymeleaf 제거 + REST API 전환 + 프론트엔드 고도화 ([2b52ae0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/2b52ae077903c44221aabd3f4d8e4e0fc61d2388))
* v4.0.3 — E2E 테스트, PWA, i18n, recharts, ErrorBoundary, 번들 최적화 ([4e340bb](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4e340bbf0e9044cef1b4bbda99fc953f49bb7dee))
* v4.1.0 — 전수 검토 반영: 누락 페이지 12개 + 특수 페이지 + 파일 업로드 ([6b680e2](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6b680e2c3c2895b366823737c39c260ef43c97c2))
* v4.1.0 — 최종 정리: 2FA, 설정 페이지, E2E, Maven/Docker 검증 ([37f8c95](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/37f8c95ef9beb24fdd18d0c3fb493dc9c7aa4eae))
* v4.2.0 — 운영 안정화 + UX 개선 (권한 연동, 비주얼 빌더, SSE 재연결) ([30a12bd](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/30a12bd540b6c7c51ef14146a04e43ede27240f6))
* v4.2.1 — Rate Limit/API 한도 전면 수정 (DB 영속화 + 확장 적용) ([7b5e1a3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/7b5e1a3601611564b3d2f1945a1b8a55b65c6ba0))
* v4.2.7 — 현실 버그 정리 + 보안 강화 + 운영 편의성 (Phase 1~6) ([d75efeb](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d75efeb118932321e1a40e1e35b4253cc03734cc))
* v4.2.8 — 공유 링크 / 커맨드 팔레트 / 팀 활동 / 엔드포인트 통계 / 질문 리포머 외 (8개 기능) ([133c439](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/133c439ba8f424670304419a33f8aba4c7f6da43))
* v4.3.0 Phase 1 — SARIF / Excel 내보내기 추가 ([9936ba1](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/9936ba12d082ee74029e9d73f276c333ac6ac3e5))
* v4.3.0 Phase 2 — Prometheus + Grafana 모니터링 스택 ([150c8d1](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/150c8d1b20a3c2875475da9b7750eb9c689a07f8))
* v4.3.0 Phase 3 — AI 모델 비용 옵티마이저 + SQL 인덱스 시뮬레이션 ([d45f464](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d45f464ea9e519c9928e4b4e326632d20d8b388d))
* v4.3.0 Phase 4 — 다국어 확장 + 대시보드 위젯 커스터마이징 ([ead7e21](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/ead7e21a158dd78a80350d32401eae1b30ea22c5))
* v4.3.0 Phase 5 — 비주얼 워크플로 빌더 개선 + Helm Chart ([dfc230e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/dfc230ec290cfec2405c3ec1fadc4bdcafbf688d))
* v4.3.0 ToolkitMetrics 호출처 통합 ([9866371](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/9866371406795d9ad89879b120d233b0b6c3f28e))
* **v4.4.0:** [#4](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/4) 자체 구축 에러 모니터링 (Sentry-style) ([7fa0b98](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/7fa0b98c97e40b4a0e9692fb7c0e8088fe9ba864))
* **v4.4.0:** [#5](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/5) Helm Chart 실 환경 검증 인프라 (Kind 자동화) ([64446cb](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/64446cbfd27cb15c89e58ff5f3a933587453c241))
* **v4.4.0:** [#6](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/6) OpenAPI 자동 생성 (Swagger UI + JSON 스펙) ([3f75dda](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3f75dda19200da56888b29231e93b3f6894ae2f5))
* **v4.4.0:** [#8](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/8) 메트릭 통합 보강 — 신규 5종 + Grafana 4 패널 ([556cca9](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/556cca938f27ae86dc2e9800b7edcec21339edc5))
* **v4.4.0:** UX 개선 3종 + 스크린샷 11개 추가 ([dede82b](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/dede82b3902691b2a781b904a2899595a2112276))
* **workspace:** 소스 선택기·이메일 발송·CSS/UX 개선 ([d2d26c9](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d2d26c9e4807c4e017304de22a4e06e988e839bb))
* 감사 로그에 사용자ID + 액션유형 추가 + 필터링 UI ([6517bb4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6517bb4fa84dc08da67d876f0adcf40793399900))
* 개인 사용자 설정 페이지 (/account/settings) ([3fb6f67](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3fb6f67cf0f8b173dc668842a2a09bfc9193be1b))
* 리뷰 워크플로우 (승인/거절 + 대댓글 + 알림) + UX 정리 (v4.2.3) ([43369ae](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/43369ae9ff99164d78fa878a531b58a63191840e))
* 리뷰 이력 대시보드 페이지 신규 (승인/거절/대기 % + 차트) ([b006627](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/b0066272b4326787169b2ea7a7637980ffbed892))
* 사용자별 API 키 관리 UI + 세션 타이머 UI ([f6a1f16](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/f6a1f1607e2bff54b1482e4240ddf263f022613f))
* 사용자별 API 키 실제 적용 — 분석 실행 시 개인 키 사용 ([56ab054](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/56ab054a6548c79ccbd2a5eb11a6055eba43aef4))
* 사용자별 기능 권한 실제 적용 — 사이드바 + URL 접근 제어 ([4d4d97e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4d4d97e377f12b9b61633e0cc7bb3a70a4e58c45))
* 설치 마법사 API키/DB 저장 기능 + README/index 빌드·배포 가이드 ([eccfd35](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/eccfd35c72f82cf18ee46030c3c388178d960e27))
* 우측 상단에 로그아웃 버튼 자동 삽입 ([65af9f1](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/65af9f11a8f51b946ef6559a41a578867389efbe))
* 워크스페이스 이메일 발송 팝업 모달 + 다중 수신자 지원 ([6f4c272](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6f4c272af529899e98a5db17427b8239545982f8))
* 워크스페이스 전체 이메일 발송 + 이력 상세 팝업 + PDF 다운로드 ([65970ab](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/65970abfa87bc7884739ea43633fd90e96a8ce0e))
* 코드 리뷰 하네스 4단계 탭 분리 + 단계별 복사/MD/PDF/이메일 ([8a40d64](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/8a40d64f5a6c5a4b4a7cbff7107d9c8bbdb470e5))
* 코드 리뷰 하네스 4단계 파이프라인 + 소스 선택 기능 구현 ([ed81c76](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/ed81c765947dc887df2c4fe3953a07bd849955ff))
* 통합 워크스페이스 다중 선택 병렬 실행 복원 ([77f20f3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/77f20f33278e7038ccb7d663bda9d70b5e206bd3))
* 파이프라인 UX 개편 (스트리밍/탭/복사 fix) + 분석 결과 이메일 발송 ([c0ff3cd](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c0ff3cd84b4335dfe44d390a7aaaf09e0792bf73))
* 파이프라인 비주얼 빌더 — 버튼으로 분석 단계 조립 ([5668193](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/566819392a61ae57c12076f4d7b7d57d89aa7694))
* 하네스 Builder 단계 이어쓰기(continuation) 적용 — 개선 코드 중간 잘림 근본 해결 ([0d60160](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/0d601605c301467b2c3a2beeb05d8b7b653f9ad3))
* 하네스 파이프라인을 3단계 분리 호출로 전환 (응답 잘림 근본 해결) ([0395b7e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/0395b7eb90a1e2148a738e0820dd18aeebc0d6ea))


### 🐛 Bug Fixes

* /history/{id}/review-status CSRF 누락 — 승인/거절 실패 버그 ([841198b](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/841198b86ed1eb59b282db349a29af28604c892f))
* 2FA QR코드 깨짐 수정 + 색상 테마 프리셋 연동 ([4aa9550](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4aa95502d950729844f6d0823893d5ff4354c748))
* 2FA 페이지 MIME 타입 오류 + JAR 내 index.html 읽기 오류 수정 ([35e24a3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/35e24a33235f04def7221bebbc094579705d6cd9))
* 2FA 페이지 redirect:/login 문자열이 body로 반환되는 버그 수정 ([5d74397](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/5d74397339668dbdbe3711873b316b1ca4059189))
* 4개 인터셉터에 /assets/** 제외 추가 — JS/CSS 모듈 로드 차단 해결 ([e553ec0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e553ec0922f76d05857b31a3f1cba56bacb2909e))
* 6가지 운영 이슈 — 분석오류/워크스페이스/ROI/사이드바/DB전환/소스필터 ([0433ce4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/0433ce4f32337046439d6dfa7d70c3baa98be5ed))
* 7가지 운영 이슈 — 계정/비밀번호/감사로그/DB이관/파이프라인/TLS ([091ea36](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/091ea36d7556d6109e7f41c4371f9bde3b50fbaf))
* **admin:** /admin/endpoint-stats 페이지 toLocaleString 오류 방어 ([c21bd06](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c21bd06e4fb68a2f011febf94d1231206a39fe4c))
* AppUser rate limit 컬럼 nullable로 변경 — 기존 DB 마이그레이션 호환 ([5439f3f](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/5439f3f699026809a4c8641694a96cef5a62f760))
* authStore.login() 이 disabledFeatures 를 fetch 안 해서 권한 OFF 메뉴가 보이던 버그 ([66b68a8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/66b68a830a6f62a32d65469302f86a5c088d9045))
* auto-translate Windows paths to container mount paths in Linux Docker ([69431d0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/69431d0d6db2625e3ba1005cace24609f668bb04))
* **chat:** rank-then-pick AI context grep so SQLMAP files aren't crowded out ([c7c1230](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c7c12300392fb0960ff8df523ebdba652e5de842))
* **chat:** 말풍선 복사 버튼 정상 동작 + 토스트 + 3초 체크 아이콘 ([befc3da](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/befc3da99b5bd4fc9bb0427e35c8ad829b744351))
* Docker H2 DB 시작 오류 — 비호환 옵션 제거 ([49708bd](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/49708bde2e2dc01f8b153fb47f51cab9881798f4))
* Docker JAR 실행 오류 — spring-boot-maven-plugin repackage 명시 ([382c62a](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/382c62aec954276b2573caff89889db0d1f1d42f))
* Docker 실행 오류 — JAR manifest + compose depends_on 수정 ([ed5dd26](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/ed5dd2618281c425c66d0ff4f13c027c0bb69151))
* Dockerfile JDK 11 → JDK 8 변경 (JDK 1.8 전용 서비스) ([3af5763](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3af57631b6e769feda279dceac6e5e3990d3cc9e))
* Dockerfile에서 JAR 검증 스텝 제거 (Spring Boot 풀 부팅 시도로 빌드 멈춤) ([d0d06b8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d0d06b856e4cab9d89c68031d6e9991dbe75ba7a))
* **docs:** GitHub Pages 갤러리 이미지 깨짐 수정 (경로 22개) ([63b9829](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/63b9829b7e43810347fef231394ea462a6f66865))
* ensure DB/file caches are populated from saved Settings on first boot ([cf2678c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/cf2678c01be1a4fa9fb2a3deeae382c5fb589670))
* explain/index.html SpEL null→boolean 변환 오류 수정 ([51911d0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/51911d0b11a4f9d6ffae47984d27c858123196f4))
* **flow:** allow CSRF-free POST to /flow/** so 분석 시작 works ([e15e78e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e15e78e5b762c5c884203b5c9e325fe8ad0a8f01))
* **flow:** correct shapeOf() return-type usage in buildMermaid ([c2f6fc1](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c2f6fc191cc5da921b9e4f87c5c53ad1ac0e12d9))
* GET /login/2fa 매핑 충돌 해결 (TwoFactorController vs SpaForwardController) ([e721caa](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e721caa47a6c9d9301c437d224a6e788b1a56276))
* H2 DB 데이터 보존 강화 + DefaultAdmin 안전성 개선 ([30069f6](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/30069f6d80044569513e90e81f7b98cd7882c5b8))
* **harness:** DB cache stays empty after WAS restart even though DB Set: OK ([1acebc9](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/1acebc9ac740588089af5670d34911b60b014d77))
* JDK 1.8 호환성 — orElseThrow() Supplier 명시 (Java 10+ 무인수 메서드 사용 불가) ([45a20df](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/45a20df8f5c4bf35055bc2d38b6b52b1fae07050))
* MySQL/PostgreSQL 드라이버 버전 명시 (빌드 오류 해결) ([42fdbe3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/42fdbe31ece409277d43b1882e95002ccd84d064))
* ORA-01882 timezone + 워크스페이스 복사/PDF + 파이프라인 에러 표시 ([84dd2e4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/84dd2e4a1135e3ace3be523be3243d9ab13b9c06))
* ORA-29900/ORA-06553 DEPTH 충돌 — 3-tier fallback 방식으로 전면 재작성 ([a731ffc](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/a731ffc83875eb7890ea0ad08f89b493dcda6322))
* Oracle 자동 이관 URL — SID/Service Name 포맷 자동 감지 ([3fd05f8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3fd05f8210f27c879888c929ab2fcd335896a029))
* OracleDbHealthIndicator 가 Tomcat 워커 스레드를 80초씩 점유하던 버그 ([8ee7323](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/8ee7323bbf981234a6f063a9dd714c89435b248a))
* Phase 1 컴파일 오류 2건 수정 ([934a20d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/934a20d5ebbb7a3273fa4ea3bc2dbe6c01649bea))
* prompts/index.html Thymeleaf th:onclick 보안 오류 수정 ([397f867](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/397f86764717d93472353f9edd6842f677a145ce))
* rate limit 컬럼 DEFAULT 0 + 자동 스키마 마이그레이션 ([13aeff4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/13aeff4eddfde38e63dafe83441e6d07f8635618))
* React 에셋 MIME 타입 오류 해결 — 빌드 출력을 static/ 루트로 변경 ([3b48d27](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3b48d27c24100433d0b9a4ddc1deebac50452ad5))
* resolveHostPath 접근 제한자 static → public static ([f86a439](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/f86a439d14cf94c2713b347c6259b10ccc7efa80))
* REST API Playground - 멀티라인 SQL JSON 파싱 오류 수정 ([0e15437](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/0e154376f994f41797571fd84e9037e7433471a1))
* SPA 라우팅 404 오류 해결 — ErrorController + 직접 index.html 서빙 ([6165eb3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6165eb3d01fe04526d2331dfe74d672915e5e25d))
* SSE 멀티라인 데이터 손실 근본 수정 + 워크스페이스 에러 상세 표시 ([e0c7aab](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e0c7aab3beb81506d600ac7c51ed0a8614159941))
* TLS handshake_failure 근본 원인 — JAVA_OPTS 오버라이드 + Alpine JDK8 ([205d1de](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/205d1de64284caa1ef45f518969488fbe5af9c6f))
* TwoFactorInterceptor에 /assets/** 제외 — 2FA 페이지 JS 로드 차단 해결 ([60f38ca](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/60f38ca58412eb27fd77d1158dd384604a89e238))
* **ui:** prompts.html CSS 통일 + workspace 스트리밍/소스선택/모델비교 개선 ([1bed3f3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/1bed3f358eaef03ede0b6d4099752e4c17642b7e))
* UserService.updateInfo 오버로드 파라미터 불일치 수정 ([4acb6f3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4acb6f34ccb1df638cd162f78c4617ff2d04fc53))
* **v1.7.0:** FavoriteRepository @Param fix, harness cleanup, dependency source selector, docs update ([fcc24b7](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/fcc24b75135afa1c854d105c520a3e2adb4dfb21))
* v2.4.1 — 로그인/Jackson/SSE 버그 수정, AI 채팅 스트리밍 개선, 권한 관리 보완 ([0e96ae2](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/0e96ae26f11ffb187feab36062bd81865f05a179))
* v2.7.0 webjar 의존성 해결 (빌드 실패 수정) ([0181d2d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/0181d2d9d8efcd822761e9f4c83cfef7efd121ff))
* **v4.4.0:** 3가지 운영 이슈 수정 (엔드포인트통계 / 사용량 / AI채팅 컨텍스트) ([653af16](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/653af16bc98ac1aacdf6329ae86f41156171fd5c))
* **v4.4.0:** AI 채팅 헤더 줄바꿈 + Docker startup readiness 보강 ([58f9caf](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/58f9caf12aabbd3805322c340996be9eb1a47db4))
* **v4.4.0:** StartupReadiness JDK 프록시 이슈 해결 (Spring Boot 시작 실패) ([59526c1](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/59526c1dc805b0d94468cfefa18b3e11b141c2d5))
* **WorkspaceController:** LinkedHashMap 타입 파라미터 오타 수정 ([a4b4b0c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/a4b4b0c962af2da2658cc40cfe4ada56af911e7e))
* 공유 링크 JSON 직접 표시 버그 + 품질 대시보드 드릴다운 모달 ([5092805](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/5092805e69f2631d4b5abe42357424fc48898eb9))
* 권한 감사 Round 2 + 에러 응답 표준화 (Item 5 & 6) ([3d58e14](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3d58e14e9c48da9fa0fded461d215561f7249551))
* 권한 관리 토글 무한 리렌더 버그 + DB 마이그레이션 테이블 누락 보강 ([fe50307](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/fe50307ddaa67a6610f39505d840333a9a455f60))
* 다수 런타임 오류 일괄 수정 ([77d2d56](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/77d2d56c5ff3451f6150b518ec6750cd0238fb24))
* 대규모 버그 수정 + UI 개선 + 소스 선택기 일괄 적용 ([d0c824f](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d0c824f02bea38d67d19fbb997327738cf41b325))
* 대시보드 위젯 토글 버그 + IndexAdvisor Settings DB 사용 ([b9b5320](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/b9b5320dfb2a9383dee56b0af54906ec7efcd5ba))
* 로그아웃 버튼 레이아웃 + 캐시 무효화 + GET 로그아웃 허용 ([59d1cf8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/59d1cf891dc83e4aaf01c23b4945914462f3c7f2))
* 로그인 302 오류 해결 + 비밀번호 보안 강화 ([d320af1](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d320af18369bab37894a7237fcbeae21fb865ef4))
* 리뷰 대시보드를 사용자 권한 관리 토글에 등록 (3곳 누락) ([7c45f6d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/7c45f6db13f81221e1860ae410fcf4109207ea5b))
* 리뷰이력 저장 누락 + 파이프라인 실시간 스트리밍 (진행적 저장 + 폴링) ([7fbb5e0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/7fbb5e095a30665bd6f91ccb1a6367b290fd0299))
* 분석 파이프라인 결과가 리뷰 이력에 저장되지 않던 버그 ([d73e77a](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d73e77a44a4b5d72767442197975a2ebc8711b8d))
* 빈 CLAUDE_BASE_URL 환경변수가 baseUrl 기본값을 덮어쓰던 문제 ([36428b4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/36428b4d5ddc9413ec310f5ef8ebd72d4a32a14b))
* 사용자 권한 관리 — 전체 메뉴 35개로 확대 ([632416a](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/632416a6470b7058a82010167101868089540881))
* 사용자 권한 관리 features 목록 동적 로드 (하드코딩 이중 관리 제거) ([2ab274c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/2ab274c88c375a85c6f7afaa625c02ccca943a52))
* 사용자 권한 관리 페이지 Thymeleaf 파싱 오류 수정 ([ab0a47d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/ab0a47df25d852700c78fe5b7adf08cdaae13cec))
* 사이드바 SECTION_PATHS admin 배열 누락 경로 보완 ([a8ae3d7](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/a8ae3d7003ba5eb79f4de8cd885fa05431f6f1ee))
* 사이드바 스크롤 위치 저장/복원 — 메뉴 클릭 후 위치 유지 ([8b4d0b5](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/8b4d0b5bab24f9b4e510a7ad654f357dc32abf5a))
* 설치 마법사 전면 수정 — CSRF 면제 + 이메일 입력 + Forbidden 해결 ([2dea832](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/2dea8324ff4abee4d7d5e0f9a9597524c6d8cf61))
* 소스 선택기 파일/DB 오브젝트 미표시 — 캐시 자동 갱신 ([9d0b024](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/9d0b0243f43edb4af105f8472d83d8f4a633d5ff))
* 시스템 헬스의 DB 정보가 실제 운영 DB 를 반영하도록 수정 ([edbe5b2](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/edbe5b21f0fc6616c369ae2b6b70a38caf7ccced))
* 실행계획 UDF 바인딩 오류 자동 재시도 + 메인 화면 카드 추가 ([c1ed7f8](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c1ed7f86345f72eae6727341efd21634d22a5791))
* 실행계획 트리 undefined 오류 — JSON.parse로 Thymeleaf inline JS 파싱 수정 ([be2b91f](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/be2b91f68214788c96f120a98139db36bfcf2b69))
* 워크스페이스 스트림 CompletableFuture→Thread 전환 + 프롬프트 textarea 확대 ([d75e152](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d75e152af526dcac47626be0bb4a63ff2ab3d50f))
* 워크스페이스 스트림 연결 오류 + SQL번역 파싱 실패 근본 수정 ([ae18f8c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/ae18f8cae448826195976caaae951cea155b402b))
* 워크스페이스 스트림 오류 근본 수정 + 프롬프트 textarea 확대 ([363e8ec](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/363e8ecb110e1641deeb5edd7c85f02c78985868))
* 워크스페이스 스트림 오류 근본 원인 — AuditLogFilter가 SSE 차단 ([84b5b2e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/84b5b2e2b36f18f5842fc048a2b13697baee356e))
* 워크스페이스 스트림을 SseStreamController 동일 패턴으로 단순화 ([645b973](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/645b973b00d37f6639ccd3a3ec1fefecacd5e9ad))
* 전수 검토 — 12개 API 불일치 일괄 수정 ([9a6841f](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/9a6841f8b35ddc2d4117abf98722c8feeeaf02e7))
* 정규식 생성기 예시 버튼이 동작 안 하던 버그 (입력칸 직접 채우기로 변경) ([57f29c0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/57f29c0688cb4031f80ab1f9dd5308a0eaea5204))
* 최종 안정화 — DbProfiles + Dashboard API + Interceptor 보완 ([ccb56c4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/ccb56c410a00c781b23ee9f2a26dfb858ad90e4c))
* 통합 워크스페이스 SQL 선택시 소스선택에서 Java/DB 둘 다 보이도록 수정 ([d7cf8f0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d7cf8f078b9136371a829488f037758e8a93fd1d))
* 파이프라인 Broken pipe 로그 폭주 + max_tokens 잘림 자동 이어받기 ([22c89c6](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/22c89c6bc4932200f0de6a695532ec9c1d771b4d))
* 파이프라인 CSRF 누락 + 워크스페이스 복사 버튼 시각 피드백 ([3367ca6](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3367ca60b70cf6e9b8267806b93a70899f07e8bd))
* 파이프라인 단계 간 컨텍스트 전파 누락 (SQL 최적화 풀 스택 step2/3 빈 결과) ([a0f0e14](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/a0f0e14204b80641d3759243a84ee6d2bc68dc31))
* 프로젝트 파일 스캔 중 'Java 파일 없음' 오해 — 진행 상태 표시 + 자동 폴링 ([b3cbc68](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/b3cbc68794c3bb1cf7a261688f4e63012f1f6d41))
* 프롬프트 템플릿 페이지 Thymeleaf 3.0 파싱 오류 수정 ([ec12674](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/ec126742f5253f47d140f0189d22cd249106cded))
* 하네스 소스 선택기 3가지 버그 수정 ([afcff5e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/afcff5eb07bba4ed9baa680daf1f184ec2fecdfe))
* 하네스 소스 선택기 추가 버그 수정 (DB SQL 문법 + UI 개선) ([4147c9d](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4147c9d8c12081e3c96289aca770e9fc880dba61))
* 하네스 응답 중간 잘림 수정 (max_tokens 오버라이드) ([05298cb](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/05298cb9e0414571a8f381ca8ff12626a0a0eac5))


### 📚 Documentation

* **ci:** release-please 사전 요구사항 명시 (PR 생성 권한) ([11c71d3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/11c71d33bf37f44f2a27e02a3af2862a261274d2))
* docs/index.html + app index.html 최신화 (v2.4.0) ([d5df1a4](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/d5df1a4cf67f1d235b1d039c37dd4a04d5762faf))
* **index.html:** 카드 섹션 압축 — 30 → 14 카드 (53% 감소) ([1beb609](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/1beb6093796c00884ad5777d3f279953fb027dad))
* README + docs/index.html v4.2.4~6 갱신, docs 의 (v버전) 라벨 제거 ([6725d1e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6725d1e26b7885e2dcf4148a79c7128aa23d7ae0))
* README + docs/index.html v4.3.0 최신화 ([cd5222c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/cd5222c56cf8fbee736ef339cfed301e0687fcc2))
* README v1.8.0 헤더 (현재) 라벨 제거 ([2c76e50](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/2c76e50a69aa8e584ddee3b46e0a9bf54d06f9f3))
* README v2.0.0 최신화 + index.html 유틸리티 도구 섹션 추가 ([c64c065](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/c64c065dd6c253d3f9a2b8598ad466891d01009d))
* README v2.2.0 완료 + index.html 관리 도구 섹션 추가 ([73825e5](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/73825e50592f5f26d6d4b31b09a7b6161880fb9b))
* README v4.0.0 섹션 헤더 (진행 중) → (완료) 수정 ([1c22918](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/1c22918f9e193ecd6c3904b214ac59ce73b64298))
* README v4.2.7 섹션 Phase 5.2+6 보강 + docs/index.html v4.2.7 기능 카드 5개 추가 ([e860020](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/e860020f0a9c6773e9981d65b20827b76565fca6))
* README 압축 — 2274 → 1737 줄 (24% 감소, 약 540 줄 절약) ([9089996](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/9089996d7b4421e9806528014c76ea2427f1ded7))
* README 정리 — 배포 가이드 추가 + 구조 개선 ([f754fb7](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/f754fb793c4a02130d1af4dc317910264a71cded))
* README 최상단에 GitHub Pages 소개 페이지 링크 추가 ([4da3b52](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/4da3b52604d523ffb869f412da23f96211b6b133))
* v1.2.0 완료 처리 + v1.3.0 로드맵 추가 ([3f537ef](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/3f537ef948c307d1cb7ebf7daf41d17979f0e2e0))
* v1.7.0 로드맵 확장 및 v1.8.0 계획 추가 ([76cad23](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/76cad23eadfba537fd86a47e3a854f5c4fd2e795))
* v2.4.0 README 완료 + index.html 신규 기능 카드 추가 ([8f2e45c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/8f2e45c2945a0cc38dedca08971600e1d4fe3061))
* v2.5.0 ~ v2.7.0 기능 반영 (docs/index.html) ([00f13ef](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/00f13ef6531a8abe8e8bfa2b25a086a55c975459))
* v4.3.0 로드맵 추가 + 운영 안정성 개선 (예외 로깅) ([04226d0](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/04226d0bee641544ba9798b4b9248fab4ae19288))
* **v4.4.0:** [#7](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/7) Quick Demo 스크린샷 인프라 (사용자 캡처 가이드) ([701994e](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/701994ecacd806e3dcbf1223844641499af6c9a6))


### ♻️ Refactoring

* v4.0.2 — Thymeleaf 완전 제거 + 컨트롤러 정리 + Monaco/Mermaid 통합 ([89eeb0c](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/89eeb0cc60ad6002a098fea1494114f71e268814))
* 프롬프트 템플릿 UI 전면 개편 ([90df507](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/90df5070a3cefa925fe02a7a181d0b16de2b7bbe))


### 🧪 Tests

* **v4.4.0:** [#2](https://github.com/Sangmoo/Claude-Java-Toolkit/issues/2) JaCoCo + 56개 단위 테스트 추가 ([67a6167](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/67a6167ecd10ece94f702e6cff354754082ad1ba))


### 📦 Build System

* mvn clean package BUILD SUCCESS (JDK 1.8, 1:04) ([6165eb3](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/6165eb3d01fe04526d2331dfe74d672915e5e25d))
* tsc clean, vite build ✓. ([16c5813](https://github.com/Sangmoo/Claude-Java-Toolkit/commit/16c5813d18cfbdb3f3ecafea9c99dbd0b8b9521d))

## [Unreleased] — v4.4.0 진행 중

### Added (✨ Features)
- **OpenAPI / Swagger UI** — `/swagger-ui.html` (ADMIN 전용) + `/v3/api-docs` JSON 스펙
- **자체 구축 에러 모니터링 (Sentry-style)** — `/admin/error-log` 페이지 + dedupe + 자동 unresolved 복귀
- **메트릭 통합 5종 신규** — 캐시 히트/미스, SSE 동시 연결 게이지, 하네스 4단계 Timer, 에러 발생률 카운터, 파이프라인 단계 카운트
- **Helm Chart Kind 검증 인프라** — `scripts/test-helm.sh` 자동화 + `VALIDATION.md` 5 시나리오
- **Helm Chart 안정성 강화** — `startupProbe` (5분 시작 허용) + `JAVA_OPTS` 옵션
- **자동 변경 로그** — release-please-action v4 GitHub workflow

### Changed (♻️ Refactoring)
- HomePage 위젯 토글 버그 수정 — 숨긴 위젯이 layout 에서 사라지지 않도록 보존
- 도구 카드 그리드 위젯 — 12개 카탈로그에서 사용자 임의 선택 + 순서 변경 모달
- **Hero 위젯 인사말/부제 사용자 편집 가능** — `{name}` 토큰 치환 + 시스템 상태 라인 토글 + 미리보기
- **언어 스위처 UI 개선** — 드롭다운 배경 불투명화 (var(--bg-card) → var(--bg-secondary)) + 즉시 적용 (window.location.reload)
- **하네스 헤더 줄바꿈 방지** — SSE 청크 flush 정책 변경 (마지막 \n 까지만 flush) → "## 📋" 만 잘려 렌더링되던 문제 해결
- IndexAdvisorService — Settings 외부 DB 우선 사용 (실패 시 H2 fallback)
- IndexAdvisorPage — Monaco Editor + 대상 DB 표시 + 샘플 SQL 드롭다운
- AdminEndpointStatsPage — 백엔드 error 응답 시 toLocaleString TypeError 방어

### Documentation (📚)
- **스크린샷 11개 추가** — `docs/screenshots/` (홈/SQL/파이프라인/하네스 + v4.3/v4.4 신기능)
- README 의 "📸 데모" 섹션 + docs/index.html "화면으로 보기" 갤러리 자동 활성화

### Tests (🧪)
- JaCoCo 0.8.11 플러그인 + 커버리지 리포트 자동화
- 6 신규 테스트 클래스 / 56 단위 테스트 케이스 (sqlindex, cost, metrics, export, pipeline, history, errorlog)
- ToolkitMetricsTest 신규 5 테스트 (캐시/Harness 단계/SSE/Error/Pipeline Step)

### Documentation (📚)
- `monitoring/METRICS.md` — Prometheus 메트릭 카탈로그 + PromQL + 권장 알람 5종
- `helm/claude-toolkit/VALIDATION.md` — 5 시나리오 (H2 / PostgreSQL / Ingress / HPA / ServiceMonitor) 체크리스트
- README — Kind 설치 (winget) + 자동 검증 스크립트 가이드

---

## [4.3.0] — 2026-04-17

### Added (✨ Features)
- SARIF 2.1.0 / Excel 워크북 내보내기 (IDE 연동 + 회의 자료 자동 생성)
- Prometheus + Grafana 모니터링 (`docker-compose --profile monitoring`)
- AI 모델 비용 옵티마이저 (Haiku/Sonnet/Opus 추천)
- SQL 인덱스 임팩트 시뮬레이션 (JDBC 메타조회 + DDL 추천)
- 다국어 5개 언어 (ko/en/ja/zh/de) + 사용자별 자동 동기화
- 대시보드 위젯 커스터마이징 (react-grid-layout + DB 영속화)
- 파이프라인 인터랙티브 그래프 (reactflow)
- Kubernetes Helm Chart (Deployment + Service + Ingress + HPA + ServiceMonitor)

### Changed
- DataRestController / AuthRestController silent catch → SLF4J 로깅 (~22 곳)
- SQL 인덱스 시뮬레이션 권한 분리 (`featureKey: 'index-advisor'`)

---

## [4.2.7] — 2026-04-15

### Security
- `/api/v1/admin/**` 역할 게이팅 누락 수정. 기존 `/admin/**` 규칙이
  `/api/v1/admin/**` 를 매칭하지 못해 VIEWER/REVIEWER 도 관리자 API 호출
  가능한 구멍이 있었음. `SecurityConfig` 에 `.hasRole("ADMIN")` 추가.
- `FavoriteController.delete` 소유자 체크 추가 — 본인 소유 아니면 HTTP 403.
- `ReviewHistoryController.delete` 역할 체크 추가 — VIEWER 는 HTTP 403.
- `ReviewCommentController.deleteComment` — 주석의 "본인 또는 ADMIN" 과
  구현 불일치 수정. ADMIN 도 삭제 가능.

### Fixed
- `/favorites/star` 중복 저장 — `(username, historyId)` 유니크 체크.
- 알림 SSE `onerror` fallback 폴링 — 기존 `setTimeout` 1회 → `setInterval`
  지속 폴링 + 재연결 시도.
- `toggleFavorite` 연속 클릭 경합 — `togglingIdsRef` 가드 추가.
- 알림 `EventSource` 가 이름 있는 `"notification"` 이벤트를 못 받던 버그 —
  `addEventListener('notification', ...)` 직접 구독으로 해결. 결과적으로
  종 아이콘 뱃지가 새 알림 즉시 증가하지 않던 문제가 해결됨.
- `markRead` 가 이미 읽은 알림을 다시 클릭해도 무조건 감산하던 버그 —
  `wasUnread` 체크 후에만 감산.
- `Favorite` 엔티티 `username` / `historyId` 컬럼 추가. 기존에 JPQL 쿼리가
  필드 부재로 예외 → catch 후 빈 배열 반환하여 **즐겨찾기 목록이 항상 비어
  보이던 치명적 버그** 수정.
- 하네스 "개선 코드" 탭 상단 `sql` 라벨 라인 제거 — `normalizeBuilderSection`
  정규식이 언어 이름만 있는 첫 줄을 제거.
- 하네스 중첩 펜스 정리 — Claude Builder 가 펜스/언어 라벨을 중복 출력해도
  프론트에서 하나의 단일 코드 블록으로 정규화.
- `OracleDbHealthIndicator` 가 요청 스레드에서 동기 프로브를 돌려 Tomcat
  워커를 최대 80초 점유하던 버그 — `@PostConstruct` + `@Scheduled(1h)` 로
  완전 분리. `health()` 는 캐시만 반환.
- `HarnessCacheService` 가 `ApplicationReadyEvent` 이후 백그라운드에서 캐시를
  채우던 구조 — `@PostConstruct` 로 전환하여 WAS 기동 전에 인라인 로드.
  사용자가 기동 직후 "소스 선택" 을 눌러도 "적용 중..." 이 뜨지 않음.

### Added
- **VIEWER 검토 대기 알림**: VIEWER 가 이력을 생성하면 REVIEWER/ADMIN
  전원에게 실시간 알림. `PendingReviewNotifier` 를 4개 저장 경로
  (`ReviewHistoryService.save` / `saveHarness` /
  `SseStreamController.saveHistory` / `PipelineExecutor`) 에 훅킹. 알림
  클릭시 `/review-requests?historyId=N` 로 이동해 해당 카드가 하이라이트됨.
- **댓글 `@멘션`**: `MentionInput` 공용 컴포넌트 (`@` 드롭다운,
  ArrowUp/Down/Enter/Tab/Esc), `/api/v1/users/mentions` 엔드포인트,
  `MENTION_PATTERN` 파싱 + 알림 발행. 이메일(`a@b.com`) 은 멘션으로 판정
  안 됨.
- **`ReviewActionDialog` 공용 다이얼로그**: 승인/거절 확정시 선택 코멘트
  입력 가능 (Ctrl+Enter / Esc). 모든 사용자가 읽을 수 있는 `ReviewNoteCard`
  로 피드백 표시.
- **이력 상세 모달** (팀 리뷰 요청 페이지): 리뷰어가 VIEWER 작성 본문을
  팝업으로 확인 + 모달 내에서 바로 승인/거절.
- **`HistoryPage` 다중 선택**: 체크박스 기반 내보내기 + 삭제 (canDelete).
- **`FavoritesPage` 검색 / 다중 선택 / 입력 표시**: 기존엔 검색도 없고
  본문이 비어 보이던 상태 → 완전 업그레이드.
- **알림 개별 / 전체 삭제**: 드롭다운 항목별 X 버튼 + 헤더 "전체 삭제".
  알림 제목 / 본문 / 시각을 계층적으로 표시.
- **이력/즐겨찾기 페이지네이션**: `?page=&size=` + `X-Has-More` 헤더.
  프론트 "더 보기 (+50)" 버튼.
- **리뷰 노트 표시**: `ReviewNoteCard` 로 모든 사용자에게 리뷰어 피드백을
  친화적으로 노출.

### Changed
- `toolkit.history.max` / `toolkit.favorites.max` 설정 외부화. 기본값 상향
  (100→200, 200→500). 환경변수 `TOOLKIT_HISTORY_MAX` /
  `TOOLKIT_FAVORITES_MAX` 로 오버라이드.
- 모달 공용 fade 애니메이션 (`modal-overlay-in` / `modal-body-in`) —
  `ReviewActionDialog` + 리뷰 상세 모달 적용.
- `utils/date.ts` 공통 유틸 추출 — `formatDate` / `formatRelative`. 기존
  중복 선언 제거.
- `utils/harnessMarkdown.tsx` 추출 — `CodeReviewPage.tsx` 에서 ~110줄 분리.
- `Dockerfile` `dependency:go-offline` 실패 로그 묵음 처리 + 이유 주석.
- 알림 드롭다운이 제목 / 본문 / 시각을 계층적으로 표시 (기존엔 message 만).

---

## [4.2.6] — 2026-04 (이전 릴리즈)

### Fixed
- `OracleDbHealthIndicator` 가 timeout 없는 JDBC 연결로 Tomcat 워커 80초씩
  점유하던 버그 (CONNECT_TIMEOUT/ReadTimeout/LoginTimeout 3초 + 60초 캐싱).
- 정규식 생성기 예시 버튼이 `clipboard.writeText` 만 호출해 HTTP IP 환경
  silent 실패 → `inputExamples` prop 으로 textarea 직접 채우기.
- `authStore.login()` 이 `disabledFeatures` 를 fetch 안 해서 권한 OFF 메뉴가
  사이드바에 보이던 버그.

### Changed
- 코드 리뷰 하네스 4단계 탭 분리 (`[[HARNESS_STAGE:N]]` sentinel 마커) +
  단계별 복사/MD/PDF/이메일 버튼.
- 리뷰 대시보드 권한 등록 3곳 누락 수정 (`AdminPermissionController` +
  `PermissionInterceptor` + `sidebarMenus.ts`).

---

## [4.2.5] — 2026-04

### Added
- 리뷰 이력 대시보드 페이지 (승인/거절/대기 % + 차트).
- 분석 파이프라인 결과가 리뷰 이력에 저장.

### Fixed
- `/history/{id}/review-status` CSRF 누락 — 승인/거절 실패 버그.
- 파이프라인 Broken pipe 로그 폭주 + max_tokens 잘림 자동 이어받기.
- 분석 파이프라인 결과가 리뷰 이력에 저장되지 않던 버그.

---

## [4.2.4] — 2026-03

### Fixed
- 파이프라인 단계 간 컨텍스트 전파 누락 (SQL 최적화 풀 스택 step2/3 빈 결과).

---

## [4.2.3] — 2026-03

### Added
- 리뷰 워크플로우 (승인/거절 + 대댓글 + 알림).
- 파이프라인 UX 개편 (스트리밍/탭/복사 fix) + 분석 결과 이메일 발송.

### Fixed
- 리뷰이력 저장 누락 + 파이프라인 실시간 스트리밍 (진행적 저장 + 폴링).
- 프로젝트 파일 스캔 중 'Java 파일 없음' 오해 — 진행 상태 표시 + 자동 폴링.
- 시스템 헬스의 DB 정보가 실제 운영 DB 를 반영하도록 수정.

---

## [4.2.2] — 2026-03

### Fixed
- 사내망 운영 이슈 정리 (TLS / DNS / Proxy 환경변수).

---

## [4.2.0] — 2026-03

### Added
- 운영 안정화 라운드 + UX 개선.

---

## [4.1.0] — 2026-02

### Changed
- Thymeleaf 템플릿 완전 제거. `claude-toolkit-ui/src/main/resources/templates/`
  디렉토리 및 `spring-boot-starter-thymeleaf` 의존성 삭제.

---

## [4.0.0] — 2026-01

### Added
- React 18 + TypeScript + Vite 기반 SPA 로 프론트엔드 전면 전환.
- 64개 React 페이지 + 공용 컴포넌트/훅/스토어 35+ 개.
- Phase 1 (프로젝트 셋업) → Phase 5 (React 루트 전환) 완료.

### Removed
- Thymeleaf 템플릿 69개 (~24,000 줄 HTML / ~15,000 줄 JS).

---

## 이전 버전

v3.x 이하의 자세한 릴리즈 히스토리는 [README.md](./README.md) 의
"버전 히스토리" 섹션을 참조하세요. 주요 마일스톤:

- **v3.x** — 사용자별 기능 권한 + 대시보드 + AES 암호화
- **v2.8** — SSE 실시간 알림 push (기존 60초 폴링 대체)
- **v2.4** — AI 채팅 + 분석 결과 댓글
- **v2.0** — 통합 워크스페이스 + 분석 파이프라인
- **v1.8** — 배치 리뷰
- **v1.0** — 초기 릴리즈 (SQL 리뷰 + 코드 리뷰 + Spring Boot Starter)

---

[4.2.7]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.6...v4.2.7
[4.2.6]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.5...v4.2.6
[4.2.5]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.4...v4.2.5
[4.2.4]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.3...v4.2.4
[4.2.3]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.2...v4.2.3
[4.2.2]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.2.0...v4.2.2
[4.2.0]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.1.0...v4.2.0
[4.1.0]: https://github.com/Sangmoo/Claude-Java-Toolkit/compare/v4.0.0...v4.1.0
[4.0.0]: https://github.com/Sangmoo/Claude-Java-Toolkit/releases/tag/v4.0.0
