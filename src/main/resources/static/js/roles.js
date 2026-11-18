// 역할 보드: 역할 칸마다 사람 카드. 카드를 끌어 옮기면 서버에 저장하고, 다른 사람이 옮기면(SSE roles) 다시 그린다
var tripId = $('body').data('trip-id');
var UNASSIGNED = -1; // 역할 id 는 1 부터라 겹치지 않는다

var trip = null;
var board = { roles: [], assignments: [] };
var dragging = null;

function render() {
  var assigned = board.assignments.map(function (a) { return a.memberId; });
  var columns = board.roles.concat([{ id: UNASSIGNED, title: '미배정', required: false, capacity: 0, tasks: [] }]);
  var box = $('#board').empty();

  columns.forEach(function (col) {
    var cards = (col.id === UNASSIGNED)
      ? trip.members.filter(function (m) { return assigned.indexOf(m.id) < 0; })
          .map(function (m) { return { member: m, reasons: [] }; })
      : board.assignments.filter(function (a) { return a.roleId === col.id; })
          .map(function (a) { return { member: memberById(a.memberId), reasons: a.reasons }; });

    var title = $('<h3>').text(col.title);
    if (col.required) title.append($('<span class="tag">').text('필수'));
    if (col.capacity > 0) title.append($('<span class="tag">').text(cards.length + '/' + col.capacity));

    var section = $('<section class="column">')
      .toggleClass('empty-required', col.required && cards.length === 0)
      .append(title);
    if (col.tasks.length) section.append($('<p class="muted small">').text(col.tasks.join(' · ')));

    var list = $('<ul class="stack">').appendTo(section);
    cards.forEach(function (c) {
      var card = $('<li class="card" draggable="true">').append($('<div class="strong">').text(c.member.name));
      if (c.reasons.length) card.append($('<div class="muted small">').text(c.reasons.join(', ')));
      card.on('dragstart', function () { dragging = c.member.id; });
      list.append(card);
    });

    section.on('dragover', function (e) { e.preventDefault(); });
    section.on('drop', function () { moveTo(col.id); });
    box.append(section);
  });

  var isHost = memberById(trip.meId).host;
  $('#btn-recommend').prop('hidden', !isHost).text(board.assignments.length ? '다시 추천받기' : '설문으로 추천받기');
}

function memberById(id) {
  return trip.members.find(function (m) { return m.id === id; });
}

// GET: /api/trips/1/roles  -> { "roles": [...], "assignments": [{ "roleId", "memberId", "reasons", "version" }] }
function loadBoard() {
  return api('GET', '/api/trips/' + tripId + '/roles').done(function (res) {
    board = res;
    render();
  });
}

// PATCH: /api/trips/1/roles/assignments/2  { "roleId": 4, "version": 0 }
// 내가 본 version 이 서버와 다르면 그 사이 누가 옮긴 것이라 409. 최신 보드로 바꾸고 알려 준다
function moveTo(roleId) {
  if (dragging === null) return;
  var memberId = dragging;
  dragging = null;
  var prev = board.assignments.find(function (a) { return a.memberId === memberId; });
  if ((prev ? prev.roleId : UNASSIGNED) === roleId) return;

  showNotice('');
  api('PATCH', '/api/trips/' + tripId + '/roles/assignments/' + memberId, {
    roleId: roleId === UNASSIGNED ? null : roleId,
    version: prev ? prev.version : null
  }).done(function (res) {
    board = res;
    render();
  }).fail(function (xhr) {
    if (xhr.status === 409) {
      loadBoard();
      showNotice('다른 사람이 먼저 옮겼어요. 최신 보드로 바꿨으니 다시 옮겨 주세요.');
    } else if (xhr.status !== 401) {
      showNotice('옮기지 못했어요. 다시 해 주세요.');
    }
  });
}

// POST: /api/trips/1/roles/recommend   방장만. 손으로 옮긴 것까지 지우고 설문으로 다시 짠다
function recommend() {
  $('#btn-recommend').prop('disabled', true).text('계산 중…');
  api('POST', '/api/trips/' + tripId + '/roles/recommend')
    .done(function (res) {
      board = res;
      render();
    })
    .always(function () { $('#btn-recommend').prop('disabled', false); });
}

$(function () {
  $('#btn-recommend').click(recommend);

  loadTrip(tripId).done(function (t) {
    trip = t;
    loadBoard();
    // 누가 들어오면 미배정 칸이 바뀌니 방 정보도 다시 읽는다
    listen(tripId, function (what) {
      if (what === 'members' || what === 'all') {
        loadTrip(tripId).done(function (t) { trip = t; loadBoard(); });
      } else if (what === 'roles') {
        loadBoard();
      }
    });
  });
});
