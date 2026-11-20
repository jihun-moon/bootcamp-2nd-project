// 장보기: 양은 서버가 센다(ShoppingItem). 화면은 "내가 살게"와 쓴 돈만 받는다. 누가 맡으면 SSE(shopping)로 모두의 화면이 바뀐다
// GET /api/trips/1/shopping -> { people, nights, drinkers, meatGrams, items: [{ key, label, group, quantity, buyerId, buyerName, amount }], spent, perPerson }
var tripId = $('body').data('trip-id');
var trip = null;
var board = null;

function me() { return trip.members.find(function (m) { return m.id === trip.meId; }); }

function render() {
  $('#shop-basis').text(board.people + '명 · ' + (board.nights ? board.nights + '박' : '당일') + ' 기준');
  renderPlan();
  var list = $('#shop-list').empty();
  var lastGroup = null;
  board.items.forEach(function (item) {
    if (item.group !== lastGroup) { list.append($('<li class="shop-group">').text(item.group)); lastGroup = item.group; }
    var row = $('<li class="shop-row">').toggleClass('taken', !!item.buyerId).toggleClass('mine', item.buyerId === trip.meId);
    row.append($('<div>').append($('<span class="strong">').text(item.label), $('<b class="shop-qty">').text(item.quantity)));
    row.append(claimPart(item));
    list.append(row);
  });
  var claimed = board.items.filter(function (i) { return i.buyerId; }).length;
  $('#shop-spent').text(board.spent ? won(board.spent) + ' · 1인 ' + won(board.perPerson) : '아직 없음');
  $('#shop-who').text(claimed + '/' + board.items.length + '줄 맡음'
    + (claimed < board.items.length ? ' · 안 맡은 줄은 "내가 살게"를 눌러 주세요' : ' · 다 맡았어요'));
}

// 방장은 −/+ 로 바꾸고, 참여자는 읽기만
function renderPlan() {
  var host = me().host;
  var box = $('#shop-plan').empty();
  var stepper = function (label, value, unit, step, min, max, key) {
    var row = $('<div class="between">').append($('<span>').text(label));
    if (!host) return row.append($('<b>').text(value + unit));
    var change = function (d) {
      var next = Math.min(max, Math.max(min, value + d));
      if (next === value) return;
      var body = { drinkers: board.drinkers, meatGrams: board.meatGrams };
      body[key] = next;
      api('PUT', '/api/trips/' + tripId + '/shopping/plan', body).done(function (b) { board = b; render(); });
    };
    return row.append($('<div class="stepper">').append(
      $('<button type="button" class="btn">').text('−').attr('aria-label', label + ' 줄이기').click(function () { change(-step); }),
      $('<b>').text(value + unit),
      $('<button type="button" class="btn">').text('+').attr('aria-label', label + ' 늘리기').click(function () { change(step); })));
  };
  box.append(stepper('술 마시는 사람', board.drinkers, '명', 1, 0, board.people, 'drinkers'),
    stepper('1인 고기', board.meatGrams, 'g', 50, 100, 600, 'meatGrams'));
}

// 줄 오른쪽: 아무도 안 맡았으면 "내가 살게"(챙겨 갈 것은 "내가 챙길게"), 내가 맡았으면 쓴 돈 칸과 내려놓기, 남이 맡았으면 이름
function claimPart(item) {
  var url = '/api/trips/' + tripId + '/shopping/items/' + item.key;
  if (!item.buyerId) {
    return $('<button type="button" class="btn">').text(item.bring ? '내가 챙길게' : '내가 살게').click(function () {
      api('PUT', url, { amount: null }).done(function (b) {
        board = b;
        render();
        delight($('.shop-row.mine').filter(function () { return $(this).text().indexOf(item.label) >= 0; }));
      })
        .fail(function (xhr) { if (xhr.status === 409) { showToast('방금 다른 사람이 맡았어요.'); load(); } });
    });
  }
  if (item.buyerId !== trip.meId) {
    var part = $('<span class="muted small">').text(withSubject(item.buyerName) + (item.bring ? ' 챙겨요' : ' 사요')
      + (item.amount ? ' · ' + won(item.amount) : ''));
    if (!me().host) return part;
    return $('<div class="row">').append(part, $('<button type="button" class="link small">').text('풀기').click(function () {
      api('DELETE', url).done(function (b) { board = b; render(); });
    }));
  }
  // 챙겨 갈 것은 돈이 안 드니 내려놓기만
  if (item.bring) {
    return $('<div class="row">').append($('<span class="muted small">').text('내가 챙겨요'),
      $('<button type="button" class="link small">').text('내려놓기').click(function () {
        api('DELETE', url).done(function (b) { board = b; render(); });
      }));
  }
  // 산 뒤 금액을 적으면 칸을 벗어날 때 저장(엔터도)
  var input = $('<input type="number" min="0" max="10000000" step="1" inputmode="numeric" class="shop-amount">')
    .attr('placeholder', '쓴 돈').attr('aria-label', item.label + ' 쓴 돈').val(item.amount || '');
  var save = function () {
    var v = input.val() === '' ? null : Number(input.val());
    if (v === item.amount) return;
    api('PUT', url, { amount: v }).done(function (b) { board = b; render(); showToast('적었어요.'); });
  };
  input.on('change', save).on('keydown', function (e) { if (e.key === 'Enter') this.blur(); });
  return $('<div class="row">').append(input, $('<button type="button" class="link small">').text('내려놓기').click(function () {
    api('DELETE', url).done(function (b) { board = b; render(); });
  }));
}

// 카톡 글: 줄마다 양과 맡은 사람. 안 맡은 줄은 "누가?"로 남겨 단톡방에서 손 들게
function shareList() {
  var lines = ['[' + trip.title + '] 장보기 (' + board.people + '명 · ' + (board.nights ? board.nights + '박' : '당일') + ')'];
  board.items.forEach(function (i) { lines.push('· ' + i.label + ' ' + i.quantity + ' — ' + (i.buyerName || '누가?')); });
  lines.push('맡을 사람은 여기서 "내가 살게"·"내가 챙길게" ' + location.origin + '/join/' + trip.inviteCode);
  shareText(lines.join('\n'), '장보기 목록을 복사했어요. 단톡방에 붙여 넣어 주세요.');
}

function load() {
  return api('GET', '/api/trips/' + tripId + '/shopping').done(function (b) { board = b; render(); });
}

$(function () {
  $('#btn-shop-share').text(shareLabel('카톡으로 장보기 목록 보내기', '장보기 목록 복사하기')).click(shareList);
  loadTrip(tripId).done(function (t) {
    trip = t;
    load();
    listen(tripId, function (what) {
      if (what === 'members' || what === 'all' || what === 'trip') loadTrip(tripId).done(function (t) { trip = t; load(); });
      else if (what === 'shopping') load();
    });
  });
});
