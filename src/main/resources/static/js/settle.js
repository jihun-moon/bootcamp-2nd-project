// 다녀와서 "누가 누구에게 얼마". 먼저 낸 돈을 적으면 보낼 줄이 바로 나온다. 장보기 "내가 살게"에 적은 돈은 저절로 들어온다.
// GET    /api/trips/1/settlement
// POST   /api/trips/1/expenses      { "kind": "FUEL", "memo": "주유 + 톨비", "amount": 64000 }
// DELETE /api/trips/1/expenses/3
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
    list.append($('<li class="settled muted">').append($('<span>').text('모두 딱 맞게 냈어요. 보낼 돈이 없어요.')));
  }
  settledStamp(board.lines.length > 0 && !board.transfers.length);
  board.transfers.forEach(function (t) {
    var mine = t.fromId === trip.meId || t.toId === trip.meId;
    $('<li class="settle-row">').toggleClass('mine', mine).append(
      $('<span>').append($('<b>').text(t.fromName), $('<span class="arrow" aria-hidden="true">').text(' → '), $('<b>').text(t.toName)),
      $('<b class="settle-amount">').text(won(t.amount))).appendTo(list);
  });
  $('#btn-settle-share').prop('hidden', !board.transfers.length);

  var lines = $('#settle-lines').empty();
  board.lines.forEach(function (l) {
    var row = $('<li class="between">').append($('<span>').text(l.payerName + ' · ' + l.label), $('<span>').text(won(l.amount)));
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

function settleText(trip) {
  var rows = settle.transfers.map(function (t) { return t.fromName + ' → ' + t.toName + ' ' + won(t.amount); });
  return '[' + trip.title + ' 정산] 모두 ' + won(settle.total) + '\n' + rows.join('\n')
    + '\n토스·카카오페이로 보내 주세요. 자세히 → ' + location.origin + '/trips/' + tripId + '/receipt';
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
  $('#settle-form').on('submit', function (e) {
    e.preventDefault();
    var amount = Number($('#settle-amount').val());
    if (!amount || amount < 1) { $('#settle-amount').focus(); return; }
    var button = $(this).find('button[type="submit"]').prop('disabled', true);
    api('POST', '/api/trips/' + tripId + '/expenses', { kind: settleKind, memo: $('#settle-memo').val() || null, amount: amount })
      .done(function (b) {
        renderSettle(b, trip);
        $('#settle-amount, #settle-memo').val('');
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
  // 누가 돈을 적거나 장보기 금액을 고치면 모두의 화면이 다시 센다. 사람·규칙이 바뀌면(술 표시, 나가기) 방부터 다시 읽는다
  listen(tripId, function (kind) {
    if (kind === 'members' || kind === 'trip' || kind === 'all') loadTrip(tripId).done(function (t) { trip = t; loadSettle(t); });
    else if (kind !== 'roles' && kind !== 'stays') loadSettle(trip);
  });
}
