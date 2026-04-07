---
name: android-bustrack
description: "어린이집 버스 위치추적 Android 앱을 개발하는 스킬. Kotlin + Jetpack Glance 위젯 + Foreground Service + GPS + Firebase Realtime DB. 'Android 앱 만들어줘', '위젯 개발', '위치 추적 앱', 'GPS 전송', '기사님 앱' 등 Android 측 개발 요청 시 사용."
---

# Android BusTrack 개발 가이드

어린이집 등하원 버스 위치추적 Android 앱을 개발한다.

## 프로젝트 구조

```
app/src/main/
├── java/com/aightlabs/bustrack/
│   ├── MainActivity.kt            // 최초 차량 등록
│   ├── LocationService.kt         // Foreground Service (GPS 수집/전송)
│   ├── BusTrackWidget.kt          // Jetpack Glance 위젯
│   └── BusTrackWidgetReceiver.kt  // 위젯 리시버
├── res/
│   ├── layout/activity_main.xml   // 차량 등록 화면
│   └── xml/widget_info.xml        // 위젯 메타데이터
└── AndroidManifest.xml
```

## 핵심 구현 사항

### 1. Jetpack Glance 위젯
- 크기: 2x2 (큰 터치 영역 — 기사님 50~60대 고려)
- OFF: 회색 배경 + "운행 시작"
- ON: 녹색 배경 + "운행 중" + 경과 시간
- 터치 시 ON/OFF 토글 → LocationService 시작/종료

### 2. LocationService (Foreground Service)
- `FusedLocationProviderClient` 사용
- 위치 요청: `PRIORITY_HIGH_ACCURACY`, interval 30초
- Foreground 알림: "위치 공유 중" + "운행 종료" 버튼
- 3시간 타이머: `Handler.postDelayed`로 자동 종료

### 3. Firebase 전송 데이터

Firebase 경로: `vehicles/{vehicleId}/location`

```kotlin
val locationData = mapOf(
    "lat" to location.latitude,
    "lng" to location.longitude,
    "speed" to location.speed,
    "updatedAt" to ServerValue.TIMESTAMP
)
database.child("vehicles").child(vehicleId)
    .child("location").setValue(locationData)
```

상태 변경:
```kotlin
// ON → "active"
database.child("vehicles").child(vehicleId)
    .child("status").setValue("active")

// OFF → "inactive"
database.child("vehicles").child(vehicleId)
    .child("status").setValue("inactive")
```

### 4. 차량 등록

Firebase 경로: `vehicles/{vehicleId}`, `kindergartens/{kindergartenId}`

등록 흐름:
1. 어린이집 이름으로 기존 kindergarten 검색
2. 없으면 새 kindergarten 생성 (ID: `kg_` + UUID)
3. 차량 생성 (ID: `vehicle_` + UUID)
4. kindergartenId 연결
5. 학부모 공유 링크: `https://{domain}/track/{kindergartenId}`

### 5. 권한

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 6. 의존성

```kotlin
dependencies {
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")
}
```

## 경계면 주의사항

- Firebase에 쓰는 필드명(`lat`, `lng`, `speed`, `updatedAt`, `status`)은 웹에서 읽는 필드명과 정확히 일치해야 한다
- status 값은 반드시 `"active"` 또는 `"inactive"` 문자열 사용 (다른 변형 금지)
- `vehicleId`와 `kindergartenId` 형식을 웹과 통일 (`vehicle_` + UUID, `kg_` + UUID)
- location은 반드시 중첩 객체로 저장 (`vehicles/{id}/location/lat`, 평탄화 금지)
