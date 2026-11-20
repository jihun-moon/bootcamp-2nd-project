// 다녀와서 "누가 누구에게 얼마". 먼저 낸 돈을 적으면 보낼 줄이 바로 나온다. 장보기 "내가 살게"에 적은 돈은 저절로 들어온다.
// GET    /api/trips/1/settlement
// POST   /api/trips/1/expenses      { "kind": "FUEL", "memo": "주유 + 톨비", "amount": 64000 }
// DELETE /api/trips/1/expenses/3
// POST   /api/trips/1/payments      { "fromId": 2, "toId": 1, "amount": 20000 }   보냈어요/받았어요
// DELETE /api/trips/1/payments/4
var settle = null;
var settleKind = 'STAY';
var SETTLE_KINDS = [['STAY', '숙소'], ['FUEL', '주유·톨비'], ['FOOD', '식비'], ['DRINK', '술'], ['OTHER', '기타']];

function renderSettle(board, trip) {
  settle = board;
  $('#settle-total').text(board.total ? '모두 ' + won(board.total) : '');
  var list = $('#settle-transfers').empty();
  if (!board.lines.length) {
    list.append($('<li class="muted">').text('먼저 낸 돈을 적으면 누가 누구에게 얼마 보낼지 바로 나와요.'));
  } else if (!board.transfers.length) {
    list.append($('<li class="settled muted">').append($('<span>').text(board.payments.length
      ? '다 보냈어요. 이번 여행 정산 끝!' : '모두 딱 맞게 냈어요. 보낼 돈이 없어요.')));
  }
  settledStamp(board.lines.length > 0 && !board.transfers.length);
  var me = trip.members.find(function (m) { return m.id === trip.meId; });
  board.transfers.forEach(function (t) {
    var mine = t.fromId === trip.meId || t.toId === trip.meId;
    // 한 번만 누르면 된다: 보내는 사람은 "보냈어요", 받는 사람은 "받았어요". 방장은 대신 눌러 줄 수 있다
    var label = t.fromId === trip.meId ? '보냈어요' : t.toId === trip.meId ? '받았어요' : me && me.host ? '받음 표시' : null;
    var right = $('<span class="settle-right">').append($('<b class="settle-amount">').text(won(t.amount)));
    if (label) {
      right.append($('<button type="button" class="btn small settle-done">').text(label).click(function () {
        var button = $(this).prop('disabled', true);
        api('POST', '/api/trips/' + tripId + '/payments', { fromId: t.fromId, toId: t.toId, amount: t.amount })
          .done(function (b) { renderSettle(b, trip); showToast(t.fromName + ' → ' + t.toName + ' 끝. 남은 줄에서 뺐어요.'); })
          .fail(function () { button.prop('disabled', false); });
      }));
    }
    $('<li class="settle-row">').toggleClass('mine', mine).append(
      $('<span>').append($('<b>').text(t.fromName), $('<span class="arrow" aria-hidden="true">').text(' → '), $('<b>').text(t.toName)),
      right).appendTo(list);
  });

  // 끝난 송금: 누가 눌렀는지와 되돌리기(누른 사람, 보낸·받은 사람, 방장)
  var paid = $('#settle-paid').empty().prop('hidden', !board.payments.length);
  if (board.payments.length) paid.append($('<li class="muted">').text('끝난 송금'));
  board.payments.forEach(function (p) {
    var row = $('<li class="between">').append($('<span>').text('✓ ' + p.fromName + ' → ' + p.toName + ' ' + won(p.amount)
      + ' · ' + withSubject(p.markedByName) + (p.markedById === p.toId ? ' 받았어요' : p.markedById === p.fromId ? ' 보냈어요' : ' 표시했어요')));
    var can = [p.fromId, p.toId, p.markedById].indexOf(trip.meId) >= 0 || (me && me.host);
    if (can) {
      row.append($('<button type="button" class="link small">').text('되돌리기').click(function () {
        api('DELETE', '/api/trips/' + tripId + '/payments/' + p.id).done(function (b) { renderSettle(b, trip); });
      }));
    }
    paid.append(row);
  });
  $('#btn-settle-share').prop('hidden', !board.transfers.length);

  var lines = $('#settle-lines').empty();
  board.lines.forEach(function (l) {
    var row = $('<li class="between">').append($('<span>').text(l.payerName + ' · ' + l.label + (l.sharedBy ? ' · ' + l.sharedBy + '명만' : '')),
      $('<span>').text(won(l.amount)));
    // 장보기 줄은 장보기 화면에서 고친다. 내가 적은 줄과 방장은 지울 수 있다
    var me = trip.members.find(function (m) { return m.id === trip.meId; });
    if (!l.shopping && (l.payerId === trip.meId || me.host)) {
      row.append($('<button type="button" class="link small">').text('지우기').attr('aria-label', l.label + ' 지우기').click(function () {
        api('DELETE', '/api/trips/' + tripId + '/expenses/' + l.id).done(function (b) { renderSettle(b, trip); });
      }));
    }
    lines.append(row);
  });
  var rules = [];
  if (board.driverPaysFuel === false) rules.push('기름값·톨비는 운전한 사람 빼고');
  if (board.drinksSeparate === true) rules.push('술은 마신 사람끼리');
  if (board.refundNoShow === false) rules.push('숙소는 빠진 사람 몫까지');
  $('#settle-rule').text(rules.length ? '우리 규칙: ' + rules.join(', ') + ' 나눠요.' : '');
  renderDrinkers(board, trip);
}

// 규칙 "술값은 마신 사람만"일 때만 보인다. 누를 때마다 바로 저장하고 보낼 줄을 다시 센다.
// 내 것은 내가, 남의 것은 방장이(못 온 사람 대신) 바꾼다
// PUT /api/trips/1/members/2/drinks  { "drinks": false }
function renderDrinkers(board, trip) {
  var box = $('#settle-drinkers').prop('hidden', board.drinksSeparate !== true);
  if (board.drinksSeparate !== true) return;
  var me = trip.members.find(function (m) { return m.id === trip.meId; });
  var list = $('#settle-drinkers-list').empty();
  trip.members.forEach(function (m) {
    var mine = m.id === trip.meId;
    $('<button type="button" class="toggle">').text(m.name)
      .attr('aria-pressed', m.drinks).attr('aria-label', m.name + (m.drinks ? ' 마심' : ' 안 마심'))
      .prop('disabled', !mine && !me.host)
      .click(function () {
        var button = $(this).prop('disabled', true);
        api('PUT', '/api/trips/' + tripId + '/members/' + m.id + '/drinks', { drinks: !m.drinks }).done(function (t) {
          delight(button[0]);
          settleTripChanged(t);
        }).always(function () { button.prop('disabled', false); });
      }).appendTo(list);
  });
  box.find('.muted').text(me.host ? '누르면 바뀌어요. 안 마신 사람을 꺼 주세요.' : '내 것만 바꿀 수 있어요.');
}

function settledStamp(on) {
  var box = $('#settle-transfers .settled');
  if (!on || !box.length) return;
  var key = 'tripmate.settled.' + tripId, first = false;
  try { first = !localStorage.getItem(key); localStorage.setItem(key, '1'); } catch (e) { /* 도장 연출만 매번 */ }
  var stamp = $('<div class="stamp keep" aria-hidden="true">').text('정산 끝').toggleClass('quiet', !first || REDUCE_MOTION).appendTo(box);
  if (first && navigator.vibrate && window.matchMedia('(pointer: coarse)').matches) navigator.vibrate([20, 40, 30]);
  return stamp;
}

// 끝의 주소는 정산 받는 링크(/s/코드). 카톡 안 브라우저처럼 로그인이 안 이어진 곳에서도 금액이 바로 보인다.
// 코드를 아직 못 받았으면(첫 응답 전) 방 영수증 주소로
var settleCode = null;
function settleText(trip) {
  var rows = settle.transfers.map(function (t) { return t.fromName + ' → ' + t.toName + ' ' + won(t.amount); });
  var link = settleCode ? location.origin + '/s/' + settleCode : location.origin + '/trips/' + tripId + '/receipt';
  return '[' + trip.title + ' 정산] 모두 ' + won(settle.total) + '\n' + rows.join('\n')
    + '\n토스·카카오페이로 보내 주세요. 로그인 없이 보기 → ' + link;
}

// PUT: /api/trips/1/settlement/link -> { "code": "..." }   몇 번 불러도 같은 코드. 둘이 동시에 처음 만들면 409 라 한 번 더
function loadSettleCode(retry) {
  api('PUT', '/api/trips/' + tripId + '/settlement/link').done(function (r) { settleCode = r.code; })
    .fail(function (xhr) { if (xhr.status === 409 && !retry) loadSettleCode(true); });
}

// 술 표시처럼 내가 방을 바꿨을 때: 바뀐 방으로 다시 센다(SSE 로도 오지만 내 화면은 바로)
var settleTripChanged = function (t) { loadSettle(t); };

function loadSettle(trip) {
  return api('GET', '/api/trips/' + tripId + '/settlement').done(function (b) { renderSettle(b, trip); });
}

function initSettle(trip) {
  var kinds = $('#settle-kind').empty();
  SETTLE_KINDS.forEach(function (k) {
    $('<button type="button">').text(k[1]).attr('aria-pressed', k[0] === settleKind).click(function () {
      settleKind = k[0];
      kinds.children().attr('aria-pressed', false);
      $(this).attr('aria-pressed', true);
    }).appendTo(kinds);
  });
  // 나눌 사람: null 이면 모두(규칙대로). 펼치면 모두 눌린 채로 시작하고, 뺄 사람만 누른다
  var sharers = null;
  var whoLabel = function () {
    var all = !sharers || sharers.size === trip.members.length;
    $('#settle-who').text('나눌 사람: ' + (all ? '모두' : sharers.size + '명만'));
  };
  var drawSharers = function () {
    var box = $('#settle-sharers').empty();
    trip.members.forEach(function (m) {
      $('<button type="button" class="toggle">').text(m.name).attr('aria-pressed', sharers.has(m.id)).click(function () {
        if (sharers.has(m.id) && sharers.size > 1) sharers.delete(m.id); else sharers.add(m.id);
        $(this).attr('aria-pressed', sharers.has(m.id));
        whoLabel();
      }).appendTo(box);
    });
  };
  $('#settle-who').click(function () {
    var open = $('#settle-sharers').prop('hidden');
    if (open && !sharers) sharers = new Set(trip.members.map(function (m) { return m.id; }));
    if (open) drawSharers();
    $('#settle-sharers').prop('hidden', !open);
    $(this).attr('aria-expanded', open);
  });

  $('#settle-form').on('submit', function (e) {
    e.preventDefault();
    var amount = Number($('#settle-amount').val());
    if (!amount || amount < 1) { $('#settle-amount').focus(); return; }
    var button = $(this).find('button[type="submit"]').prop('disabled', true);
    var picked = sharers && sharers.size < trip.members.length ? Array.from(sharers) : null;
    api('POST', '/api/trips/' + tripId + '/expenses', { kind: settleKind, memo: $('#settle-memo').val() || null, amount: amount, sharerIds: picked })
      .done(function (b) {
        renderSettle(b, trip);
        $('#settle-amount, #settle-memo').val('');
        sharers = null;   // 다음 돈은 다시 모두로
        $('#settle-sharers').prop('hidden', true);
        $('#settle-who').attr('aria-expanded', false);
        whoLabel();
        delight($('#settle-transfers')[0]);
        showToast('적었어요. 보낼 돈을 다시 셌어요.');
      })
      .always(function () { button.prop('disabled', false); });
  });
  $('#btn-settle-share').text(shareLabel('카톡으로 정산 보내기', '정산 글 복사하기')).click(function () {
    shareText(settleText(trip), '정산 글을 복사했어요. 단톡방에 붙여 넣어 주세요.');
  });
  settleTripChanged = function (t) { trip = t; loadSettle(t); };
  loadSettle(trip);
  // 공유 단추를 누른 뒤 서버를 기다리면 휴대폰 공유 창이 "사용자가 누른 것"으로 안 쳐져 막힌다. 그래서 미리 받아 둔다
  loadSettleCode(false);
  // 누가 돈을 적거나 장보기 금액을 고치면 모두의 화면이 다시 센다. 사람·규칙이 바뀌면(술 표시, 나가기) 방부터 다시 읽는다
  listen(tripId, function (kind) {
    if (kind === 'members' || kind === 'trip' || kind === 'all') loadTrip(tripId).done(function (t) { trip = t; loadSettle(t); });
    else if (kind !== 'roles' && kind !== 'stays') loadSettle(trip);
  });
}
