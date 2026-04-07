---
name: ibus-orchestrator
description: "어린이집 버스 위치추적 서비스(ibus)의 에이전트 팀을 조율하는 오케스트레이터. 'ibus 개발해줘', '버스 추적 앱 만들어줘', '위치 추적 서비스 구현', '어린이집 버스 앱', 'Android 앱이랑 웹 페이지 개발해줘' 요청 시 사용. 후속 작업: '수정해줘', '다시 실행', '업데이트', '보완', 'QA만 다시', 'Android만 수정', '웹만 수정', '이전 결과 개선' 요청 시에도 반드시 이 스킬 사용."
---

# iBus Orchestrator

어린이집 등하원 버스 실시간 위치추적 서비스의 에이전트 팀을 조율하여 Android 앱 + 학부모 웹 페이지를 생성한다.

## 실행 모드: 에이전트 팀

## 에이전트 구성

| 팀원 | 에이전트 정의 | 역할 | 스킬 | 출력 |
|------|-------------|------|------|------|
| android-developer | `.claude/agents/android-developer.md` | Android 앱 + 위젯 | android-bustrack | `_workspace/android/` |
| web-developer | `.claude/agents/web-developer.md` | 학부모 웹 페이지 | web-bustrack | `_workspace/web/` |
| qa-inspector | `.claude/agents/qa-inspector.md` | 통합 정합성 검증 | qa-bustrack | `_workspace/qa_report.md` |

## 워크플로우

### Phase 0: 컨텍스트 확인

1. `_workspace/` 디렉토리 존재 여부 확인
2. 실행 모드 결정:
   - **`_workspace/` 미존재** → 초기 실행. Phase 1 진행
   - **`_workspace/` 존재 + 부분 수정 요청** → 해당 에이전트만 재호출
   - **`_workspace/` 존재 + 새 입력** → `_workspace/`를 `_workspace_{YYYYMMDD_HHMMSS}/`로 이동 후 Phase 1

### Phase 1: 준비

1. PRD 파일(`PRD_어린이집_등하원_버스_위치추적.md`) 읽기
2. `_workspace/` 디렉토리 생성
3. Firebase 기본 설정 파일 생성:
   - `_workspace/firebase/database-rules.json` — PRD 섹션 4.4 기반
   - `_workspace/firebase/db-structure.md` — PRD 섹션 4.3 기반, 양쪽 개발자 참조용

### Phase 2: 팀 구성

팀 생성:
```
TeamCreate(
  team_name: "ibus-team",
  members: [
    {
      name: "android-developer",
      agent_type: "android-developer",
      model: "opus",
      prompt: "PRD를 읽고 android-bustrack 스킬을 참조하여 Android 앱 전체를 구현하라.
               출력: _workspace/android/. Firebase 구조: _workspace/firebase/db-structure.md 참조.
               주요 모듈 완성 시마다 리더와 qa-inspector에게 알려라."
    },
    {
      name: "web-developer",
      agent_type: "web-developer",
      model: "opus",
      prompt: "PRD를 읽고 web-bustrack 스킬을 참조하여 학부모 웹 페이지를 구현하라.
               출력: _workspace/web/. Firebase 구조: _workspace/firebase/db-structure.md 참조.
               지도 페이지 완성 시 리더와 qa-inspector에게 알려라."
    },
    {
      name: "qa-inspector",
      agent_type: "qa-inspector",
      model: "opus",
      prompt: "qa-bustrack 스킬을 참조하여 Android와 Web 코드의 경계면을 교차 검증하라.
               PRD를 기준 문서로 사용. 모듈 완성 알림 시 즉시 해당 부분 검증.
               이슈 발견 시 해당 에이전트에게 SendMessage로 수정 요청.
               최종 리포트: _workspace/qa_report.md"
    }
  ]
)
```

작업 등록:
```
TaskCreate(tasks: [
  { title: "Android 차량 등록 화면", assignee: "android-developer" },
  { title: "Android Foreground Service + GPS", assignee: "android-developer" },
  { title: "Android Jetpack Glance 위젯", assignee: "android-developer" },
  { title: "Android 3시간 자동 종료", assignee: "android-developer" },
  { title: "Android Manifest + 빌드 설정", assignee: "android-developer" },
  { title: "Web 카카오맵 지도 페이지", assignee: "web-developer" },
  { title: "Web Firebase 리스너 + 실시간 갱신", assignee: "web-developer" },
  { title: "Web 차량 정보 카드 + 상태 표시", assignee: "web-developer" },
  { title: "Web Firebase Hosting 설정", assignee: "web-developer" },
  { title: "QA 경계면 교차 검증",
    assignee: "qa-inspector",
    depends_on: ["Android Foreground Service + GPS", "Web Firebase 리스너 + 실시간 갱신"] },
  { title: "QA 상태 전이 완전성 검증",
    assignee: "qa-inspector",
    depends_on: ["Android Jetpack Glance 위젯", "Web 차량 정보 카드 + 상태 표시"] },
  { title: "QA PRD 기능 준수 최종 점검",
    assignee: "qa-inspector",
    depends_on: ["QA 경계면 교차 검증", "QA 상태 전이 완전성 검증"] }
])
```

### Phase 3: 병렬 개발 + 점진적 QA

android-developer와 web-developer가 병렬로 개발한다.

팀원 간 통신 규칙:
- android-developer → web-developer: Firebase 데이터 형식 변경 시 SendMessage
- qa-inspector → android-developer/web-developer: 경계면 이슈 발견 시 양쪽에 SendMessage
- 모든 팀원 → 리더: 작업 완료 시 TaskUpdate

리더 모니터링:
- TaskGet으로 전체 진행률 확인
- 팀원이 막혔을 때 SendMessage로 지시 또는 작업 재할당

### Phase 4: 통합 검증

1. 모든 개발 작업 완료 대기
2. qa-inspector의 최종 검증 리포트 확인
3. FAIL 항목이 있으면 해당 에이전트에게 수정 요청 → 재검증 (최대 2회)

### Phase 5: 정리

1. 팀원들에게 종료 요청
2. 팀 정리
3. `_workspace/` 보존 (사후 검증용)
4. 사용자에게 결과 요약 보고:
   - Android 프로젝트: `_workspace/android/`
   - 웹 프로젝트: `_workspace/web/`
   - QA 리포트: `_workspace/qa_report.md`

## 데이터 흐름

```
[리더]
  ├─ _workspace/firebase/db-structure.md (공유 참조)
  │
  ├─→ [android-developer] ←SendMessage→ [web-developer]
  │         │                                  │
  │         ↓                                  ↓
  │    _workspace/android/              _workspace/web/
  │         │                                  │
  │         └──────── Read by ─────────────────┘
  │                      │
  │                [qa-inspector]
  │                      ↓
  │              _workspace/qa_report.md
  │
  └─── Read all → 최종 결과 보고
```

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| 팀원 1명 실패 | SendMessage로 상태 확인 → 재시작 또는 리더가 직접 처리 |
| QA FAIL 항목 | 해당 에이전트에게 수정 요청, 최대 2회 재검증 후 보고서에 명시 |
| Firebase 구조 변경 필요 | 리더가 db-structure.md 업데이트 후 양쪽에 브로드캐스트 |
| 타임아웃 | 현재까지 완성된 산출물로 부분 결과 보고 |

## 테스트 시나리오

### 정상 흐름
1. 사용자가 "ibus 개발해줘" 요청
2. Phase 1: PRD 분석, Firebase 기본 설정 생성
3. Phase 2: 3명 팀 구성, 12개 작업 등록
4. Phase 3: android/web 병렬 개발, qa 점진 검증
5. Phase 4: 최종 QA 전체 PASS
6. Phase 5: 팀 정리, 결과 보고
7. 산출물: `_workspace/android/`, `_workspace/web/`, `_workspace/qa_report.md`

### 에러 흐름
1. Phase 3에서 qa-inspector가 경계면 이슈 발견 (updatedAt vs timestamp 필드명 불일치)
2. qa-inspector → android-developer, web-developer 양쪽에 SendMessage
3. 양쪽 수정 후 qa-inspector 재검증 → PASS
4. Phase 4 최종 QA 통과
5. 정상 종료
