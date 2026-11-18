// 숙소: 그룹 취향(평균·최솟값), 숙소 표(점수·투표·조건), 링크 올리기. 다른 사람이 바꾸면(SSE stays) 다시 그린다
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

function renderTable() {
  $('#stay-table').prop('hidden', board.stays.length === 0);
  var head = $('#stay-head').empty().append('<th>숙소</th><th>점수</th><th>투표</th>');
  board.amenities.forEach(function (a) { head.append($('<th class="muted">').text(a.label)); });

  var myVote = board.stays.find(function (s) { return s.voterIds.indexOf(trip.meId) >= 0; });
  var ranked = board.stays.slice().sort(function (a, b) {
    return score(b.amenities) - score(a.amenities) || a.id - b.id;
  });

  var rows = $('#stay-rows').empty();
  ranked.forEach(function (stay) {
    var link = $('<a target="_blank" rel="noopener noreferrer" class="row strong">').attr('href', stay.url);
    if (stay.imageUrl) link.append($('<img class="thumb" alt="" referrerpolicy="no-referrer">').attr('src', stay.imageUrl));
    link.append($('<span>').text(stay.title));

    var mine = myVote && myVote.id === stay.id;
    var voteCell = $('<td class="center">').append(
      $('<button type="button" class="btn">').toggleClass('primary', mine).text(stay.voterIds.length + '표')
        .click(function () { vote(mine ? null : stay.id); }));
    if (stay.voterIds.length) voteCell.append($('<div class="muted small">').text(stay.voterIds.map(nameOf).join(', ')));

    var row = $('<tr>').append($('<td>').append(link), $('<td class="center">').text(score(stay.amenities).toFixed(1)), voteCell);
    board.amenities.forEach(function (a) {
      row.append($('<td class="center">').text(stay.amenities.indexOf(a.key) >= 0 ? '✓' : ''));
    });
    rows.append(row);
  });
}

function renderForm() {
  var box = $('#stay-amenities');
  if (box.children().length) return; // 체크해 둔 것이 다시 그릴 때 지워지지 않게 한 번만
  board.amenities.forEach(function (a) {
    $('<label class="row">').append($('<input type="checkbox" name="amenities">').val(a.key), $('<span>').text(a.label)).appendTo(box);
  });
}

// GET: /api/trips/1/stays  -> { "amenities": [...], "preferences": {...}, "surveyed": 3, "stays": [...] }
function loadBoard() {
  return api('GET', '/api/trips/' + tripId + '/stays').done(function (res) {
    board = res;
    renderPreferences();
    renderTable();
    renderForm();
  });
}

// PUT: /api/trips/1/votes/2  { "stayId": 3 }   같은 숙소를 다시 누르면 취소(null)
function vote(stayId) {
  showNotice('');
  api('PUT', '/api/trips/' + tripId + '/votes/' + trip.meId, { stayId: stayId })
    .fail(function (xhr) { if (xhr.status !== 401) showNotice('투표하지 못했어요. 다시 눌러 주세요.'); })
    .always(loadBoard);
}

// POST: /api/trips/1/stays  { "url": "https://...", "title": "숲속 독채", "amenities": ["DETACHED"] }
function addStay(e) {
  e.preventDefault();
  showNotice('');
  $('#btn-add').prop('disabled', true).text('올리는 중…');
  var amenities = $('#stay-amenities input:checked').map(function () { return this.value; }).get();
  api('POST', '/api/trips/' + tripId + '/stays', {
    url: $('#stay-url').val(),
    title: $('#stay-title').val() || null,
    amenities: amenities
  }).done(function () {
    $('#stay-form')[0].reset();
    loadBoard();
  }).fail(function (xhr) {
    if (xhr.status !== 401) showNotice('숙소를 올리지 못했어요. 링크가 https:// 로 시작하는지 확인해 주세요.');
  }).always(function () {
    $('#btn-add').prop('disabled', false).text('올리기');
  });
}

$(function () {
  $('#stay-form').submit(addStay);

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
