---
name: web-bustrack
description: "어린이집 버스 위치추적 학부모 웹 페이지를 개발하는 스킬. 카카오맵 JavaScript API + Firebase Realtime DB 실시간 리스너 + 반응형 모바일 웹. '웹 페이지 만들어줘', '학부모 페이지', '지도 페이지', '카카오맵 연동', '실시간 위치 표시' 등 웹 측 개발 요청 시 사용."
---

# Web BusTrack 개발 가이드

학부모가 모바일 브라우저에서 버스 위치를 실시간으로 확인하는 웹 페이지를 개발한다.

## 프로젝트 구조

```
web/
├── index.html          // 메인 랜딩
├── track.html          // /track/{kindergartenId} — 실시간 지도
├── style.css           // 반응형 스타일
├── app.js              // Firebase + 카카오맵 로직
├── firebase.json       // Firebase Hosting 설정
└── .firebaserc         // Firebase 프로젝트 연결
```

## 핵심 구현 사항

### 1. 카카오맵 지도

```html
<script src="//dapi.kakao.com/v2/maps/sdk.js?appkey=YOUR_APP_KEY"></script>
```

- 초기 중심: 부산 에코델타시티 (35.1028, 128.9656)
- 줌 레벨: 4 (동네 단위)
- 차량 마커: 커스텀 이미지 (버스 아이콘) + 이름 라벨
- 마커 이동 애니메이션: `requestAnimationFrame`으로 현재 위치 → 새 위치 보간

### 2. Firebase Realtime DB 리스너

```javascript
// URL에서 kindergartenId 추출
const kindergartenId = window.location.pathname.split('/track/')[1];

// 어린이집 이름 표시
firebase.database().ref('kindergartens/' + kindergartenId + '/name')
  .once('value', (snap) => {
    document.getElementById('header-title').textContent =
      snap.val() + ' 등하원 차량 위치';
  });

// 해당 어린이집의 차량만 필터링
const vehiclesRef = firebase.database().ref('vehicles');
vehiclesRef.orderByChild('kindergartenId').equalTo(kindergartenId)
  .on('value', (snapshot) => {
    snapshot.forEach((child) => {
      updateMarker(child.key, child.val());
    });
  });
```

### 3. 차량 마커 업데이트

```javascript
function updateMarker(vehicleId, vehicle) {
  const { lat, lng } = vehicle.location;
  const isActive = vehicle.status === 'active';

  if (markers[vehicleId]) {
    animateMarker(markers[vehicleId], lat, lng);
  } else {
    markers[vehicleId] = createMarker(lat, lng, vehicle.name, isActive);
  }

  updateMarkerStyle(vehicleId, isActive);
  updateInfoCard(vehicleId, vehicle);
}
```

### 4. 차량 정보 카드

- 차량 아이콘 터치 시 하단 카드 표시
- 표시 항목:
  - 차량 이름 (예: "강서어린이집 버스")
  - 운행 구간 (예: "에코델타시티 운행")
  - 상태: "운행 중" (녹색) 또는 "운행 종료" (회색)
  - 마지막 업데이트: "30초 전", "5분 전" 등 상대 시간
- 상태 색상: 운행 중 = `#22c55e`, 운행 종료 = `#9ca3af`

### 5. 운행 종료/비운행 처리

- `status === "inactive"`:
  - 마커 회색 처리 (opacity 0.5)
  - 정보 카드에 "운행 종료" + 마지막 updatedAt 시각
- 모든 차량이 inactive:
  - 지도 중앙에 "현재 운행 중인 차량이 없습니다" 오버레이

### 6. Firebase Hosting 설정

```json
{
  "hosting": {
    "public": ".",
    "rewrites": [
      { "source": "/track/**", "destination": "/track.html" }
    ]
  }
}
```

## 경계면 주의사항

- Firebase에서 읽는 필드명(`lat`, `lng`, `speed`, `updatedAt`, `status`)은 Android가 쓰는 필드명과 정확히 일치해야 한다
- location은 중첩 객체 — `vehicle.location.lat` (평탄화 아님)
- status 비교는 `=== "active"` / `=== "inactive"` 사용 (대소문자 정확)
- `kindergartenId` 기반 `orderByChild().equalTo()` 필터링 — Firebase 인덱스 규칙에 `.indexOn: ["kindergartenId"]` 필요
- `onValue` 리스너는 초기 로드와 이후 업데이트를 모두 처리해야 한다 (차량 추가/삭제 포함)
