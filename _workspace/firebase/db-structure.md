# Firebase Realtime DB 구조 — 경계면 계약 문서

이 문서는 Android 앱과 웹 페이지가 공유하는 Firebase 데이터 구조의 **단일 진실 원천(Single Source of Truth)**이다.
양쪽 개발자는 이 문서의 필드명, 경로, 타입을 정확히 따라야 한다.

## 데이터 구조

```
Firebase Realtime DB
│
├── kindergartens/
│   └── {kindergartenId}/                    # 형식: "kg_" + UUID
│       ├── name: String                     # 예: "강서어린이집"
│       ├── createdAt: Number                # ServerValue.TIMESTAMP (Unix ms)
│       └── shareLink: String                # 예: "https://{domain}/track/kg_abc123"
│
└── vehicles/
    └── {vehicleId}/                         # 형식: "vehicle_" + UUID
        ├── kindergartenId: String           # → kindergartens 참조키
        ├── name: String                     # 예: "버스"
        ├── route: String                    # 예: "에코델타시티 운행"
        ├── status: String                   # "active" 또는 "inactive" (소문자 정확히)
        └── location/                        # ⚠️ 반드시 중첩 객체 (평탄화 금지)
            ├── lat: Number (double)         # 위도
            ├── lng: Number (double)         # 경도
            ├── speed: Number (float)        # 속도 (m/s)
            └── updatedAt: Number            # ServerValue.TIMESTAMP (Unix ms)
```

## 경계면 규칙

| 규칙 | 상세 |
|------|------|
| ID 형식 | kindergartenId: `kg_` + UUID, vehicleId: `vehicle_` + UUID (밑줄 사용) |
| status 값 | `"active"` 또는 `"inactive"` 만 허용 (대소문자 정확) |
| location 구조 | 반드시 중첩 객체. `vehicles/{id}/location/lat` (O), `vehicles/{id}/lat` (X) |
| 타임스탬프 | Android: `ServerValue.TIMESTAMP` 사용. Web: Unix milliseconds로 읽기 |
| 인덱스 | `vehicles` 하위에 `.indexOn: ["kindergartenId"]` 설정 필수 |

## 생산자/소비자 매핑

| 데이터 | 생산자 (Write) | 소비자 (Read) |
|--------|---------------|---------------|
| `kindergartens/{id}/*` | Android (차량 등록 시) | Web (어린이집 이름 표시) |
| `vehicles/{id}/status` | Android (위젯 ON/OFF) | Web (운행 중/종료 표시) |
| `vehicles/{id}/location/*` | Android (GPS 30초 간격) | Web (마커 위치 갱신) |
| `vehicles/{id}/name, route, kindergartenId` | Android (차량 등록 시) | Web (차량 정보 카드) |

## Firebase 경로 참조 (코드에서 사용할 정확한 경로)

```
# 어린이집 관련
kindergartens/{kindergartenId}/name
kindergartens/{kindergartenId}/createdAt
kindergartens/{kindergartenId}/shareLink

# 차량 관련
vehicles/{vehicleId}/kindergartenId
vehicles/{vehicleId}/name
vehicles/{vehicleId}/route
vehicles/{vehicleId}/status
vehicles/{vehicleId}/location/lat
vehicles/{vehicleId}/location/lng
vehicles/{vehicleId}/location/speed
vehicles/{vehicleId}/location/updatedAt
```
