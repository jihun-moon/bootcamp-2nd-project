// 숙소: 그룹 취향(평균·최솟값), 숙소 카드(점수·투표·조건), 링크 올리기. 다른 사람이 바꾸면(SSE stays) 다시 그린다
var tripId = $('body').data('trip-id');

var trip = null;
var board = null;

// 그룹이 원하는 정도(평균)를 숙소가 가진 조건만큼 더한다
function score(amenities) {
  return amenities.reduce(function (sum, key) {
    return sum + (board.preferences[key] ? board.preferences[key].avg : 0);
  }, 0);
}

function nameOf(memberId) {
  var m = trip.members.find(function (m) { return m.id === memberId; });
  return m ? m.name : '';
}

function renderPreferences() {
  $('#pref-empty').prop('hidden', board.surveyed > 0);
  $('#pref-note').prop('hidden', board.surveyed === 0)
    .text('설문 ' + board.surveyed + '명 · 막대는 평균, 세로선은 가장 낮게 준 사람의 점수(5점 만점)');
  var list = $('#pref-bars').empty();
  board.amenities.forEach(function (a) {
    var p = board.preferences[a.key];
    if (!p) return;
    // 평균만 보면 4:4 로 갈린 조건이 무난해 보여서 최솟값 1점이면 "의견 갈림"으로 따로 보인다
    var split = p.min <= 1;
    var bar = $('<div class="bar">')
      .append($('<div class="fill">').css('width', (p.avg / 5 * 100) + '%'))
      .append($('<div class="min-line">').css('left', (p.min / 5 * 100) + '%'));
    $('<li class="pref-row">')
      .append($('<span>').text(a.label), bar,
        $('<span class="small">').toggleClass('error', split).text(split ? '의견 갈림' : p.avg.toFixed(1) + '점'))
      .appendTo(list);
  });
}

// 그룹 취향 점수가 높은 순으로 카드를 그린다. 1등은 테를 칠해 "지금 가장 맞는 곳"이 바로 보이게
function renderStays() {
  var myVote = board.stays.find(function (s) { return s.voterIds.indexOf(trip.meId) >= 0; });
  var ranked = board.stays.slice().sort(function (a, b) {
    return score(b.amenities) - score(a.amenities) || a.id - b.id;
  });
  $('#stay-empty').prop('hidden', ranked.length > 0);
  $('#stay-count').text(ranked.length ? ranked.length + '곳 · 내 표 ' + (myVote ? '1' : '0') + '/1' : '');

  var list = $('#stay-list').empty();
  ranked.forEach(function (stay, i) {
    var mine = myVote && myVote.id === stay.id;
    var card = $('<li class="box stay">').toggleClass('best', i === 0 && board.surveyed > 0);
    if (stay.imageUrl) card.append($('<img class="photo" alt="" loading="lazy" referrerpolicy="no-referrer">').attr('src', stay.imageUrl));

    var title = $('<a target="_blank" rel="noopener noreferrer" class="strong">').attr('href', stay.url).text(stay.title + ' ↗');
    card.append($('<div class="between">').append(title,
      board.surveyed > 0 ? $('<span class="rank">').text((i + 1) + '위 · ' + score(stay.amenities).toFixed(1) + '점') : null));

    if (stay.price) card.append(priceLine(stay.price));

    var chips = $('<div class="row wrap">');
    board.amenities.forEach(function (a) {
      if (stay.amenities.indexOf(a.key) >= 0) chips.append($('<span class="chip on">').text(a.label));
    });
    // 그룹에서 "꼭 있어야" 한 사람이 있는(최솟값 4 이상) 조건이 이 숙소에 없으면 따로 짚어 준다
    missingMust(stay).forEach(function (a) { chips.append($('<span class="chip warn">').text(a.label + ' 없음')); });
    card.append(chips);

    var voters = stay.voterIds.map(nameOf).filter(Boolean);
    card.append($('<div class="between">').append(
      $('<span class="muted small">').text(voters.length ? voters.join(', ') : '아직 표 없음'),
      $('<button type="button" class="btn">').toggleClass('primary', !!mine)
        .text(mine ? '내 표 ✓ ' + stay.voterIds.length : '투표 ' + stay.voterIds.length)
        .attr('aria-pressed', !!mine)
        .click(function () { vote(mine ? null : stay.id); })));
    list.append(card);
  });
}

// "1박 23.9만원" 크게, 아래 "2박 8명이면 1인 약 6만원". 예약 사이트에 다시 들어가 나눗셈하지 않게 우리 방 날짜·인원으로 바로 센다
function priceLine(price) {
  var man = function (won) { return (Math.round(won / 1000) / 10).toLocaleString('ko-KR') + '만원'; };
  var nights = (trip.startDate && trip.endDate)
    ? Math.max(1, Math.round((new Date(trip.endDate) - new Date(trip.startDate)) / 86400000)) : 1;
  var people = trip.members.length;
  var line = $('<p class="price">').text('1박 ' + man(price) + ' ');
  if (people > 1) line.append($('<small>').text(nights + '박 ' + people + '명이면 1인 약 ' + man(price * nights / people)));
  return line;
}

function missingMust(stay) {
  return board.amenities.filter(function (a) {
    var p = board.preferences[a.key];
    return p && p.min >= 4 && stay.amenities.indexOf(a.key) < 0;
  });
}

function renderForm() {
  var box = $('#stay-amenities');
  if (box.children().length) return; // 골라 둔 것이 다시 그릴 때 지워지지 않게 한 번만
  board.amenities.forEach(function (a) {
    $('<button type="button" class="toggle">').attr('data-key', a.key).attr('aria-pressed', false).text(a.label)
      .click(function () { $(this).attr('aria-pressed', $(this).attr('aria-pressed') !== 'true'); })
      .appendTo(box);
  });
}

function pickedAmenities() {
  return $('#stay-amenities .toggle[aria-pressed="true"]').map(function () { return $(this).data('key'); }).get();
}

// POST: /api/trips/1/stays/preview  { "url": "https://..." } -> { "title", "imageUrl", "amenities": ["DETACHED"] }
// 링크를 붙여 넣으면 바로 읽어 이름·사진을 보이고, 소개 글에서 찾은 조건을 칠해 둔다. 사람이 이미 고친 칸은 건드리지 않는다
var previewedUrl = null;
function previewLink() {
  var url = $('#stay-url').val().trim();
  if (!/^https?:\/\/\S+\.\S+/.test(url) || url === previewedUrl) return;
  previewedUrl = url;
  $('#stay-preview').prop('hidden', false);
  $('#preview-photo').prop('hidden', true);
  $('#preview-status').text('링크 읽는 중…');
  api('POST', '/api/trips/' + tripId + '/stays/preview', { url: url }).done(function (p) {
    if ($('#stay-url').val().trim() !== url) return; // 읽는 사이 다른 링크로 바꿨으면 버린다
    if (p.imageUrl) $('#preview-photo').attr('src', p.imageUrl).prop('hidden', false);
    if (p.title && !$('#stay-title').val()) $('#stay-title').val(p.title);
    if (p.price && !$('#stay-price').val()) $('#stay-price').val(p.price);
    p.amenities.forEach(function (key) { $('#stay-amenities .toggle[data-key="' + key + '"]').attr('aria-pressed', true); });
    var found = [];
    if (p.price) found.push('오늘 1박 ' + p.price.toLocaleString('ko-KR') + '원');
    if (p.amenities.length) found.push('조건 ' + p.amenities.length + '개');
    $('#preview-status').text(found.length
      ? '링크에서 찾아 채웠어요: ' + found.join(' · ') + '. 가격은 날짜마다 달라요. 틀리면 고쳐 주세요.'
      : (p.title ? '이름과 사진을 가져왔어요. 있는 조건만 눌러 주세요.' : '이 사이트는 정보를 안 보여 줘요. 이름과 조건만 적어 주세요.'));
  }).fail(function () {
    previewedUrl = null;
    $('#preview-status').text('링크를 읽지 못했어요. 그래도 올릴 수 있어요.');
  });
}

// GET: /api/trips/1/stays  -> { "amenities": [...], "preferences": {...}, "surveyed": 3, "stays": [...] }
function loadBoard() {
  return api('GET', '/api/trips/' + tripId + '/stays').done(function (res) {
    board = res;
    renderPreferences();
    renderStays();
    renderForm();
  });
}

// PUT: /api/trips/1/votes/2  { "stayId": 3 }   같은 숙소를 다시 누르면 취소(null)
function vote(stayId) {
  showNotice('');
  api('PUT', '/api/trips/' + tripId + '/votes/' + trip.meId, { stayId: stayId })
    .fail(function (xhr) { if (xhr.status !== 401) showNotice('투표하지 못했어요. 다시 눌러 주세요.'); })
    .always(function () { loadBoard().done(function () { delight($('#stay-list .btn.primary')); }); });
}

// POST: /api/trips/1/stays  { "url": "https://...", "title": "숲속 독채", "amenities": ["DETACHED"] }
function addStay(e) {
  e.preventDefault();
  showNotice('');
  $('#btn-add').prop('disabled', true).text('올리는 중…');
  api('POST', '/api/trips/' + tripId + '/stays', {
    url: $('#stay-url').val().trim(),
    title: $('#stay-title').val().trim() || null,
    price: $('#stay-price').val() ? Number($('#stay-price').val()) : null,
    amenities: pickedAmenities()
  }).done(function () {
    $('#stay-form')[0].reset();
    $('#stay-amenities .toggle').attr('aria-pressed', false);
    $('#stay-preview').prop('hidden', true);
    previewedUrl = null;
    showToast('숙소를 올렸어요. 친구들 화면에도 바로 보여요.');
    loadBoard();
  }).fail(function (xhr) {
    if (xhr.status !== 401) showNotice(xhr.status === 400
      ? '링크가 https:// 로 시작하는지 확인해 주세요. 숙소는 방마다 30곳까지 올릴 수 있어요.'
      : '숙소를 올리지 못했어요. 다시 눌러 주세요.');
  }).always(function () {
    $('#btn-add').prop('disabled', false).text('올리기');
  });
}

$(function () {
  $('#stay-form').submit(addStay);
  // 붙여 넣으면 바로, 손으로 치면 멈춘 뒤 읽는다
  var typing = null;
  $('#stay-url').on('paste', function () { setTimeout(previewLink, 0); })
    .on('input', function () { clearTimeout(typing); typing = setTimeout(previewLink, 700); });

  loadTrip(tripId).done(function (t) {
    trip = t;
    loadBoard();
    // 설문을 끝낸 사람이 생기면 그룹 취향이 바뀐다
    listen(tripId, function (what) {
      if (what === 'members' || what === 'all') {
        loadTrip(tripId).done(function (t) { trip = t; loadBoard(); });
      } else if (what === 'stays') {
        loadBoard();
      }
    });
  });
});
