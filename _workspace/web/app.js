// ============================================================
// app.js — Firebase Realtime DB listener + KakaoMap + Markers
// ============================================================

(function () {
  'use strict';

  // ---------- Firebase 초기화 ----------
  var firebaseConfig = {
    apiKey: 'AIzaSyC8uad4VK2-rtSRHQmrJMd4Mtdj3bCVyyA',
    authDomain: 'ibus-bustrack.firebaseapp.com',
    databaseURL: 'https://ibus-bustrack-default-rtdb.asia-southeast1.firebasedatabase.app',
    projectId: 'ibus-bustrack',
    storageBucket: 'ibus-bustrack.firebasestorage.app',
    messagingSenderId: '1083949263357',
    appId: '1:1083949263357:web:7b8f0ef40f306cd70ad194'
  };

  firebase.initializeApp(firebaseConfig);
  var db = firebase.database();

  // ---------- URL에서 kindergartenId 추출 ----------
  var pathParts = window.location.pathname.split('/track/');
  var kindergartenId = pathParts[1] ? pathParts[1].replace(/\/$/, '') : null;

  if (!kindergartenId) {
    document.getElementById('header-title').textContent = '잘못된 접근입니다';
    return;
  }

  // ---------- 상태 저장소 ----------
  var map = null;
  var markers = {};      // vehicleId -> { marker, overlay, data, animation }
  var vehicles = {};     // vehicleId -> vehicle data
  var selectedVehicleId = null;
  var relativeTimeTimer = null;

  // ---------- 카카오맵 초기화 ----------
  var DEFAULT_CENTER = { lat: 35.1028, lng: 128.9656 };
  var DEFAULT_ZOOM = 4;

  function initMap() {
    var container = document.getElementById('map');
    var options = {
      center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
      level: DEFAULT_ZOOM
    };
    map = new kakao.maps.Map(container, options);
  }

  // ---------- 마커 생성 ----------
  function createMarker(vehicleId, vehicle) {
    var lat = vehicle.location.lat;
    var lng = vehicle.location.lng;
    var isActive = vehicle.status === 'active';
    var position = new kakao.maps.LatLng(lat, lng);

    // 마커 (기본 마커 사용)
    var marker = new kakao.maps.Marker({
      position: position,
      map: map,
      opacity: isActive ? 1.0 : 0.5
    });

    // 라벨 오버레이
    var labelClass = 'marker-label' + (isActive ? '' : ' inactive');
    var labelContent = '<div class="' + labelClass + '">' +
      escapeHtml(vehicle.name) + ' - ' + escapeHtml(vehicle.route) + '</div>';
    var overlay = new kakao.maps.CustomOverlay({
      position: position,
      content: labelContent,
      yAnchor: 2.2,
      map: map
    });

    // 마커 클릭 이벤트
    kakao.maps.event.addListener(marker, 'click', function () {
      showVehicleCard(vehicleId);
    });

    markers[vehicleId] = {
      marker: marker,
      overlay: overlay,
      data: { lat: lat, lng: lng },
      animation: null
    };
  }

  // ---------- 마커 애니메이션 (ease-out cubic 보간, 1초) ----------
  function animateMarker(vehicleId, newLat, newLng) {
    var entry = markers[vehicleId];
    if (!entry) return;

    // 진행 중인 애니메이션 취소
    if (entry.animation) {
      cancelAnimationFrame(entry.animation);
      entry.animation = null;
    }

    var startLat = entry.data.lat;
    var startLng = entry.data.lng;
    var duration = 1000; // 1초
    var startTime = null;

    function easeOutCubic(t) {
      return 1 - Math.pow(1 - t, 3);
    }

    function step(timestamp) {
      if (!startTime) startTime = timestamp;
      var elapsed = timestamp - startTime;
      var progress = Math.min(elapsed / duration, 1);
      var eased = easeOutCubic(progress);

      var currentLat = startLat + (newLat - startLat) * eased;
      var currentLng = startLng + (newLng - startLng) * eased;
      var pos = new kakao.maps.LatLng(currentLat, currentLng);

      entry.marker.setPosition(pos);
      entry.overlay.setPosition(pos);

      if (progress < 1) {
        entry.animation = requestAnimationFrame(step);
      } else {
        entry.data.lat = newLat;
        entry.data.lng = newLng;
        entry.animation = null;
      }
    }

    entry.animation = requestAnimationFrame(step);
  }

  // ---------- 마커 스타일 업데이트 ----------
  function updateMarkerStyle(vehicleId, vehicle) {
    var entry = markers[vehicleId];
    if (!entry) return;

    var isActive = vehicle.status === 'active';
    entry.marker.setOpacity(isActive ? 1.0 : 0.5);

    // 라벨 오버레이 갱신
    var labelClass = 'marker-label' + (isActive ? '' : ' inactive');
    var labelContent = '<div class="' + labelClass + '">' +
      escapeHtml(vehicle.name) + ' - ' + escapeHtml(vehicle.route) + '</div>';
    entry.overlay.setContent(labelContent);
  }

  // ---------- 마커 업데이트 (생성 또는 이동) ----------
  function updateMarker(vehicleId, vehicle) {
    var lat = vehicle.location.lat;
    var lng = vehicle.location.lng;

    if (markers[vehicleId]) {
      animateMarker(vehicleId, lat, lng);
      updateMarkerStyle(vehicleId, vehicle);
    } else {
      createMarker(vehicleId, vehicle);
    }

    vehicles[vehicleId] = vehicle;
  }

  // ---------- 삭제된 차량 마커 제거 ----------
  function removeMarker(vehicleId) {
    var entry = markers[vehicleId];
    if (!entry) return;

    if (entry.animation) {
      cancelAnimationFrame(entry.animation);
    }
    entry.marker.setMap(null);
    entry.overlay.setMap(null);
    delete markers[vehicleId];
    delete vehicles[vehicleId];
  }

  // ---------- 상대 시간 ----------
  function getRelativeTime(timestamp) {
    if (!timestamp) return '';
    var diff = Date.now() - timestamp;
    if (diff < 0) diff = 0;

    var seconds = Math.floor(diff / 1000);
    if (seconds < 60) return seconds + '초 전';

    var minutes = Math.floor(seconds / 60);
    if (minutes < 60) return minutes + '분 전';

    var hours = Math.floor(minutes / 60);
    if (hours < 24) return hours + '시간 전';

    var days = Math.floor(hours / 24);
    return days + '일 전';
  }

  // ---------- 시각 포맷 (오후 5:30 형태) ----------
  function formatTime(timestamp) {
    if (!timestamp) return '';
    var d = new Date(timestamp);
    var hours = d.getHours();
    var minutes = d.getMinutes();
    var ampm = hours < 12 ? '오전' : '오후';
    var displayHours = hours % 12 || 12;
    var displayMinutes = minutes < 10 ? '0' + minutes : minutes;
    return ampm + ' ' + displayHours + ':' + displayMinutes;
  }

  // ---------- 차량 카드 리스트 갱신 ----------
  function updateCardList() {
    var cardList = document.getElementById('card-list');
    var ids = Object.keys(vehicles);

    // active 차량을 위에 정렬
    ids.sort(function (a, b) {
      var aActive = vehicles[a].status === 'active' ? 0 : 1;
      var bActive = vehicles[b].status === 'active' ? 0 : 1;
      return aActive - bActive;
    });

    var html = '';
    for (var i = 0; i < ids.length; i++) {
      var vid = ids[i];
      var v = vehicles[vid];
      var isActive = v.status === 'active';
      var statusClass = isActive ? 'active' : 'inactive';
      var statusText = isActive ? '운행 중' : '운행 종료';
      var timeText = isActive
        ? getRelativeTime(v.location.updatedAt) + ' 업데이트'
        : formatTime(v.location.updatedAt);

      html += '<div class="card-item" data-vehicle-id="' + escapeAttr(vid) + '">' +
        '<div class="card-item-dot ' + statusClass + '"></div>' +
        '<div class="card-item-info">' +
        '<div class="card-item-name">' + escapeHtml(v.name) + ' - ' + escapeHtml(v.route) + '</div>' +
        '</div>' +
        '<div style="text-align:right">' +
        '<div class="card-item-status ' + statusClass + '">' + statusText + '</div>' +
        '<div class="card-item-updated">' + timeText + '</div>' +
        '</div>' +
        '</div>';
    }

    cardList.innerHTML = html;

    // 카드 아이템 클릭 이벤트
    var items = cardList.querySelectorAll('.card-item');
    for (var j = 0; j < items.length; j++) {
      items[j].addEventListener('click', (function (id) {
        return function () { showVehicleCard(id); };
      })(items[j].getAttribute('data-vehicle-id')));
    }

    // 운행 중인 차량이 없는지 체크
    updateNoVehiclesOverlay();
  }

  // ---------- 운행 중 차량 없음 오버레이 ----------
  function updateNoVehiclesOverlay() {
    var overlay = document.getElementById('no-vehicles-overlay');
    var ids = Object.keys(vehicles);

    if (ids.length === 0) {
      overlay.classList.remove('hidden');
      return;
    }

    var hasActive = false;
    for (var i = 0; i < ids.length; i++) {
      if (vehicles[ids[i]].status === 'active') {
        hasActive = true;
        break;
      }
    }

    if (hasActive) {
      overlay.classList.add('hidden');
    } else {
      overlay.classList.remove('hidden');
    }
  }

  // ---------- 선택 차량 카드 (팝업) ----------
  function showVehicleCard(vehicleId) {
    var card = document.getElementById('vehicle-card');
    var v = vehicles[vehicleId];
    if (!v) return;

    selectedVehicleId = vehicleId;
    var isActive = v.status === 'active';

    document.getElementById('card-status-dot').className = isActive ? 'active' : 'inactive';
    document.getElementById('card-name').textContent = v.name;
    document.getElementById('card-route').textContent = v.route;

    var statusText = isActive ? '운행 중' : '운행 종료';
    var timeText = isActive
      ? getRelativeTime(v.location.updatedAt) + ' 업데이트'
      : formatTime(v.location.updatedAt);
    var statusEl = document.getElementById('card-status-text');
    statusEl.textContent = statusText + ' \u00b7 ' + timeText;
    statusEl.style.color = isActive ? '#22c55e' : '#9ca3af';

    card.classList.remove('hidden');
    // 약간의 딜레이로 트랜지션 트리거
    requestAnimationFrame(function () {
      card.classList.add('visible');
    });

    // 해당 마커 위치로 이동
    var pos = new kakao.maps.LatLng(v.location.lat, v.location.lng);
    map.panTo(pos);
  }

  function hideVehicleCard() {
    var card = document.getElementById('vehicle-card');
    card.classList.remove('visible');
    setTimeout(function () {
      card.classList.add('hidden');
    }, 250);
    selectedVehicleId = null;
  }

  // ---------- HTML escape ----------
  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function escapeAttr(str) {
    return escapeHtml(str);
  }

  // ---------- Firebase 리스너 ----------
  function startListeners() {
    var connectionOverlay = document.getElementById('connection-overlay');

    // 연결 상태 감시
    var connectedRef = db.ref('.info/connected');
    connectedRef.on('value', function (snap) {
      if (snap.val() === true) {
        connectionOverlay.classList.add('hidden');
      } else {
        connectionOverlay.classList.remove('hidden');
      }
    });

    // 어린이집 이름 표시
    db.ref('kindergartens/' + kindergartenId + '/name')
      .once('value', function (snap) {
        var name = snap.val();
        if (name) {
          document.getElementById('header-title').textContent =
            name + ' 등하원 차량 위치';
          document.title = name + ' 등하원 차량 위치';
        }
      });

    // 해당 어린이집의 차량 리스너
    var vehiclesRef = db.ref('vehicles');
    vehiclesRef.orderByChild('kindergartenId').equalTo(kindergartenId)
      .on('value', function (snapshot) {
        // 현재 스냅샷에 있는 차량 ID 수집
        var currentIds = {};
        snapshot.forEach(function (child) {
          var vid = child.key;
          var data = child.val();
          currentIds[vid] = true;
          updateMarker(vid, data);
        });

        // 스냅샷에 없는 기존 차량 제거
        var existingIds = Object.keys(markers);
        for (var i = 0; i < existingIds.length; i++) {
          if (!currentIds[existingIds[i]]) {
            removeMarker(existingIds[i]);
          }
        }

        updateCardList();

        // 선택된 차량 카드 갱신
        if (selectedVehicleId && vehicles[selectedVehicleId]) {
          showVehicleCard(selectedVehicleId);
        }
      });
  }

  // ---------- 상대 시간 자동 갱신 (30초마다) ----------
  function startRelativeTimeUpdater() {
    relativeTimeTimer = setInterval(function () {
      updateCardList();
      if (selectedVehicleId && vehicles[selectedVehicleId]) {
        showVehicleCard(selectedVehicleId);
      }
    }, 30000);
  }

  // ---------- 이벤트 바인딩 ----------
  function bindEvents() {
    // 지도 클릭 시 카드 닫기
    kakao.maps.event.addListener(map, 'click', function () {
      hideVehicleCard();
    });

    // 카드 핸들 터치로 닫기
    document.getElementById('card-handle').addEventListener('click', function () {
      hideVehicleCard();
    });
  }

  // ---------- 초기화 ----------
  function init() {
    initMap();
    bindEvents();
    startListeners();
    startRelativeTimeUpdater();
  }

  // 카카오맵 로드 확인 후 초기화
  if (typeof kakao !== 'undefined' && kakao.maps) {
    kakao.maps.load(function () {
      init();
    });
  } else {
    document.getElementById('map-container').innerHTML =
      '<div style="display:flex;align-items:center;justify-content:center;height:100%;padding:20px;text-align:center;">' +
      '<p style="color:#ef4444;font-size:15px;">지도를 불러올 수 없습니다.<br>페이지를 새로고침해 주세요.</p></div>';
  }

})();
