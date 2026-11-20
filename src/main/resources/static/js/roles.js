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
  renderShortage(isHost);
  $('#btn-card').prop('hidden', !board.assignments.length);

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
  // 정해진 뒤에는 "결과 카드"가 주인공. 다시 추천은 손으로 옮긴 것까지 갈아엎으니 눈에 덜 띄게
  $('#btn-recommend').prop('hidden', !isHost).toggleClass('primary', !board.assignments.length)
    .text(board.assignments.length ? '다시 추천받기' : '설문으로 추천받기');
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
// 역할이 정해진 걸 이 방에서 처음 볼 때 보드에 "확정" 도장이 쾅. 같은 휴대폰에서 다시 열면 안 찍는다
function stampOnce() {
  var key = 'tripmate.stamped.' + tripId;
  try { if (!board.assignments.length || localStorage.getItem(key)) return; localStorage.setItem(key, '1'); } catch (e) { return; }
  if (REDUCE_MOTION) return;
  var stamp = $('<div class="stamp" aria-hidden="true">').text('확정');
  $('#board').css('position', 'relative').append(stamp);
  if (navigator.vibrate && window.matchMedia('(pointer: coarse)').matches) navigator.vibrate([20, 40, 30]);
  setTimeout(function () { stamp.remove(); }, 2200);
}

function loadBoard() {
  return api('GET', '/api/trips/' + tripId + '/roles').done(function (res) {
    board = res;
    render();
    stampOnce();
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

var MAX_ROLES = 12; // 서버 RoleService.MAX_ROLES 와 같다

// 역할 자리(정원 합)보다 사람이 많으면 남는 사람이 생긴다. 방장에게만 한 번에 채우는 단추를 보인다
function renderShortage(isHost) {
  var slots = board.roles.reduce(function (sum, r) { return sum + r.capacity; }, 0);
  var short = trip.members.length - slots;
  var show = isHost && short > 0 && board.roles.length + 2 <= MAX_ROLES;
  $('#roles-short').prop('hidden', !show);
  if (show) $('#roles-short-text').text(short + '명 자리가 모자라요. 역할을 두 개 더하면 모두에게 역할이 생겨요.');
}

// 남는 사람 수만큼 '설거지'와 '방 정리'를 반씩 나눠 더하고(정원은 칸마다 8명까지) 다시 추천받는다. 이미 있는 이름은 뒤에 번호
function fillShortage() {
  var slots = board.roles.reduce(function (sum, r) { return sum + r.capacity; }, 0);
  var short = trip.members.length - slots;
  if (short <= 0) return;
  var names = board.roles.map(function (r) { return r.title; });
  var unique = function (t) { var n = t, i = 2; while (names.indexOf(n) >= 0) n = t + ' ' + i++; names.push(n); return n; };
  var first = Math.min(8, Math.ceil(short / 2)), second = Math.min(8, short - first);
  var add = [{ title: unique('설거지'), capacity: first }];
  if (second > 0) add.push({ title: unique('방 정리'), capacity: second });
  $('#btn-roles-fill').prop('disabled', true).text('나누는 중…');
  var chain = $.when();
  add.forEach(function (r) {
    chain = chain.then(function () {
      return api('POST', '/api/trips/' + tripId + '/roles', { title: r.title, required: false, capacity: r.capacity, tasks: [] });
    });
  });
  chain.then(recommend).always(function () {
    $('#btn-roles-fill').prop('disabled', false).text('설거지·방 정리 더하고 다시 나누기');
  });
}

// POST: /api/trips/1/roles/recommend   방장만. 손으로 옮긴 것까지 지우고 설문으로 다시 짠다
function recommend() {
  $('#btn-recommend').prop('disabled', true).text('계산 중…');
  return api('POST', '/api/trips/' + tripId + '/roles/recommend')
    .done(function (res) {
      board = res;
      render();
    })
    .always(function () { $('#btn-recommend').prop('disabled', false); });
}

// GET: /api/trips/1/roles/travel
// -> { "message": null, "legs": [{ "name", "driver", "km", "minutes", "toll", "fuel", "note" }], "perPerson": 6000,
//      "fuelPrice": 1700, "fuelSource": "어림값", "kmPerLiter": 12, "tips": [] }
var travelTimer = null;
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
    // "운전한 사람도 기름값 내?"를 단톡방에서 따지지 않게 두 기준을 나란히 둔다. 고르는 건 그룹 몫
    // 방 규칙(운전한 사람 기름값)을 정했으면 그쪽 칸을 "우리 규칙"으로 칠한다
    var rule = trip.rules.driverPaysFuel;
    var split = $('<div class="split">').appendTo(box);
    split.append($('<p>').toggleClass('chosen', rule === true)
      .append($('<span class="muted small">').text(rule === true ? '우리 규칙 · 모두 나누면' : '모두 나누면'), $('<b>').text(won(travel.perPerson))));
    if (travel.perPassenger !== null) {
      split.append($('<p>').toggleClass('chosen', rule === false)
        .append($('<span class="muted small">').text(rule === false ? '우리 규칙 · 운전한 사람은 빼고' : '운전한 사람은 빼고'), $('<b>').text(won(travel.perPassenger))));
    }
    box.append($('<p class="muted small">').text('운전하는 차 왕복 기름값·통행료, ' + trip.members.length + '명 기준. 운전자 수고비는 단톡방에서 정해요.'));
  }
  travel.tips.forEach(function (t) { box.append($('<p class="notice">').text(t)); });
  box.append($('<p class="muted small">').text('길·통행료: 카카오모빌리티 · 휘발유 ' + won(travel.fuelPrice) + '/L(' + travel.fuelSource + ') · 연비 ' + travel.kmPerLiter + 'km/L로 셈'));
}

function minutesText(minutes) {
  var h = Math.floor(minutes / 60);
  return (h ? h + '시간 ' : '') + (minutes % 60) + '분';
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
  if (travel && travel.perPerson !== null) {
    // 규칙을 정했으면 그 값 하나만, 안 정했으면 두 기준을 다
    var rule = trip.rules.driverPaysFuel;
    if (rule === false && travel.perPassenger !== null) lines.push('1인 교통비 약 ' + won(travel.perPassenger) + ' (운전한 사람은 빼고, 기름값·통행료 왕복)');
    else if (rule === true) lines.push('1인 교통비 약 ' + won(travel.perPerson) + ' (모두 나눠서, 기름값·통행료 왕복)');
    else lines.push('1인 교통비 약 ' + won(travel.perPerson) + ' (기름값·통행료 왕복)'
      + (travel.perPassenger !== null ? ', 운전자 빼면 ' + won(travel.perPassenger) : ''));
  }
  var rules = rulesText(trip.rules);
  if (rules) lines.push('규칙: ' + rules);
  lines.push('TripMate 에서 정했어요 ' + location.origin + '/join/' + trip.inviteCode);
  return lines.join('\n');
}

function copyResult() {
  shareText(resultText(), '복사했어요. 단톡방에 붙여 넣어 주세요.');
}

// 역할 결과 카드(폭 1080 그림). 글은 단톡방에서 길게 늘어져 안 읽히는데 그림은 한눈에 보이고,
// 아직 설문 안 한 친구도 "나만 없네"로 들어온다. 바닥 주소가 다음 방을 부른다(docs/18 다음에 할 것 2번)
// 역할 이름에 맞는 사진(테스트 카드와 같은 넷). 더한 역할(설거지 등)은 기록형 사진
var ROLE_PHOTO = [['운전', 'drive'], ['총무', 'money'], ['장보기', 'bbq'], ['요리', 'bbq'], ['고기', 'bbq']];
function rolePhoto(title) {
  var hit = ROLE_PHOTO.find(function (p) { return title.indexOf(p[0]) >= 0; });
  return '/img/role/' + (hit ? hit[1] : 'photo') + '.jpg';
}

function loadImage(src) {
  return new Promise(function (resolve, reject) {
    var img = new Image();
    img.onload = function () { resolve(img); };
    img.onerror = reject;
    img.src = src;
  });
}

// 사진을 칸에 꽉 차게 가운데를 잘라 넣는다(object-fit: cover 와 같은 셈)
function drawCover(g, img, x, y, w, h) {
  var scale = Math.max(w / img.width, h / img.height);
  var sw = w / scale, sh = h / scale;
  g.drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, x, y, w, h);
}

// 긴 방 이름·여러 이름은 글자를 줄여 맞춘다. 폭만 줄이면(fillText 의 maxWidth) 영수증 때처럼 옆으로 눌려 찍혔다
function fitText(g, text, x, y, max, weight, size, min, font) {
  while (size > min) {
    g.font = weight + ' ' + size + 'px ' + font;
    if (g.measureText(text).width <= max) break;
    size -= 2;
  }
  g.font = weight + ' ' + size + 'px ' + font;
  g.fillText(text, x, y, max);
}

function drawRoleCard() {
  var W = 1080, pad = 72, top = 560, rowH = 190, font = '"Pretendard Variable", Pretendard, sans-serif';
  var rows = columns().filter(function (col) { return col.role.id !== UNASSIGNED && col.cards.length; });
  var left = trip.members.length - board.assignments.length;
  var H = top + 64 + rows.length * rowH + (left ? 60 : 0) + 250;
  var cover = tripPhoto(trip.destination, trip.startDate);
  var srcs = [cover ? cover.src : '/img/photo/forest-hills.jpg'].concat(rows.map(function (r) { return rolePhoto(r.role.title); }));
  return document.fonts.ready.then(function () { return Promise.all(srcs.map(loadImage)); }).then(function (imgs) {
    var c = document.createElement('canvas'); c.width = W; c.height = H;
    var g = c.getContext('2d');
    g.fillStyle = '#f5f6f8'; g.fillRect(0, 0, W, H);

    // 머리: 여행지 사진 위에 방 이름
    drawCover(g, imgs[0], 0, 0, W, top);
    var grad = g.createLinearGradient(0, top - 360, 0, top);
    grad.addColorStop(0, 'rgba(0,0,0,0)'); grad.addColorStop(1, 'rgba(0,0,0,0.72)');
    g.fillStyle = grad; g.fillRect(0, top - 360, W, 360);
    g.fillStyle = '#ffffff'; g.textAlign = 'left';
    g.font = '600 40px ' + font; g.fillText('우리 여행 역할 발표', pad, top - 170);
    fitText(g, trip.title, pad, top - 82, W - pad * 2 - 200, 800, 76, 48, font);
    var meta = [trip.destination, trip.startDate ? formatPeriod(trip.startDate, trip.endDate) : null, trip.members.length + '명']
      .filter(Boolean).join(' · ');
    g.font = '400 34px ' + font; g.fillStyle = 'rgba(255,255,255,0.86)'; g.fillText(meta, pad, top - 30, W - pad * 2 - 200);

    // 역할마다 한 줄: 동그란 사진, 역할 이름, 사람, 첫 근거
    var y = top + 64;
    rows.forEach(function (row, i) {
      var cx = pad + 64, cy = y + 70;
      g.save(); g.beginPath(); g.arc(cx, cy, 64, 0, Math.PI * 2); g.clip();
      drawCover(g, imgs[i + 1], cx - 64, cy - 64, 128, 128);
      g.restore();
      var tx = pad + 128 + 36, maxW = W - tx - pad;
      g.fillStyle = '#5f6873'; g.font = '600 32px ' + font; g.fillText(row.role.title, tx, y + 34, maxW);
      g.fillStyle = '#1b1f24';
      fitText(g, row.cards.map(function (card) { return card.member.name; }).join(' · '), tx, y + 96, maxW, 800, 52, 34, font);
      var reason = row.cards.map(function (card) { return card.reasons[0]; }).filter(Boolean)[0];
      if (reason) { g.fillStyle = '#0e7c72'; g.font = '600 30px ' + font; g.fillText(reason, tx, y + 144, maxW); }
      if (i < rows.length - 1) { g.fillStyle = '#e3e6ea'; g.fillRect(tx, y + rowH - 12, W - tx - pad, 2); }
      y += rowH;
    });
    if (left) { g.fillStyle = '#5f6873'; g.font = '400 32px ' + font; g.fillText('아직 역할 없는 사람 ' + left + '명', pad, y + 20); }

    // 도장: 정해졌다는 맛. 머리 사진 오른쪽 아래 경계에 비스듬히
    g.save(); g.translate(W - pad - 96, top - 40); g.rotate(-0.21);
    g.fillStyle = 'rgba(245,246,248,0.94)'; g.beginPath(); g.arc(0, 0, 92, 0, Math.PI * 2); g.fill();
    g.strokeStyle = 'rgba(220,53,69,0.9)'; g.lineWidth = 8;
    g.beginPath(); g.arc(0, 0, 80, 0, Math.PI * 2); g.stroke();
    g.fillStyle = 'rgba(220,53,69,0.9)'; g.textAlign = 'center'; g.font = '800 50px ' + font; g.fillText('확정', 0, 18);
    g.restore();

    // 바닥: 바꾸는 법과 주소
    g.textAlign = 'left';
    g.fillStyle = '#5f6873'; g.font = '400 32px ' + font; g.fillText('바꾸고 싶으면 방에서 카드만 옮기면 돼요', pad, H - 170, W - pad * 2);
    g.fillStyle = '#1b1f24'; g.font = '800 44px ' + font; g.fillText('TripMate', pad, H - 96);
    g.fillStyle = '#5f6873'; g.font = '400 32px ' + font; g.fillText('우리도 1분 만에 정해 보기 → ' + location.host, pad, H - 46, W - pad * 2);
    return new Promise(function (resolve) { c.toBlob(resolve, 'image/jpeg', 0.9); }); // 사진이 들어 PNG 는 무거웠다
  });
}

function shareRoleCard() {
  var button = $('#btn-card').prop('disabled', true);
  drawRoleCard().then(function (blob) {
    return shareImage(blob, 'tripmate-roles.jpg', '[' + trip.title + '] 역할 나왔다 ㅋㅋ ' + location.origin + '/trips/' + tripId + '/roles',
      '역할 카드를 저장했어요. 단톡방에 올려 주세요.');
  }).catch(function () { showToast('카드를 만들지 못했어요. 다시 눌러 주세요.'); })
    .finally(function () { button.prop('disabled', false); });
}

$(function () {
  $('#btn-recommend').click(recommend);
  $('#btn-roles-fill').click(fillShortage);
  $('#btn-card').text(shareLabel('카톡으로 결과 카드 보내기', '결과 카드 저장하기')).click(shareRoleCard);
  $('#btn-copy').text(shareLabel('글로 보내기', '글로 복사하기')).click(copyResult);
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
    // 배정이 있으면 교통비도 바로 센다. "계산하기"를 찾아 누르지 않아도 보드 아래에 근거가 있다
    loadBoard().done(function () { if (board.assignments.length) loadTravel(); });
    // 누가 들어오면 미배정 칸이 바뀌니 방 정보도 다시 읽는다
    listen(tripId, function (what) {
      if (what === 'trip') {
        // 방 규칙이 바뀌면 교통비 칸의 "우리 규칙" 표시만 다시 그린다
        loadTrip(tripId).done(function (t) { trip = t; renderTravel(); });
      } else if (what === 'members' || what === 'all') {
        loadTrip(tripId).done(function (t) { trip = t; loadBoard(); });
      } else if (what === 'roles') {
        // 운전이 바뀌면 교통비도 바뀐다. 카드를 연달아 옮길 때마다 길찾기를 부르지 않게 멈춘 뒤 한 번만 센다
        loadBoard();
        clearTimeout(travelTimer);
        travelTimer = setTimeout(loadTravel, 1500);
      }
    });
  });
});
