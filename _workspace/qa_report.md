# QA 검증 리포트

## 요약
- 검증 항목: 12개
- 통과: 12개 | 실패: 0개 | 미검증: 0개

## 경계면 검증 결과

### [PASS] 1. LocationService Firebase write shape vs db-structure.md
- 검증: Android LocationService가 Firebase에 쓰는 데이터 shape과 db-structure.md 스펙 비교
- Android 측: `LocationService.kt:199-204` — `mapOf("lat" to lat, "lng" to lng, "speed" to speed.toDouble(), "updatedAt" to ServerValue.TIMESTAMP)`
- DB 스펙: `vehicles/{vehicleId}/location/ { lat: Number, lng: Number, speed: Number, updatedAt: Number }`
- 결과: 일치. 4개 필드(lat, lng, speed, updatedAt) 모두 정확. ServerValue.TIMESTAMP 사용.

### [PASS] 2. app.js onValue 데이터 접근 shape vs db-structure.md
- 검증: Web app.js가 Firebase에서 읽는 데이터 접근 패턴과 db-structure.md 비교
- Web 측: `app.js:53-54` — `vehicle.location.lat`, `vehicle.location.lng`; `app.js:229` — `v.location.updatedAt`; `app.js:55` — `vehicle.status === 'active'`
- DB 스펙: `vehicles/{vehicleId}/location/lat`, `vehicles/{vehicleId}/location/lng`, `vehicles/{vehicleId}/location/updatedAt`, `vehicles/{vehicleId}/status`
- 결과: 일치. 중첩 접근 패턴(`vehicle.location.lat`) 사용, 평탄화 접근(`vehicle.lat`) 없음.

### [PASS] 3. Android vs Web Firebase 경로 1:1 대조
- 검증: 양쪽 코드의 Firebase 경로가 동일한지 비교
- Android 측: `MainActivity.kt:131` — `database.child("kindergartens").child(kindergartenId)`; `MainActivity.kt:168` — `database.child("vehicles").child(vehicleId)`; `LocationService.kt:206-207` — `database.child("vehicles").child(vehicleId).child("location")`; `LocationService.kt:215-216` — `database.child("vehicles").child(vehicleId).child("status")`
- Web 측: `app.js:350` — `db.ref('kindergartens/' + kindergartenId + '/name')`; `app.js:361` — `db.ref('vehicles')` with `orderByChild('kindergartenId').equalTo(kindergartenId)`
- 결과: 일치. 양쪽 모두 `kindergartens/{id}`, `vehicles/{id}/location`, `vehicles/{id}/status` 경로 사용.

### [PASS] 4. status 문자열 리터럴 "active"/"inactive" 일치
- 검증: Android와 Web에서 사용하는 status 문자열 값이 동일한지 비교
- Android 측: `LocationService.kt:216` — `setValue("active")`; `LocationService.kt:222` — `setValue("inactive")`; `MainActivity.kt:159` — 초기값 `"inactive"`
- Web 측: `app.js:55,139,225,270,290` — `vehicle.status === 'active'`; `app.js:227` — `isActive ? '운행 중' : '운행 종료'`
- 결과: 일치. 양쪽 모두 소문자 "active"/"inactive" 사용.

### [PASS] 5. ID 형식 (kg_, vehicle_) 통일
- 검증: kindergartenId, vehicleId 형식이 db-structure.md와 일치하는지 확인
- Android 측: `MainActivity.kt:125` — `"kg_${UUID.randomUUID()}"`; `MainActivity.kt:154` — `"vehicle_${UUID.randomUUID()}"`
- DB 스펙: `kindergartenId: "kg_" + UUID`, `vehicleId: "vehicle_" + UUID`
- Web 측: `app.js:23-24` — URL에서 kindergartenId 추출 (kg_* 형식 수신)
- 결과: 일치. 접두사 `kg_`, `vehicle_` + UUID 형식 통일.

### [PASS] 6. location 중첩 구조 확인 (평탄화 금지)
- 검증: location 데이터가 중첩 객체로 저장/접근되는지 확인
- Android 측: `MainActivity.kt:160-165` — `"location" to mapOf(...)`; `LocationService.kt:206-207` — `.child("location").setValue(locationData)`
- Web 측: `app.js:53-54` — `vehicle.location.lat`, `vehicle.location.lng`
- DB 스펙: "반드시 중첩 객체 (평탄화 금지)" — `vehicles/{id}/location/lat`
- 결과: 일치. 양쪽 모두 중첩 구조 사용, 평탄화 없음.

### [PASS] 7. 상태 전이 완전성 (ON/OFF/자동종료 3가지)
- 검증: 3가지 상태 전이 시나리오가 Android → Firebase → Web 전체 체인에서 올바르게 동작하는지 확인
- (1) Widget ON: `BusTrackWidget.kt:149-153` → `LocationService.kt:86(setStatusActive)` → Firebase status="active" → `app.js:55,227` → "운행 중" 녹색(#22c55e)
- (2) Widget OFF: `BusTrackWidget.kt:145-148` → `LocationService.kt:102(setStatusInactive)` → Firebase status="inactive" → `app.js:227` → "운행 종료" 회색(#9ca3af)
- (3) 자동종료: `LocationService.kt:39,91(3시간 타이머)` → `LocationService.kt:59-63(autoStopRunnable)` → stopSelf → onDestroy → setStatusInactive → Web 반영
- 결과: 일치. 3가지 전이 모두 양쪽에서 일관된 문자열과 동작.

### [PASS] 8. kindergartenId 필터링 쿼리 정확성
- 검증: Web에서 어린이집별 차량을 정확히 필터링하는지 확인
- Web 측: `app.js:361-362` — `vehiclesRef.orderByChild('kindergartenId').equalTo(kindergartenId)`
- Android 측: `MainActivity.kt:155` — `"kindergartenId" to kindergartenId` (차량 생성 시 kindergartenId 포함)
- DB 스펙: `vehicles/{vehicleId}/kindergartenId: String → kindergartens 참조키`
- 결과: 일치. orderByChild + equalTo 쿼리가 정확히 kindergartenId 필드를 기준으로 필터링.

### [PASS] 9. Firebase rules indexOn 존재 (수정 후 재검증 통과)
- 검증: Firebase 데이터베이스 규칙에 vehicles 하위 indexOn 설정이 있는지 확인
- DB 스펙: `vehicles` 하위에 `.indexOn: ["kindergartenId"]` 설정 필수
- Web 측: `app.js:362` — `orderByChild('kindergartenId')` 쿼리 사용 (인덱스 필요)
- 수정 전: database rules 파일 미존재 (FAIL)
- 수정 후: `database.rules.json` 생성 — `vehicles` 하위 `.indexOn: ["kindergartenId"]` 포함. `firebase.json`에 `"database": { "rules": "database.rules.json" }` 추가.
- 결과: 일치. web-developer가 수정 반영 완료, 재검증 통과.

### [PASS] 10. 마커 애니메이션 구현 확인
- 검증: GPS 데이터 수신 시 마커가 부드럽게 이동하는 애니메이션이 구현되었는지 확인
- Web 측: `app.js:90-132` — `animateMarker()` 함수. requestAnimationFrame 기반. ease-out cubic 보간(`easeOutCubic(t) = 1 - Math.pow(1 - t, 3)`). 1초 duration. lat/lng 동시 보간. 마커 + 오버레이 위치 동시 업데이트.
- PRD 3.2.3: "GPS 데이터 수신 시 마커 위치 부드럽게 이동 (애니메이션)"
- 결과: 일치. PRD 요구사항 충족.

### [PASS] 11. "운행 중인 차량 없음" 메시지 확인
- 검증: 운행 중인 차량이 없을 때 안내 메시지가 표시되는지 확인
- Web 측: `track.html:16-18` — `<div id="no-vehicles-overlay"><p>현재 운행 중인 차량이 없습니다</p></div>`
- Web 측: `app.js:259-281` — `updateNoVehiclesOverlay()` 함수. 차량 0대 또는 모든 차량 inactive 시 오버레이 표시.
- PRD 3.2.4: "현재 운행 중인 차량이 없을 때: '현재 운행 중인 차량이 없습니다' 메시지"
- 결과: 일치. 메시지 텍스트 정확히 일치, 조건 로직 올바름.

### [PASS] 12. 3시간 자동 종료 로직 확인
- 검증: 3시간 경과 시 자동 종료 및 알림이 구현되었는지 확인
- Android 측: `LocationService.kt:39` — `AUTO_STOP_DELAY_MS = 3 * 60 * 60 * 1000L` (10,800,000ms = 3시간)
- Android 측: `LocationService.kt:91` — `handler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)` (서비스 시작 시 스케줄)
- Android 측: `LocationService.kt:59-63` — autoStopRunnable → stopTracking() + showAutoStopNotification()
- Android 측: `LocationService.kt:100,226` — 수동 종료 시 `handler.removeCallbacks(autoStopRunnable)` (타이머 취소)
- Android 측: `LocationService.kt:236-237` — "운행이 자동 종료되었습니다 (3시간 경과)" 알림
- PRD 3.1.5: "운행 시작 후 3시간 경과 시 자동 OFF", "자동 종료 시 알림: '운행이 자동 종료되었습니다'"
- 결과: 일치. 3시간 타이머, 자동 종료, 위젯 갱신, 알림 모두 구현됨.
