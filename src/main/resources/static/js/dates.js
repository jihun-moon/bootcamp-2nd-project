// 날짜 맞추기: 되는 날 달력(여러 날 누르기)과 좋은 날짜 후보 셈. 개요(trip.js)와 설문(survey.js)이 같이 쓴다
// GET  /api/trips/1/dates            -> { "memberCount": 8, "responded": 5, "available": { "2027-01-15": [1, 2, 5] } }
// PUT  /api/trips/1/members/2/dates  { "dates": ["2027-01-15", "2027-01-16"] }

function isoDate(d) {
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}
function addDays(key, n) {
  var d = new Date(key + 'T00:00:00');
  d.setDate(d.getDate() + n);
  return isoDate(d);
}

// 한 달 달력. mine 은 내가 고른 날(Set), available 은 날짜별 되는 사람. 많이 겹친 날일수록 진하게(4단계), 숫자는 되는 사람 수
function availabilityCalendar(el, opts) {
  var shown = opts.month || new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  var draw = function () {
    el.empty().addClass('cal');
    var y = shown.getFullYear(), m = shown.getMonth();
    var now = new Date();
    var todayKey = isoDate(new Date(now.getFullYear(), now.getMonth(), now.getDate()));
    var head = $('<div class="cal-head">').appendTo(el);
    head.append(
      $('<button type="button" class="cal-nav" aria-label="이전 달">‹</button>')
        .prop('disabled', y === now.getFullYear() && m === now.getMonth())
        .click(function () { shown = new Date(y, m - 1, 1); draw(); }),
      $('<span class="strong">').text(y + '년 ' + (m + 1) + '월'),
      $('<button type="button" class="cal-nav" aria-label="다음 달">›</button>')
        .click(function () { shown = new Date(y, m + 1, 1); draw(); }));
    var grid = $('<div class="cal-grid">').appendTo(el);
    ['일', '월', '화', '수', '목', '금', '토'].forEach(function (w) { $('<span class="cal-week">').text(w).appendTo(grid); });
    for (var i = 0; i < new Date(y, m, 1).getDay(); i++) $('<span>').appendTo(grid);
    for (var d = 1; d <= new Date(y, m + 1, 0).getDate(); d++) {
      var date = new Date(y, m, d), key = isoDate(date);
      var count = (opts.available[key] || []).length;
      var heat = count ? Math.ceil(count / opts.total * 4) : 0;
      var cell = $('<button type="button" class="cal-day avail">').attr('data-date', key).attr('data-heat', heat)
        .attr('aria-pressed', opts.mine.has(key))
        .attr('aria-label', (m + 1) + '월 ' + d + '일' + (count ? ', ' + count + '명 가능' : ''))
        .prop('disabled', key < todayKey || !opts.editable && !count)
        .toggleClass('sat', date.getDay() === 6).toggleClass('sun', date.getDay() === 0)
        .append($('<span>').text(d), count ? $('<small>').text(count) : null);
      if (opts.editable) {
        cell.click(function () {
          var k = $(this).data('date');
          if (opts.mine.has(k)) opts.mine.delete(k); else opts.mine.add(k);
          $(this).attr('aria-pressed', opts.mine.has(k));
          opts.onChange(opts.mine);
        });
      }
      grid.append(cell);
    }
  };
  draw();
}

// 좋은 날짜 후보: 2박 3일·1박 2일·당일 창을 모두 보고, 그 날들에 다 되는 사람이 많은 순(같으면 긴 여행, 이른 날).
// 사람 8명 × 날 60개라 그냥 다 센다
function bestWindows(board, members, wanted) {
  var days = Object.keys(board.available).sort();
  var found = [];
  [3, 2, 1].forEach(function (len) {
    days.forEach(function (start) {
      var ok = members.filter(function (m) {
        for (var i = 0; i < len; i++) {
          if ((board.available[addDays(start, i)] || []).indexOf(m.id) < 0) return false;
        }
        return true;
      });
      if (ok.length) found.push({ start: start, end: addDays(start, len - 1), nights: len - 1, ok: ok,
        missing: members.filter(function (m) { return ok.indexOf(m) < 0; }) });
    });
  });
  found.sort(function (a, b) { return b.ok.length - a.ok.length || b.nights - a.nights || (a.start < b.start ? -1 : 1); });
  // 같은 시작일의 짧은 창은 긴 창에 묻히니 시작일이 겹치지 않게 고른다
  var picked = [];
  found.forEach(function (w) {
    if (picked.length < (wanted || 3) && !picked.some(function (p) { return w.start <= p.end && w.end >= p.start; })) picked.push(w);
  });
  return picked;
}
