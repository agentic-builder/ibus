---
name: qa-bustrack
description: "어린이집 버스 위치추적 서비스의 통합 정합성을 검증하는 스킬. Android ↔ Firebase ↔ Web 경계면 교차 비교, 데이터 shape 일치, 상태 전이 완전성 검증. 'QA 검증', '경계면 확인', '통합 테스트', '정합성 점검', '데이터 형식 확인' 요청 시 사용."
---

# QA BusTrack 검증 가이드

Android 앱, Firebase DB, 학부모 웹 간의 경계면을 교차 비교하여 데이터 정합성을 검증한다.

## 검증 방법: "양쪽 동시 읽기"

경계면 검증은 반드시 양쪽 코드를 동시에 열어 비교한다.

| 검증 대상 | 왼쪽 (생산자) | 오른쪽 (소비자) |
|----------|-------------|---------------|
| GPS 데이터 shape | LocationService의 Firebase write | app.js의 onValue 콜백 |
| Firebase 경로 | Android의 `database.child()` 호출 | Web의 `firebase.database().ref()` |
| 상태 값 | Android의 status 문자열 | Web의 `status ===` 비교 |
| 차량 ID | Android의 vehicleId 생성 | Web의 kindergartenId 필터링 |

## 검증 체크리스트

### 1. 데이터 shape 교차 검증 (최우선)

Android LocationService에서 Firebase에 쓰는 데이터 스키마:
```
vehicles/{vehicleId}/
  ├── kindergartenId  → String
  ├── name            → String
  ├── route           → String
  ├── status          → String ("active" | "inactive")
  └── location/
      ├── lat         → Number (double)
      ├── lng         → Number (double)
      ├── speed       → Number (float)
      └── updatedAt   → Number (timestamp)
```

Web app.js에서 Firebase로부터 읽는 데이터가 위와 정확히 일치하는지 확인한다.

주의할 불일치 패턴:
- `updatedAt` vs `timestamp` vs `lastUpdated` — 필드명 오류가 가장 흔한 버그
- `vehicle.location.lat` vs `vehicle.lat` — 중첩 구조 불일치
- `speed` 타입: Android는 float → Web에서 숫자로 처리하는지

### 2. Firebase 경로 일치 검증

Android 코드의 모든 `database.child()` 체인과 Web 코드의 모든 `ref()` 경로를 추출하여 1:1 대조한다.

### 3. 상태 전이 완전성

| 트리거 | Android 동작 | Firebase 변경 | Web 반영 |
|--------|-------------|--------------|---------|
| 위젯 ON | Service 시작 | status → "active" | 마커 녹색, "운행 중" |
| 위젯 OFF | Service 종료 | status → "inactive" | 마커 회색, "운행 종료" |
| 3시간 초과 | 자동 종료 | status → "inactive" | 마커 회색, "운행 종료" |
| GPS 수신 불가 | 마지막 위치 유지 | location 변경 없음 | 마지막 위치 표시 유지 |

모든 전이에서 Android가 쓰는 status 값과 Web이 비교하는 status 값이 동일 문자열인지 확인한다.

### 4. 어린이집/차량 등록 정합성

- Android에서 생성하는 `kindergartenId` 형식(`kg_` + UUID)과 Web URL의 kindergartenId가 동일 형식
- 같은 어린이집 이름으로 등록한 차량이 동일 kindergartenId에 연결
- Web의 `orderByChild('kindergartenId').equalTo()` 쿼리가 정확히 동작
- Firebase 규칙에 `vehicles` 하위 `.indexOn: ["kindergartenId"]` 설정됨

### 5. PRD 기능 준수

PRD 섹션 3.1, 3.2의 모든 기능 요구사항을 하나씩 대조하여 구현 여부를 확인한다.

## 리포트 형식

```markdown
# QA 검증 리포트

## 요약
- 검증 항목: N개
- 통과: X개 | 실패: Y개 | 미검증: Z개

## 경계면 검증 결과

### [PASS] 항목명
- 검증: {무엇을 비교했는지}
- 결과: {통과 근거}

### [FAIL] 항목명
- 검증: {무엇을 비교했는지}
- 결과: {불일치 상세}
- 수정 필요: {파일, 위치, 수정 방법}

### [SKIP] 항목명
- 사유: {미검증 이유 — 미구현, 접근 불가 등}
```
