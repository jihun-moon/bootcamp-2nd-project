// 역할 보드: 역할 칸마다 사람 카드. 카드를 누르거나(휴대폰) 끌어 옮기면(PC) 서버에 저장하고, 다른 사람이 옮기면(SSE roles) 다시 그린다
var tripId = $('body').data('trip-id');
var UNASSIGNED = -1; // 역할 id 는 1 부터라 겹치지 않는다
var MOVED_MARK_MS = 1500;

var trip = null;
var board = { roles: [], assignments: [] };
var dragging = null;
var picking = null; // 역할 고르기 창을 연 사람
var justMoved = null;
var editing = null; // 고치는 중인 역할. null 이면 새로 더하기
var travel = null; // 마지막으로 센 이동·회비 근거. 역할이 바뀌면 운전하는 차가 달라지니 비운다

// 미배정까지 넣은 칸 목록. 칸마다 그 칸에 있는 카드를 같이 담는다
function columns() {
  var assigned = board.assignments.map(function (a) { return a.memberId; });
  return board.roles.concat([{ id: UNASSIGNED, title: '미배정', required: false, capacity: 0, tasks: [] }])
    .map(function (col) {
      var cards = (col.id === UNASSIGNED)
        ? trip.members.filter(function (m) { return assigned.indexOf(m.id) < 0; })
            .map(function (m) { return { member: m, reasons: [] }; })
        : board.assignments.filter(function (a) { return a.roleId === col.id; })
            .map(function (a) { return { member: memberById(a.memberId), reasons: a.reasons }; });
      return { role: col, cards: cards };
    });
}

function render() {
  var box = $('#board').empty();
  var isHost = memberById(trip.meId).host;

  columns().forEach(function (col) {
    var role = col.role;
    var title = $('<h3>').text(role.title);
    if (role.required) title.append($('<span class="tag">').text('필수'));
    if (role.capacity > 0) title.append($('<span class="tag">').text(col.cards.length + '/' + role.capacity));
    if (isHost && role.id !== UNASSIGNED) {
      title.append($('<button type="button" class="link tag">').text('고치기').on('click', function () { openEditor(role); }));
    }

    var section = $('<section class="column">')
      .toggleClass('empty-required', role.required && col.cards.length === 0)
      .append(title);
    if (role.tasks.length) section.append($('<p class="muted small">').text(role.tasks.join(' · ')));

    var list = $('<ul class="stack">').appendTo(section);
    col.cards.forEach(function (c) { list.append(memberCard(c)); });
    if (!col.cards.length && role.id !== UNASSIGNED) {
      list.append($('<li class="muted small">').text(role.required ? '꼭 한 명은 있어야 해요' : '비어 있어요'));
    }

    section.on('dragover', function (e) { e.preventDefault(); });
    section.on('drop', function () {
      var memberId = dragging;
      dragging = null;
      moveTo(memberId, role.id);
    });
    box.append(section);
  });

  $('#btn-add-role').prop('hidden', !isHost);
  $('#btn-recommend').prop('hidden', !isHost).text(board.assignments.length ? '다시 추천받기' : '설문으로 추천받기');
}

// 카드 전체가 단추라 휴대폰은 눌러서, 키보드는 Tab·Enter 로 옮긴다. 끌기는 PC 용으로 같이 둔다
function memberCard(c) {
  var m = c.member;
  var name = $('<div class="strong">').text(m.name);
  if (m.id === trip.meId) name.append($('<span class="tag me">').text('나'));
  if (!m.surveyDone) name.append($('<span class="tag">').text('설문 전'));

  var button = $('<button type="button" class="member-card">').append(name);
  if (c.reasons.length) button.append($('<div class="muted small">').text(c.reasons.join(', ')));
  button.on('click', function () { openPicker(m.id); });

  var card = $('<li class="card" draggable="true">').toggleClass('moved', m.id === justMoved).append(button);
  card.on('dragstart', function () { dragging = m.id; });
  return card;
}

function memberById(id) {
  return trip.members.find(function (m) { return m.id === id; });
}

function currentRoleOf(memberId) {
  var a = board.assignments.find(function (a) { return a.memberId === memberId; });
  return a ? a.roleId : UNASSIGNED;
}

// 역할 고르기 창. 정원이 찬 칸도 고를 수 있게 둔다(서버도 막지 않는다). 손으로 한 명 더 넣는 건 사람 판단이다
function openPicker(memberId) {
  picking = memberId;
  var now = currentRoleOf(memberId);
  $('#picker-title').text(memberById(memberId).name + ' 님을 어디로 옮길까요?');

  var list = $('#picker-options').empty();
  columns().forEach(function (col) {
    var role = col.role;
    var label = $('<span>').text(role.title);
    var count = (role.capacity > 0) ? col.cards.length + '/' + role.capacity : col.cards.length + '명';
    if (role.capacity > 0 && col.cards.length >= role.capacity) count += ' · 가득';
    var option = $('<button type="button" class="choice between">')
      .append(label, $('<span class="muted small">').text(role.id === now ? '지금 여기' : count))
      .prop('disabled', role.id === now)
      .on('click', function () {
        closePicker();
        moveTo(memberId, role.id);
      });
    list.append($('<li>').append(option));
  });
  var dialog = $('#role-picker')[0];
  if (!dialog.open) dialog.showModal();
}

function closePicker() {
  picking = null;
  $('#role-picker')[0].close();
}

// 역할 편집 창. role 이 없으면 새로 더하기
function openEditor(role) {
  editing = role;
  $('#editor-title').text(role ? '역할 고치기' : '역할 더하기');
  $('#role-title').val(role ? role.title : '');
  $('#role-capacity').val(role ? role.capacity : 1);
  $('#role-required').prop('checked', role ? role.required : false);
  $('#role-tasks').val(role ? role.tasks.join(', ') : '');
  $('#btn-role-delete').prop('hidden', !role);
  $('#role-error').prop('hidden', true);
  $('#role-editor')[0].showModal();
}

// POST: /api/trips/1/roles     { "title": "설거지", "required": false, "capacity": 2, "tasks": ["저녁 설거지"] }
// PATCH: /api/trips/1/roles/7  같은 칸
function saveRole(e) {
  e.preventDefault();
  var tasks = $('#role-tasks').val().split(',').map(function (t) { return t.trim(); }).filter(Boolean);
  if (tasks.length > 5) {
    $('#role-error').text('할 일은 5개까지 적을 수 있어요.').prop('hidden', false);
    return;
  }
  var body = {
    title: $('#role-title').val().trim(),
    required: $('#role-required').prop('checked'),
    capacity: Number($('#role-capacity').val()),
    tasks: tasks
  };
  var request = editing
    ? api('PATCH', '/api/trips/' + tripId + '/roles/' + editing.id, body)
    : api('POST', '/api/trips/' + tripId + '/roles', body);
  $('#btn-role-save').prop('disabled', true);
  request.done(function (res) {
    board = res;
    render();
    $('#role-editor')[0].close();
  }).fail(function (xhr) {
    if (xhr.status === 401) return;
    $('#role-error').text(xhr.status === 400
      ? '이름(30자), 정원(1~8명), 할 일(20자씩 5개)을 확인해 주세요. 역할은 12개까지예요.'
      : '저장하지 못했어요. 다시 눌러 주세요.').prop('hidden', false);
  }).always(function () { $('#btn-role-save').prop('disabled', false); });
}

// DELETE: /api/trips/1/roles/7  -> 204. 그 역할에 있던 사람은 미배정으로
function deleteRole() {
  if (!confirm("'" + editing.title + "' 역할을 지울까요? 이 역할에 있던 사람은 미배정으로 가요.")) return;
  api('DELETE', '/api/trips/' + tripId + '/roles/' + editing.id).done(function () {
    $('#role-editor')[0].close();
    loadBoard();
  }).fail(function (xhr) {
    if (xhr.status !== 401) $('#role-error').text('지우지 못했어요. 다시 눌러 주세요.').prop('hidden', false);
  });
}

// GET: /api/trips/1/roles  -> { "roles": [...], "assignments": [{ "roleId", "memberId", "reasons", "version" }] }
function loadBoard() {
  return api('GET', '/api/trips/' + tripId + '/roles').done(function (res) {
    board = res;
    render();
    // 창을 연 사이 누가 옮겼으면 칸 인원이 바뀌었으니 창도 새로 그린다
    if (picking !== null) openPicker(picking);
  });
}

// PATCH: /api/trips/1/roles/assignments/2  { "roleId": 4, "version": 0 }
// 내가 본 version 이 서버와 다르면 그 사이 누가 옮긴 것이라 409. 최신 보드로 바꾸고 알려 준다
function moveTo(memberId, roleId) {
  if (memberId === null || currentRoleOf(memberId) === roleId) return;
  var prev = board.assignments.find(function (a) { return a.memberId === memberId; });

  showNotice('');
  api('PATCH', '/api/trips/' + tripId + '/roles/assignments/' + memberId, {
    roleId: roleId === UNASSIGNED ? null : roleId,
    version: prev ? prev.version : null
  }).done(function (res) {
    board = res;
    // 휴대폰은 칸이 세로로 하나씩이라 옮긴 카드가 화면 밖으로 간다. 잠깐 칠하고 그 칸까지 내려 준다
    justMoved = memberId;
    render();
    var card = $('#board .card.moved')[0];
    if (card) card.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    setTimeout(function () {
      if (justMoved !== memberId) return;
      justMoved = null;
      $('#board .card.moved').removeClass('moved');
    }, MOVED_MARK_MS);
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

// GET: /api/trips/1/roles/travel
// -> { "message": null, "legs": [{ "name", "driver", "km", "minutes", "toll", "fuel", "note" }], "perPerson": 6000,
//      "fuelPrice": 1700, "fuelSource": "어림값", "kmPerLiter": 12, "tips": [] }
function loadTravel() {
  $('#btn-travel').prop('disabled', true).text('계산 중…');
  api('GET', '/api/trips/' + tripId + '/roles/travel')
    .done(function (res) {
      travel = res;
      renderTravel();
    })
    .always(function () { $('#btn-travel').prop('disabled', false).text('다시 계산'); });
}

function renderTravel() {
  var box = $('#travel').empty();
  if (!travel) return;
  if (travel.message) {
    box.append($('<p class="hint">').text(travel.message));
    return;
  }
  var list = $('<ul class="stack">').appendTo(box);
  travel.legs.forEach(function (l) {
    var name = $('<div class="strong">').text(l.name);
    if (l.driver) name.append($('<span class="tag">').text('운전'));
    var detail = (l.km === null)
      ? l.note
      : '편도 ' + l.km + 'km · ' + minutesText(l.minutes) + ' · 기름 ' + won(l.fuel) + ' · 통행료 ' + won(l.toll);
    list.append($('<li class="card">').append(name, $('<div class="muted small">').text(detail)));
  });
  if (travel.perPerson !== null) {
    box.append($('<p class="strong">').text('1인 교통비 약 ' + won(travel.perPerson) + ' (운전하는 차 왕복, ' + trip.members.length + '명)'));
  }
  travel.tips.forEach(function (t) { box.append($('<p class="notice">').text(t)); });
  box.append($('<p class="muted small">').text('길·통행료: 카카오모빌리티 · 휘발유 ' + won(travel.fuelPrice) + '/L(' + travel.fuelSource + ') · 연비 ' + travel.kmPerLiter + 'km/L로 셈'));
}

function minutesText(minutes) {
  var h = Math.floor(minutes / 60);
  return (h ? h + '시간 ' : '') + (minutes % 60) + '분';
}

function won(n) {
  return n.toLocaleString('ko-KR') + '원';
}

// 단톡방에 붙여 넣을 글. 링크는 방 주소 대신 초대 링크라 아직 안 들어온 사람도 눌러서 들어온다
function resultText() {
  var lines = ['[' + trip.title + '] 역할'];
  columns().forEach(function (col) {
    if (col.role.id === UNASSIGNED && !col.cards.length) return;
    var names = col.cards.map(function (c) { return c.member.name; });
    lines.push(col.role.title + ': ' + (names.length ? names.join(', ') : '(비어 있음)'));
  });
  var notYet = trip.members.filter(function (m) { return !m.surveyDone; }).map(function (m) { return m.name; });
  if (notYet.length) lines.push('설문 아직: ' + notYet.join(', '));
  if (travel && travel.perPerson !== null) lines.push('1인 교통비 약 ' + won(travel.perPerson) + ' (기름값·통행료 왕복)');
  lines.push('TripMate 에서 정했어요 ' + location.origin + '/join/' + trip.inviteCode);
  return lines.join('\n');
}

function copyResult() {
  copyText(resultText()).then(
    function () { showToast('복사했어요. 단톡방에 붙여 넣어 주세요.'); },
    function () { showNotice('복사하지 못했어요. 다시 눌러 주세요.'); }
  );
}

$(function () {
  $('#btn-recommend').click(recommend);
  $('#btn-copy').click(copyResult);
  $('#btn-travel').click(loadTravel);
  $('#btn-add-role').click(function () { openEditor(null); });
  $('#role-form').on('submit', saveRole);
  $('#btn-role-delete').click(deleteRole);
  $('#btn-role-cancel').click(function () { $('#role-editor')[0].close(); });
  $('#btn-picker-close').click(closePicker);
  // 창 바깥(어두운 곳)을 누르면 닫는다. 창 안을 누르면 target 이 안쪽 요소라 닫히지 않는다
  $('#role-picker').on('click', function (e) { if (e.target === this) closePicker(); })
    .on('close', function () { picking = null; });

  loadTrip(tripId).done(function (t) {
    trip = t;
    loadBoard();
    // 누가 들어오면 미배정 칸이 바뀌니 방 정보도 다시 읽는다
    listen(tripId, function (what) {
      if (what === 'members' || what === 'all') {
        loadTrip(tripId).done(function (t) { trip = t; loadBoard(); });
      } else if (what === 'roles') {
        loadBoard();
        if (travel) {
          travel = null;
          $('#travel').empty().append($('<p class="hint">').text('역할이 바뀌었어요. 다시 계산해 주세요.'));
        }
      }
    });
  });
});
