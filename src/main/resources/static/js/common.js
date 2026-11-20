// 모든 화면이 같이 쓰는 것: API 부르기, 방 머리 채우기, 실시간 알림 받기

// 로그인 뒤 돌아올 곳. 다른 사이트로 튀지 않게 우리 주소만 받는다.
// "/\evil.com" 이나 "/<탭>/evil.com" 은 / 로 시작해도 브라우저가 "//evil.com" 으로 읽는다. 그래서 글자로 보지 않고
// 브라우저와 같은 방식(URL)으로 풀어 본 뒤 origin 이 우리 것인지 본다
function safeNext(path) {
  if (!path) return '/';
  try {
    var url = new URL(path, location.origin);
    return url.origin === location.origin ? url.pathname + url.search + url.hash : '/';
  } catch (e) {
    return '/';
  }
}

function goLogin() {
  location.href = '/login?next=' + encodeURIComponent(location.pathname);
}

// $.ajax 를 JSON 으로 부른다. 로그인이 풀려 있으면(401) 로그인 화면으로 보낸다
// 예) api('PUT', '/api/trips/1/votes/2', { stayId: 3 })
function api(method, url, body) {
  return $.ajax({
    url: url,
    type: method,
    contentType: 'application/json',
    data: body === undefined ? undefined : JSON.stringify(body)
  }).fail(function (xhr) {
    if (xhr.status === 401) goLogin();
  });
}

// 초대 링크·카톡용 글을 복사한다. clipboard API 는 https·localhost 에서만 돼서
// 같은 와이파이 휴대폰으로 http://192.168... 에 들어온 경우는 옛 방식(execCommand)으로 복사한다
function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) return navigator.clipboard.writeText(text);
  var area = $('<textarea readonly>').val(text).css({ position: 'fixed', opacity: 0 }).appendTo('body');
  area[0].select();
  var ok = document.execCommand('copy');
  area.remove();
  return ok ? Promise.resolve() : Promise.reject();
}

// 결과만 짧게 알릴 때(복사했어요 등). 막힘·오류처럼 사람이 읽고 해야 할 일이 있으면 showNotice 를 쓴다
var toastTimer = null;
function showToast(message) {
  var toast = $('#toast');
  if (!toast.length) toast = $('<div id="toast" class="toast" role="status" aria-live="polite">').appendTo('body');
  toast.text(message).prop('hidden', false);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(function () { toast.prop('hidden', true); }, 2500);
}

function showNotice(message) {
  $('#notice').text(message).prop('hidden', !message);
}

// 사진이 있으면 사진, 없으면 이름 첫 글자 동그라미
function avatar(name, imageUrl) {
  if (imageUrl) return $('<img class="avatar" alt="" referrerpolicy="no-referrer">').attr('src', imageUrl);
  return $('<span class="avatar"></span>').text(name.slice(0, 1));
}

// GET: /api/trips/1   방 머리(이름·지역·날짜·인원·나)를 채우고 방 정보를 돌려준다
function loadTrip(tripId) {
  return api('GET', '/api/trips/' + tripId).done(function (trip) {
    var me = trip.members.find(function (m) { return m.id === trip.meId; });
    var period = trip.startDate ? trip.startDate + ' ~ ' + (trip.endDate || '') : '날짜 미정';
    document.title = trip.title + ' | TripMate';
    $('#trip-title').text(trip.title);
    $('#trip-meta').text((trip.destination || '지역 미정') + ' · ' + period + ' · ' + trip.members.length + '명');
    $('#me-name').empty().append(avatar(me.name, me.imageUrl), $('<span>').text(me.name));
  }).fail(function (xhr) {
    if (xhr.status === 404) $('#trip-title').text('방을 찾을 수 없어요. 초대 링크로 들어와 주세요.');
  });
}

// GET: /api/trips/1/events  (SSE, event: change | ping)
// 서버는 "바뀌었다"(data: roles | members | stays)만 알리고, 화면은 onChange 에서 API 로 다시 읽는다
var SILENCE_LIMIT = 40000; // 서버는 15초마다 ping. 이보다 오래 조용하면 끊긴 것으로 본다

function listen(tripId, onChange) {
  var source = null;
  var lastSeen = Date.now();
  var opened = false;

  function connect() {
    if (source) source.close();
    lastSeen = Date.now();
    source = new EventSource('/api/trips/' + tripId + '/events');
    source.onopen = function () {
      lastSeen = Date.now();
      // 다시 붙은 것이면 끊긴 사이 바뀐 것을 놓쳤을 수 있어 한 번 새로 읽는다
      if (opened) onChange('all');
      opened = true;
    };
    source.addEventListener('ping', function () { lastSeen = Date.now(); });
    source.addEventListener('change', function (e) {
      lastSeen = Date.now();
      onChange(e.data);
    });
  }

  connect();
  setInterval(function () {
    if (Date.now() - lastSeen > SILENCE_LIMIT) connect();
  }, 10000);
}

// 방 화면 공통: 로그아웃 버튼
// POST: /api/logout  -> 204
$(function () {
  $('#btn-logout').click(function () {
    api('POST', '/api/logout').always(function () { location.href = '/'; });
  });
});
