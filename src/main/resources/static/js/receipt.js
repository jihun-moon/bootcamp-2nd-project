// 우리 여행 영수증. 방·역할·숙소·교통비를 한 번에 읽어 영수증 한 장으로 그리고, 같은 내용을 그림(PNG)으로 만들어 보낸다
// GET /api/trips/1, /api/trips/1/roles, /api/trips/1/stays, /api/trips/1/roles/travel
var tripId = $('body').data('trip-id');
var lines = null; // 화면과 그림이 같이 쓰는 줄: { kind: 'title'|'meta'|'cut'|'row'|'total'|'note', left, right }

function won(n) { return n.toLocaleString('ko-KR') + '원'; }
function nightsOf(trip) {
  if (!trip.startDate || !trip.endDate) return null;
  return Math.max(0, Math.round((new Date(trip.endDate) - new Date(trip.startDate)) / 86400000));
}

// 숙소는 표를 가장 많이 받은 곳(같으면 먼저 올린 곳). 가격이 있으면 1인 숙소비 = 1박 × 박 수 ÷ 인원
function pickStay(stays) {
  return stays.slice().sort(function (a, b) { return b.voterIds.length - a.voterIds.length || a.id - b.id; })[0] || null;
}

function buildLines(trip, board, stays, travel, shopping) {
  var people = trip.members.length;
  var nights = nightsOf(trip);
  var out = [];
  out.push({ kind: 'title', left: 'TripMate 여행 영수증' });
  out.push({ kind: 'meta', left: trip.title });
  out.push({ kind: 'meta', left: [trip.destination || '지역 미정', formatPeriod(trip.startDate, trip.endDate), people + '명'].join(' · ') });
  out.push({ kind: 'cut' });

  // 역할: 칸마다 사람 이름과 첫 근거 하나
  board.roles.forEach(function (role) {
    var picked = board.assignments.filter(function (a) { return a.roleId === role.id; });
    if (!picked.length) return;
    var names = picked.map(function (a) {
      var m = trip.members.find(function (x) { return x.id === a.memberId; });
      return m ? m.name : '';
    }).join('·');
    var reason = (picked[0].reasons || [])[0];
    out.push({ kind: 'row', left: role.title + '  ' + names, right: reason || '' });
  });
  if (!board.assignments.length) out.push({ kind: 'note', left: '역할은 설문이 다 모이면 정해져요' });
  out.push({ kind: 'cut' });

  var total = 0;
  var stay = pickStay(stays);
  if (stay) {
    var stayPart = (stay.price && nights !== null) ? Math.round(stay.price * Math.max(1, nights) / people / 100) * 100 : null;
    // 날짜가 아직이면 1인 몫을 못 세니 아는 만큼(1박 가격)만
    var stayRight = stayPart !== null ? won(stayPart) : (stay.price ? '1박 ' + won(stay.price) : '가격 미정');
    out.push({ kind: 'row', left: '숙소  ' + stay.title, right: stayRight });
    if (stayPart !== null) total += stayPart;
  }
  if (travel && travel.perPerson !== null) {
    var exempt = trip.rules.driverPaysFuel === false && travel.perPassenger !== null;
    var move = exempt ? travel.perPassenger : travel.perPerson;
    out.push({ kind: 'row', left: '교통  기름값·통행료' + (exempt ? '(운전자 빼고)' : ''), right: won(move) });
    total += move;
  }
  // 장보기: 산 사람들이 적은 쓴 돈 합 ÷ 인원(서버가 100원 단위로 올림)
  if (shopping && shopping.spent) {
    out.push({ kind: 'row', left: '장보기  ' + shopping.items.filter(function (i) { return i.buyerId; }).length + '줄', right: won(shopping.perPerson) });
    total += shopping.perPerson;
  }
  if (total) out.push({ kind: 'total', left: '1인 합계', right: won(total) });
  else out.push({ kind: 'note', left: '숙소 가격과 운전이 정해지면 1인 합계가 찍혀요' });

  var rules = rulesText(trip.rules);
  if (rules) { out.push({ kind: 'cut' }); out.push({ kind: 'note', left: '규칙 · ' + rules }); }
  out.push({ kind: 'cut' });
  out.push({ kind: 'note', left: '단톡방 싸움 0회 · 정하는 데 걸린 시간 1분' });
  out.push({ kind: 'note', left: '발행 ' + new Date().toLocaleDateString('ko-KR') + ' · tripmate' });
  return out;
}

function renderReceipt() {
  var box = $('#receipt').empty().removeClass('printing');
  lines.forEach(function (l) {
    if (l.kind === 'cut') { box.append('<hr class="receipt-cut">'); return; }
    var row = $('<p>').addClass('r-' + l.kind).append($('<span>').text(l.left));
    if (l.right) row.append($('<b>').text(l.right));
    box.append(row);
  });
  void box[0].offsetWidth; // 다 그린 뒤 처음부터 뽑히게 애니메이션을 다시 건다
  box.addClass('printing');
}

// 그림(PNG): 화면의 영수증과 같은 줄을 canvas 에 그린다. 라이브러리 없이, 글꼴은 이미 받아 둔 Pretendard
function drawPng() {
  var W = 720, pad = 48, lh = { title: 64, meta: 40, row: 52, total: 72, note: 40, cut: 36 };
  var font = '"Pretendard Variable", Pretendard, sans-serif';
  var c = document.createElement('canvas').getContext('2d');
  // 메모 줄이 그림 폭을 넘으면 낱말 단위로 줄을 나눈다(글자를 눌러 담지 않게)
  c.font = '400 22px ' + font;
  var drawn = [];
  lines.forEach(function (l) {
    if (l.kind !== 'note' || c.measureText(l.left).width <= W - pad * 2) { drawn.push(l); return; }
    var cur = '';
    l.left.split(' ').forEach(function (word) {
      var next = cur ? cur + ' ' + word : word;
      if (c.measureText(next).width > W - pad * 2 && cur) { drawn.push({ kind: 'note', left: cur }); cur = word; }
      else cur = next;
    });
    if (cur) drawn.push({ kind: 'note', left: cur });
  });
  var H = pad * 2 + drawn.reduce(function (h, l) { return h + lh[l.kind]; }, 0) + 24;
  var canvas = c.canvas;
  canvas.width = W; canvas.height = H;
  c.fillStyle = '#ffffff'; c.fillRect(0, 0, W, H);
  // 아래 톱니
  c.fillStyle = '#0f1214';
  for (var x = 0; x < W; x += 24) { c.beginPath(); c.moveTo(x, H); c.lineTo(x + 12, H - 12); c.lineTo(x + 24, H); c.fill(); }
  var y = pad;
  drawn.forEach(function (l) {
    y += lh[l.kind];
    var base = y - lh[l.kind] / 3;
    if (l.kind === 'cut') {
      c.strokeStyle = '#c4cad1'; c.setLineDash([8, 8]); c.beginPath(); c.moveTo(pad, base); c.lineTo(W - pad, base); c.stroke(); c.setLineDash([]);
      return;
    }
    var size = { title: 34, meta: 24, row: 26, total: 34, note: 22 }[l.kind];
    var weight = (l.kind === 'title' || l.kind === 'total') ? 800 : (l.kind === 'row' ? 600 : 400);
    c.font = weight + ' ' + size + 'px ' + font;
    c.fillStyle = (l.kind === 'meta' || l.kind === 'note') ? '#5f6873' : '#1b1f24';
    // 제목·날짜·메모는 화면처럼 가운데, 줄(역할·금액)은 왼쪽
    var center = l.kind === 'title' || l.kind === 'meta' || l.kind === 'note';
    c.textAlign = center ? 'center' : 'left';
    c.fillText(l.left, center ? W / 2 : pad, base, l.right ? W - pad * 2 - 220 : W - pad * 2);
    if (l.right) {
      c.textAlign = 'right';
      c.fillStyle = l.kind === 'total' ? '#0e7c72' : '#1b1f24';
      c.font = (l.kind === 'total' ? 800 : 500) + ' ' + size + 'px ' + font;
      c.fillText(l.right, W - pad, base, 220);
    }
  });
  return new Promise(function (resolve) { canvas.toBlob(resolve, 'image/png'); });
}

// 휴대폰: 공유 창에 그림 파일째로(카톡 고르면 사진으로 간다). 그림 공유가 안 되는 곳(PC·카톡 안 브라우저)은 그림을 저장한다
function shareReceipt() {
  $('#btn-receipt-share').prop('disabled', true);
  document.fonts.ready.then(drawPng).then(function (blob) {
    var file = new File([blob], 'tripmate-receipt.png', { type: 'image/png' });
    if (CAN_SHARE && navigator.canShare && navigator.canShare({ files: [file] })) {
      return navigator.share({ files: [file], text: '우리 여행 영수증 나왔다 ㅋㅋ' }).catch(function () {});
    }
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'tripmate-receipt.png';
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
    showToast('영수증 그림을 저장했어요. 단톡방에 올려 주세요.');
  }).finally(function () { $('#btn-receipt-share').prop('disabled', false); });
}

$(function () {
  $('#btn-receipt-share').text(shareLabel('카톡으로 영수증 보내기', '영수증 그림 저장하기')).click(shareReceipt);
  loadTrip(tripId).done(function (trip) {
    // 교통비는 길찾기를 부르니 느리거나 안 될 수 있다. 실패해도 나머지로 영수증을 뽑는다
    var travel = $.Deferred();
    api('GET', '/api/trips/' + tripId + '/roles/travel')
      .done(function (data) { travel.resolve(data); })   // .done(travel.resolve) 는 (값, 상태, xhr) 셋을 넘겨 배열이 된다
      .fail(function () { travel.resolve(null); });
    $.when(api('GET', '/api/trips/' + tripId + '/roles'), api('GET', '/api/trips/' + tripId + '/stays'), travel,
      api('GET', '/api/trips/' + tripId + '/shopping'))
      .done(function (roles, stays, t, shop) {
        lines = buildLines(trip, roles[0], stays[0].stays, t && t.message ? null : t, shop[0]);
        renderReceipt();
      });
  });
});
