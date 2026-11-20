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
    // 화면마다 따로 쓰지 않아도 되게 공통으로. 화면이 더 맞는 말을 쓰면 그 말이 뒤에 덮는다
    else if (xhr.status === 0) showNotice('인터넷 연결이 끊긴 것 같아요. 연결되면 다시 눌러 주세요.');
    else if (xhr.status >= 500) showNotice('잠시 문제가 생겼어요. 조금 뒤 다시 눌러 주세요.');
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

// 휴대폰이면 공유 창(카톡 고르면 바로 그 대화방에 붙는다)을 띄우고, PC 면 복사한다. 손이 "복사 → 카톡 열기 → 붙여 넣기" 세 번에서 한 번으로 준다.
// PC 크롬·엣지도 share 가 있지만 윈도우 공유 창에는 카톡 PC 가 잘 안 떠서, 손가락으로 쓰는 기기(pointer: coarse)에서만 띄운다.
// 공유 창을 사람이 닫으면(AbortError) 아무것도 하지 않는다. 카톡 안 브라우저처럼 share 가 없으면 복사로 간다
var CAN_SHARE = !!navigator.share && window.isSecureContext && window.matchMedia('(pointer: coarse)').matches;
function shareText(text, copiedMessage) {
  if (CAN_SHARE) {
    return navigator.share({ text: text }).catch(function (e) {
      if (e && e.name === 'AbortError') return;
      return copyAndTell(text, copiedMessage);
    });
  }
  return copyAndTell(text, copiedMessage);
}

function copyAndTell(text, copiedMessage) {
  return copyText(text).then(
    function () { showToast(copiedMessage || '복사했어요. 단톡방에 붙여 넣어 주세요.'); },
    function () { showNotice('복사가 막혀 있어요. 화면의 링크를 길게 눌러 복사해 주세요.'); });
}

// "2027-01-15", "2027-01-17" -> "1월 15일(금) ~ 17일(일) · 2박 3일". 날짜 글자(ISO)를 그대로 보이면 읽는 데 한 번 더 걸린다
var WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
function formatPeriod(start, end) {
  if (!start) return '날짜 미정';
  var a = new Date(start + 'T00:00:00');
  var text = (a.getMonth() + 1) + '월 ' + a.getDate() + '일(' + WEEKDAYS[a.getDay()] + ')';
  if (!end || end === start) return text + ' 당일';
  var b = new Date(end + 'T00:00:00');
  var nights = Math.round((b - a) / 86400000);
  var endText = (b.getMonth() === a.getMonth() ? '' : (b.getMonth() + 1) + '월 ') + b.getDate() + '일(' + WEEKDAYS[b.getDay()] + ')';
  return text + ' ~ ' + endText + ' · ' + nights + '박 ' + (nights + 1) + '일';
}

// 단추 글을 기기에 맞춘다. 휴대폰 "카톡으로 보내기", PC "복사해서 단톡방에 붙이기"
function shareLabel(phone, pc) {
  return CAN_SHARE ? phone : pc;
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
    var period = formatPeriod(trip.startDate, trip.endDate);
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
