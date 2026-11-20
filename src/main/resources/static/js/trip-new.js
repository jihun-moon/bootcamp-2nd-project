// 여행방 만들기: 지역 누르기, 달력에서 출발·도착 누르기, 이름은 비우면 저절로
// POST: /api/trips  { "title": "강릉 2박 3일", "destination": "강릉", "startDate": "2027-01-15", "endDate": "2027-01-17" }
// 만든 사람이 방장이 되고, 만든 방 개요(초대 보내기가 맨 위)로 간다
var DRAFT_KEY = 'tripmate.trip-draft';
// 단체 여행으로 많이 가는 곳. 지역 이름은 카카오 길찾기 검색어로도 쓰여서 시·군 이름 그대로
var REGIONS = ['가평', '강릉', '양양', '속초', '춘천', '태안', '부산', '경주', '여수', '거제', '전주', '제주'];

// 고른 곳·때에 맞춰 왼쪽 사진을 바꾼다. 누르는 순간 "거기 가는 기분"이 들게(지훈: 사소한 게 쌓이면 고퀄리티).
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
var defaultCover = null;

var picked = { start: null, end: null };   // 'YYYY-MM-DD'
var shown = null;                           // 달력에 보이는 달의 1일(Date)

// 시간대 때문에 하루가 밀리지 않게 Date 를 글자로 바꿀 때 지역 날짜를 그대로 쓴다
function iso(d) {
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}
function today() {
  var d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

// 지역: 하나만 고른다. 다시 누르면 취소. "직접 입력"을 누르면 칸이 열린다
function renderRegions() {
  var box = $('#regions').empty();
  REGIONS.concat(['직접 입력']).forEach(function (name) {
    var custom = name === '직접 입력';
    $('<button type="button" class="toggle">').text(name).attr('data-region', custom ? '' : name).click(function () {
      var on = $(this).attr('aria-pressed') === 'true';
      box.children().attr('aria-pressed', false);
      $(this).attr('aria-pressed', !on);
      $('#destination').prop('hidden', !(custom && !on)).val(custom || on ? '' : name);
      if (custom && !on) $('#destination').trigger('focus');
      suggestTitle();
    }).appendTo(box);
  });
}

function destination() {
  return $('#destination').val().trim();
}

// 달력: 한 달씩. 지난 날은 못 누른다. 첫 번째 누름은 출발, 두 번째는 도착(출발보다 앞이면 출발을 바꾼다).
// 숙소 앱처럼 출발·도착은 동그라미, 그 사이는 띠로 잇는다(스레드: "며칠짜리 일정은 한 줄로 길게")
function renderCalendar() {
  var cal = $('#cal').empty();
  var y = shown.getFullYear(), m = shown.getMonth();
  var head = $('<div class="cal-head">');
  var prev = $('<button type="button" class="cal-nav" aria-label="이전 달">‹</button>')
    .prop('disabled', y === today().getFullYear() && m === today().getMonth())
    .click(function () { shown = new Date(y, m - 1, 1); renderCalendar(); });
  var next = $('<button type="button" class="cal-nav" aria-label="다음 달">›</button>')
    .click(function () { shown = new Date(y, m + 1, 1); renderCalendar(); });
  head.append(prev, $('<span class="strong">').text(y + '년 ' + (m + 1) + '월'), next).appendTo(cal);

  var grid = $('<div class="cal-grid">').appendTo(cal);
  ['일', '월', '화', '수', '목', '금', '토'].forEach(function (w) { $('<span class="cal-week">').text(w).appendTo(grid); });
  for (var i = 0; i < new Date(y, m, 1).getDay(); i++) $('<span>').appendTo(grid);

  var days = new Date(y, m + 1, 0).getDate();
  var todayIso = iso(today());
  for (var d = 1; d <= days; d++) {
    var date = new Date(y, m, d);
    var key = iso(date);
    var cell = $('<button type="button" class="cal-day">').text(d).attr('data-date', key)
      .attr('aria-label', (m + 1) + '월 ' + d + '일')
      .prop('disabled', key < todayIso)
      .toggleClass('sat', date.getDay() === 6).toggleClass('sun', date.getDay() === 0)
      .toggleClass('today', key === todayIso)
      .toggleClass('start', key === picked.start).toggleClass('end', key === picked.end)
      .toggleClass('between', !!(picked.start && picked.end && key > picked.start && key < picked.end))
      .attr('aria-pressed', key === picked.start || key === picked.end);
    if (picked.start && picked.end && picked.start !== picked.end) {
      cell.toggleClass('band-right', key === picked.start).toggleClass('band-left', key === picked.end);
    }
    cell.click(function () { pickDate($(this).data('date')); });
    grid.append(cell);
  }
}

function pickDate(key) {
  if (!picked.start || picked.end || key < picked.start) {
    picked = { start: key, end: null };
  } else {
    picked.end = key;
  }
  applyDates();
}

function applyDates() {
  $('#startDate').val(picked.start || '');
  $('#endDate').val(picked.end || '');
  renderCalendar();
  $('#period').text(!picked.start ? '출발일과 도착일을 차례로 눌러 주세요.'
    : !picked.end ? formatPeriod(picked.start, null).replace(' 당일', '') + ' 출발 · 이제 도착일을 눌러 주세요'
    : formatPeriod(picked.start, picked.end)).toggleClass('set', !!picked.end);
  suggestTitle();
}

// "이번 주말", "다음 주말": 금요일 출발, 일요일 도착. 오늘이 금·토·일이면 이번 주말은 오늘부터
function renderQuickDates() {
  var box = $('#quick-dates').empty();
  var t = today();
  var friday = new Date(t.getFullYear(), t.getMonth(), t.getDate() + ((5 - t.getDay() + 7) % 7));
  if (t.getDay() === 6 || t.getDay() === 0) friday = t;
  [['이번 주말', 0], ['다음 주말', 7]].forEach(function (q) {
    var start = new Date(friday.getFullYear(), friday.getMonth(), friday.getDate() + q[1]);
    var sunday = new Date(start.getFullYear(), start.getMonth(), start.getDate() + ((7 - start.getDay()) % 7));
    $('<button type="button" class="toggle">').text(q[0]).click(function () {
      picked = { start: iso(start), end: iso(sunday) };
      shown = new Date(start.getFullYear(), start.getMonth(), 1);
      applyDates();
    }).appendTo(box);
  });
}

// 이름을 안 적으면 쓸 이름을 칸 안에 미리 보여 준다: "강릉 2박 3일", "강릉 여행", "우리 여행"
function autoTitle() {
  var place = destination();
  if (picked.start && picked.end) {
    var nights = Math.round((new Date(picked.end) - new Date(picked.start)) / 86400000);
    return (place || '우리') + ' ' + (nights ? nights + '박 ' + (nights + 1) + '일' : '당일치기');
  }
  return place ? place + ' 여행' : '우리 여행';
}
function suggestTitle() {
  $('#title').attr('placeholder', autoTitle());
  updateCover();
}

function coverFor() {
  var region = REGION_PHOTO[destination()];
  if (region) return { src: '/img/region/' + region[0] + '.jpg', alt: region[1] };
  if (picked.start) {
    var m = Number(picked.start.slice(5, 7));
    var season = m === 12 || m <= 2 ? 'winter' : m <= 5 ? 'spring' : m >= 9 && m <= 11 ? 'autumn' : null;
    if (season) return { src: '/img/region/' + season + '.jpg', alt: SEASON_PHOTO[season] };
  }
  return defaultCover;
}

// 새 사진을 먼저 받아 두고 살짝 사라졌다 나타나게 바꾼다(받는 동안 빈 칸이 보이지 않게)
function updateCover() {
  var img = $('.cover');
  var next = coverFor();
  if (!img.length || !next || img.attr('src') === next.src) return;
  var pre = new Image();
  pre.onload = function () {
    img.addClass('fading');
    setTimeout(function () { img.attr({ src: next.src, alt: next.alt }).removeClass('fading'); }, 180);
  };
  pre.src = next.src;
}

function readForm() {
  return {
    title: $('#title').val().trim() || autoTitle(),
    destination: destination(),
    startDate: picked.start || '',
    endDate: picked.end || ''
  };
}

function create(form) {
  // 로그인 안 한 채 누르면 api() 가 로그인으로 보낸다. 돌아왔을 때 다시 고르지 않게 맡겨 둔다
  sessionStorage.setItem(DRAFT_KEY, JSON.stringify(form));
  $('#btn-create').prop('disabled', true).text('만드는 중…');
  api('POST', '/api/trips', {
    title: form.title,
    destination: form.destination || null,
    startDate: form.startDate || null,
    endDate: form.endDate || null
  }).done(function (trip) {
    sessionStorage.removeItem(DRAFT_KEY);
    location.href = '/trips/' + trip.id;
  }).fail(function (xhr) {
    if (xhr.status === 401) return;
    sessionStorage.removeItem(DRAFT_KEY); // 다음에 들어왔을 때 저절로 또 만들지 않게
    $('#form-error').text('방을 만들지 못했어요. 잠시 뒤 다시 눌러 주세요.').prop('hidden', false);
    $('#btn-create').prop('disabled', false).text('방 만들고 초대 링크 받기');
  });
}

$(function () {
  defaultCover = { src: $('.cover').attr('src'), alt: $('.cover').attr('alt') };
  shown = new Date(today().getFullYear(), today().getMonth(), 1);
  renderRegions();
  renderQuickDates();
  $('#destination').on('input', suggestTitle);
  $('#trip-form').submit(function (e) {
    e.preventDefault();
    create(readForm());
  });

  // 로그인하고 돌아왔으면 고른 것을 되살리고, 이미 한 번 눌렀던 것이니 바로 만든다
  var draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || 'null');
  if (draft && draft.title) {
    $('#title').val(draft.title);
    $('#destination').val(draft.destination || '');
    picked = { start: draft.startDate || null, end: draft.endDate || null };
    if (picked.start) shown = new Date(picked.start + 'T00:00:00');
    shown = new Date(shown.getFullYear(), shown.getMonth(), 1);
    applyDates();
    create(draft);
    return;
  }
  applyDates();
});
