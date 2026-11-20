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

// 방 규칙 세 칸. key 는 Spring TripDto.Rules 칸 이름. 커뮤니티 글에서 가장 많이 싸운 것들이라(docs/11) 출발 전에 정해 둔다
var ROOM_RULES = [
  { key: 'driverPaysFuel', title: '운전한 사람 기름값', options: [{ label: '같이 내요', value: true }, { label: '빼 줘요', value: false }] },
  { key: 'refundNoShow', title: '전날 빠지면 예약금', options: [{ label: '돌려줘요', value: true }, { label: '안 돌려줘요', value: false }] },
  { key: 'drinksSeparate', title: '술값', options: [{ label: '다 같이 나눠요', value: false }, { label: '마신 사람만', value: true }] }
];

// 정한 것만 "운전한 사람 기름값 빼 줘요 · 술값 마신 사람만" 한 줄로. 하나도 안 정했으면 빈 글
function rulesText(rules) {
  return ROOM_RULES.filter(function (r) { return rules && rules[r.key] !== null && rules[r.key] !== undefined; })
    .map(function (r) {
      var picked = r.options.find(function (o) { return o.value === rules[r.key]; });
      return r.title + ' ' + picked.label;
    }).join(' · ');
}

// 손맛: 누른 것이 톡 튀고, 휴대폰은 12ms 짧게 떤다(안드로이드. 아이폰 사파리는 진동이 없어 튀기만).
// 움직임 줄이기를 켠 사람은 둘 다 안 한다
var REDUCE_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
function delight(el) {
  if (REDUCE_MOTION || !el) return;
  var node = el.jquery ? el[0] : el;
  if (!node) return;
  node.classList.remove('pop');
  void node.offsetWidth;
  node.classList.add('pop');
  if (navigator.vibrate && window.matchMedia('(pointer: coarse)').matches) navigator.vibrate(12);
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

// 여행지·철 사진. 방 만들기(고르는 순간)와 방 개요(머리 사진)가 같이 쓴다.
// 사진은 Unsplash 무료, 지역마다 그곳을 대표하는 장면 하나(가평 북한강, 강릉 바다 정자, 양양 서핑, 속초 설악산, 춘천 메타세쿼이아 길,
// 태안 바다, 부산 해변열차, 경주 동궁과 월지, 여수 케이블카, 거제 외도, 전주 한옥 지붕, 제주 성산)
var REGION_PHOTO = {
  '가평': ['gapyeong', '안개 낀 북한강과 소나무'], '강릉': ['gangneung', '파도 치는 강릉 바다의 정자'],
  '양양': ['yangyang', '서퍼들과 빨간 등대가 있는 양양 바다'], '속초': ['sokcho', '설악산 바위 능선'],
  '춘천': ['chuncheon', '메타세쿼이아가 늘어선 길'], '태안': ['taean', '꽃 너머로 배가 지나가는 태안 바다'],
  '부산': ['busan', '해운대 바닷가를 달리는 해변열차'], '경주': ['gyeongju', '연못에 비친 동궁과 월지'],
  '여수': ['yeosu', '케이블카가 지나는 여수 바다'], '거제': ['geoje', '바다를 내려다보는 외도 정원'],
  '전주': ['jeonju', '전주 한옥마을 지붕'], '제주': ['jeju', '바다 옆 성산일출봉']
};
// 지역 없이 날짜만 고르면 그 철 사진. 여름은 처음 사진(바다) 그대로
var SEASON_PHOTO = { winter: '눈 내리는 거리', spring: '벚꽃이 핀 길', autumn: '단풍 든 연못가 정자' };

// 지역 사진이 있으면 그것, 없으면 출발 달의 철 사진(여름은 null → 화면의 처음 사진)
function tripPhoto(destination, startDate) {
  var region = REGION_PHOTO[destination];
  if (region) return { src: '/img/region/' + region[0] + '.jpg', alt: region[1] };
  if (startDate) {
    var m = Number(startDate.slice(5, 7));
    var season = m === 12 || m <= 2 ? 'winter' : m <= 5 ? 'spring' : m >= 9 && m <= 11 ? 'autumn' : null;
    if (season) return { src: '/img/region/' + season + '.jpg', alt: SEASON_PHOTO[season] };
  }
  return null;
}

// 방 머리 사진 위 한 줄: 떠나기 전엔 D-day, 여행 중엔 몇째 날, 다녀와선 며칠 지났는지(그날그날 다른 말이 보이게)
function dday(destination, startDate, endDate, meet) {
  if (!startDate) return null;
  var day = function (s) { return new Date(s + 'T00:00:00'); };
  var now = new Date(); now = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  var toStart = Math.round((day(startDate) - now) / 86400000);
  var place = destination || '여행';
  // 하루 전·당일에는 모일 시간·장소가 먼저(지각이 제일 흔한 다툼이라)
  var clock = function (t) { var h = Number(t.slice(0, 2)), m = t.slice(3, 5); return (h < 12 ? '오전 ' : '오후 ') + (h % 12 || 12) + (m === '00' ? '시' : ':' + m); };
  var at = meet && (meet.time || meet.place) ? [meet.time ? clock(meet.time) : null, meet.place].filter(Boolean).join(' · ') : null;
  if (toStart === 1 && at) return '내일 ' + at;
  if (toStart > 0) return place + (destination ? '까지' : '') + ' D-' + toStart;
  if (toStart === 0) return at ? '오늘 ' + at + '에서 만나요' : '오늘 출발! 짐 다 챙겼죠?';
  var sinceEnd = Math.round((now - day(endDate || startDate)) / 86400000);
  if (sinceEnd <= 0) return place + ' ' + (1 - toStart) + '일째';
  return place + ' 다녀온 지 ' + sinceEnd + '일';
}

// 이름 뒤 이/가: 받침이 있으면 "문지훈이·재현이", 없으면 "민지가"(한글이 아니면 "이(가)")
function withSubject(name) {
  var code = name.charCodeAt(name.length - 1) - 0xAC00;
  if (code < 0 || code > 11171) return name + '이(가)';
  return name + (code % 28 ? '이' : '가');
}

// 같은 멤버로 다음 여행. POST: /api/trips/1/next -> { "tripId": 7, "created": true, "memberCount": 8 }
// 새 방(누가 먼저 만들었으면 그 방)으로 옮겨 가고, 옮겨 간 화면에서 한 줄로 알린다
function startNextTrip(tripId, button) {
  $(button).prop('disabled', true);
  return api('POST', '/api/trips/' + tripId + '/next').done(function (r) {
    try {
      sessionStorage.setItem('tripmate.toast', r.created
        ? '2탄 방을 열었어요. ' + r.memberCount + '명이 그대로 들어와 있어요. 되는 날부터 눌러 주세요.'
        : '먼저 열린 2탄 방으로 왔어요.');
    } catch (e) { /* 알림만 못 띄울 뿐 옮겨 가는 데는 지장 없다 */ }
    location.href = '/trips/' + r.tripId;
  }).fail(function () { $(button).prop('disabled', false); });
}

// 방 화면 공통: 로그아웃 버튼, 앞 화면이 남긴 알림 한 줄
// POST: /api/logout  -> 204
$(function () {
  try {
    var toast = sessionStorage.getItem('tripmate.toast');
    if (toast) { sessionStorage.removeItem('tripmate.toast'); showToast(toast); }
  } catch (e) { /* 저장소가 막힌 창(사생활 보호)이면 알림 없이 */ }
  $('#btn-logout').click(function () {
    api('POST', '/api/logout').always(function () { location.href = '/'; });
  });
});
