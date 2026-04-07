# iBus — 어린이집 등하원 버스 실시간 위치 추적 서비스

## 프로젝트 개요
어린이집 등하원 차량의 실시간 위치를 학부모가 웹에서 확인할 수 있는 서비스.
- 기사님: Android 홈 화면 위젯 (토글 ON/OFF)
- 학부모: 웹 페이지 (카카오맵 + 실시간 위치)
- 중계: Firebase Realtime DB

## 하네스: iBus 개발팀

**목표:** Android 앱 + 학부모 웹 페이지를 에이전트 팀으로 병렬 개발하고, 경계면 정합성을 QA로 보장

**에이전트 팀:**
| 에이전트 | 역할 |
|---------|------|
| android-developer | Kotlin + Jetpack Glance 위젯 + GPS + Firebase 연동 Android 앱 개발 |
| web-developer | 카카오맵 + Firebase 리스너 학부모 웹 페이지 개발 |
| qa-inspector | Android ↔ Firebase ↔ Web 경계면 교차 검증 |

**스킬:**
| 스킬 | 용도 | 사용 에이전트 |
|------|------|-------------|
| android-bustrack | Android 앱 개발 가이드 | android-developer |
| web-bustrack | 웹 페이지 개발 가이드 | web-developer |
| qa-bustrack | 통합 정합성 검증 체크리스트 | qa-inspector |
| ibus-orchestrator | 팀 전체 조율 | 리더 (트리거 스킬) |

**실행 규칙:**
- ibus 개발, 버스 추적 앱, 위치 추적 서비스 관련 작업 요청 시 `ibus-orchestrator` 스킬을 통해 에이전트 팀으로 처리하라
- 단순 질문/확인은 에이전트 팀 없이 직접 응답해도 무방
- 모든 에이전트는 `model: "opus"` 사용
- 중간 산출물: `_workspace/` 디렉토리

**디렉토리 구조:**
```
.claude/
├── agents/
│   ├── android-developer.md
│   ├── web-developer.md
│   └── qa-inspector.md
└── skills/
    ├── harness/              (메타 스킬)
    ├── android-bustrack/
    │   └── SKILL.md
    ├── web-bustrack/
    │   └── SKILL.md
    ├── qa-bustrack/
    │   └── SKILL.md
    └── ibus-orchestrator/
        └── SKILL.md
```

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-04-07 | 초기 구성 | 전체 | PRD 기반 하네스 신규 구축 |
