// 방 개요: 다음 할 일, 초대 링크, 설문 진행과 참여자. 누가 들어오거나 설문을 끝내면(SSE members) 다시 그린다
var tripId = $('body').data('trip-id');
var trip = null;

function render(t) {
  trip = t;
  var me = trip.members.find(function (m) { return m.id === trip.meId; });
  var notYet = trip.members.filter(function (m) { return !m.surveyDone; });
  var done = trip.members.length - notYet.length;

  $('#invite-link').val(location.origin + '/join/' + trip.inviteCode);
  var photo = tripPhoto(trip.destination, trip.startDate);
  var cover = $('.ov-cover img');
  if (photo && cover.attr('src') !== photo.src) cover.attr({ src: photo.src, alt: photo.alt });
  renderDday();
  $('#survey-count').text('설문 ' + done + '/' + trip.members.length + '명');
  $('#survey-bar').css('width', (done / trip.members.length * 100) + '%');
  renderNext(me, notYet);
  renderProgress(notYet);
  renderMeet(me);
  renderRename(me);
  renderDraw();
  renderRules(me);
  renderDates(me);

  var list = $('#members').empty();
  trip.members.forEach(function (m) {
    var name = $('<div class="strong">').text(m.name);
    if (m.host) name.append($('<span class="tag">').text('방장'));
    if (m.id === trip.meId) name.append($('<span class="tag me">').text('나'));
    var info = $('<div>').append(name,
      $('<span class="chip">').toggleClass('on', m.surveyDone).text(m.surveyDone ? '설문 완료' : '설문 전'));
    var row = $('<li class="row">').append(avatar(m.name, m.imageUrl), info).appendTo(list);
    // 방장은 못 오게 된 사람을 뺄 수 있다(빠진 사람이 남아 있으면 1인 금액이 다 틀린다)
    if (me.host && m.id !== me.id) {
      row.append($('<span class="member-acts">').append(
        $('<button type="button" class="link small">').text('방장 주기').attr('aria-label', m.name + ' 님에게 방장 넘기기')
          .click(function () { handOver(m); }),
        $('<button type="button" class="link small">').text('빼기').attr('aria-label', m.name + ' 님 빼기')
          .click(function () { leaveTrip(m, false); })));
    }
  });
  // 여행 전에도 "이 멤버로 또" 를 열 수 있게 사람 상자 끝에 한 줄. 두 명 이상일 때만(혼자면 같은 멤버가 없다)
  // 혼자 남은 방은 나갈 수 없다(서버도 막는다)
  $('#leave-trip').prop('hidden', trip.members.length < 2).off('click').click(function () { leaveTrip(me, true); });
  var again = $('#next-trip').empty().prop('hidden', trip.members.length < 2);
  if (trip.nextTripId) again.append($('<a>').attr('href', '/trips/' + trip.nextTripId).text('2탄 방 보기 →'));
  else again.append($('<button type="button" class="link">').text('이 멤버 그대로 다음 여행 열기 →')
    .click(function () { startNextTrip(tripId, this); }));
}

// 내 상태에 따라 할 일 하나를 크게 보여 준다: 혼자면 초대 → 내 설문 → (방장) 안 한 사람 부르기 → 결과 보기
function renderNext(me, notYet) {
  var actions = $('#next-actions').empty();
  var link = function (label, href, primary) {
    return $('<a class="btn">').toggleClass('primary', !!primary).attr('href', href).text(label);
  };
  var ended = trip.endDate && trip.endDate < isoDate(new Date());
  if (trip.nextTripId) {
    $('#next-title').text('2탄 방이 열렸어요');
    $('#next-text').text('같은 사람, 같은 규칙으로 들어가 있어요. 되는 날만 다시 누르면 돼요.');
    actions.append(link('2탄 방으로', '/trips/' + trip.nextTripId, true), link('이번 영수증', '/trips/' + tripId + '/receipt'));
  } else if (ended) {
    $('#next-title').text('다녀왔어요? 이 멤버로 2탄');
    $('#next-text').text('사람·방 규칙은 그대로 새 방을 열어요. 초대 링크를 다시 돌릴 필요 없이 날짜만 맞추면 돼요.');
    actions.append($('<button type="button" class="btn primary">').text('2탄 방 열기').click(function () { startNextTrip(tripId, this); }),
      link('이번 영수증', '/trips/' + tripId + '/receipt'));
  } else if (trip.members.length === 1) {
    $('#next-title').text('먼저 친구들을 불러요');
    $('#next-text').text(shareLabel('누르면 카톡을 골라 바로 보낼 수 있어요.', '복사한 글을 단톡방에 붙여 넣으면 돼요.') + ' 친구는 링크 한 번 누르고 1분 설문만 하면 돼요.');
    actions.append($('<button type="button" class="btn primary">').text(shareLabel('카톡으로 초대 보내기', '초대 글 복사하기')).click(sendInvite),
      me.surveyDone ? null : link('내 설문 먼저 (1분)', '/trips/' + tripId + '/survey'));
  } else if (!me.surveyDone) {
    $('#next-title').text('내 설문부터 해요 (1분)');
    $('#next-text').text('차가 있는지, 맡기 싫은 일, 숙소에서 중요한 것만 물어요. 답에 따라 역할이 추천돼요.');
    actions.append(link('설문하기', '/trips/' + tripId + '/survey', true));
  } else if (notYet.length && me.host) {
    $('#next-title').text(notYet.length + '명이 아직 설문 전이에요');
    $('#next-text').text(notYet.map(function (m) { return m.name; }).join(', ') + ' 님에게 단톡방으로 한 번 더 알려 주세요.');
    actions.append($('<button type="button" class="btn primary">').text('한 번 더 알리기').click(sendReminder),
      link('지금 역할 보기', '/trips/' + tripId + '/roles'));
  } else if (notYet.length) {
    $('#next-title').text('설문 고마워요!');
    $('#next-text').text('남은 ' + notYet.length + '명이 끝나면 역할이 자동으로 정해져요. 숙소 링크는 지금 올려도 돼요.');
    actions.append(link('숙소 올리기', '/trips/' + tripId + '/stays', true), link('역할 보드', '/trips/' + tripId + '/roles'));
  } else {
    $('#next-title').text('모두 설문을 끝냈어요');
    $('#next-text').text('설문으로 역할이 정해졌어요. 마음에 안 들면 보드에서 눌러 바꿀 수 있어요.');
    actions.append(link('역할 보드', '/trips/' + tripId + '/roles', true), link('숙소 투표', '/trips/' + tripId + '/stays'),
      link('우리 여행 영수증', '/trips/' + tripId + '/receipt'));
  }
}

// 준비 체크 다섯 줄. 역할·숙소·장보기는 각 API 를 한 번씩 읽는다(방 화면이 바뀔 때마다 SSE 로 다시)
function renderProgress(notYet) {
  var base = '/api/trips/' + tripId;
  $.when(api('GET', base + '/roles'), api('GET', base + '/stays'), api('GET', base + '/shopping')).done(function (r, s, sh) {
    var roles = r[0], stays = s[0], shop = sh[0];
    var votes = stays.stays.reduce(function (n, x) { return n + x.voterIds.length; }, 0);
    var claimed = shop.items.filter(function (i) { return i.buyerId; }).length;
    var steps = [
      { done: !!trip.startDate, title: '날짜', text: trip.startDate ? formatPeriod(trip.startDate, trip.endDate) : '되는 날을 모으는 중', href: '' },
      { done: notYet.length === 0, title: '설문', text: (trip.members.length - notYet.length) + '/' + trip.members.length + '명', href: '/survey' },
      { done: roles.assignments.length > 0, title: '역할', text: roles.assignments.length ? roles.assignments.length + '명 정해짐' : '설문이 다 모이면 저절로', href: '/roles' },
      { done: votes > 0 && votes >= Math.ceil(trip.members.length / 2), title: '숙소',
        text: stays.stays.length ? stays.stays.length + '곳 · 표 ' + votes + '/' + trip.members.length : '링크를 올려 주세요', href: '/stays' },
      { done: claimed === shop.items.length, title: '장보기', text: claimed + '/' + shop.items.length + '줄 맡음', href: '/shopping' }
    ];
    var list = $('#progress').empty();
    steps.forEach(function (st) {
      var row = $('<a class="progress-row">').attr('href', '/trips/' + tripId + st.href).toggleClass('done', st.done)
        .append($('<span class="progress-mark" aria-hidden="true">').text(st.done ? '✓' : ''),
          $('<span class="strong">').text(st.title), $('<span class="muted small">').text(st.text));
      list.append($('<li>').append(row));
      if (st.done && lastDone && lastDone.indexOf(st.title) < 0) delight(row.find('.progress-mark'));
    });
    lastDone = steps.filter(function (st) { return st.done; }).map(function (st) { return st.title; });
    var done = steps.filter(function (st) { return st.done; }).length;
    $('#progress-count').text(done + '/5');
    // 다 끝나면 영수증으로
    if (done === 5) list.append($('<li>').append($('<a class="btn primary block">').attr('href', '/trips/' + tripId + '/receipt').text('우리 여행 영수증 뽑기')));
  });
}

// 날짜 맞추기. 방에 날짜가 정해지면 숨는다. 누를 때마다 0.6초 기다렸다 한 번에 저장(연달아 눌러도 요청 하나)
var myDates = null;
var datesTimer = null;
function renderDates(me) {
  $('#dates-card').prop('hidden', !!trip.startDate);
  if (trip.startDate) return;
  api('GET', '/api/trips/' + tripId + '/dates').done(function (board) {
    drawDates(me, board);
    // 첫 화면 "되는 날 누르기"로 왔으면 달력까지 내려 준다(숨어 있던 칸이라 주소의 #만으로는 안 내려간다). 한 번만
    if (location.hash === '#dates-card') {
      history.replaceState(null, '', location.pathname);
      document.getElementById('dates-card').scrollIntoView({ behavior: REDUCE_MOTION ? 'auto' : 'smooth', block: 'start' });
    }
  });
}

function drawDates(me, board) {
  if (myDates === null) {
    myDates = new Set(Object.keys(board.available).filter(function (k) { return board.available[k].indexOf(me.id) >= 0; }));
  }
  $('#dates-count').text(board.responded + '/' + board.memberCount + '명 답함');
  availabilityCalendar($('#dates-cal'), {
    mine: myDates, available: board.available, total: board.memberCount, editable: true,
    onChange: function (set) {
      clearTimeout(datesTimer);
      datesTimer = setTimeout(function () {
        api('PUT', '/api/trips/' + tripId + '/members/' + me.id + '/dates', { dates: Array.from(set) })
          .done(function (b) { drawBest(me, b); $('#dates-count').text(b.responded + '/' + b.memberCount + '명 답함'); });
      }, 600);
    }
  });
  drawBest(me, board);
}

// "1월 15일(금) ~ 17일(일) · 2박 3일 · 8명 중 7명 (하준 안 됨)". 방장은 옆 단추로 바로 정한다
function drawBest(me, board) {
  var list = $('#dates-best').empty();
  var best = bestWindows(board, trip.members, 3);
  if (!best.length) {
    list.append($('<li class="hint">').text('겹치는 날이 생기면 여기 좋은 날짜가 떠요.'));
    return;
  }
  best.forEach(function (w) {
    // 날짜는 굵은 한 줄, 박 수와 되는 사람은 아래 작은 줄(한 줄에 몰면 "2박 / 3일"로 꺾였다)
    var who = w.missing.length
      ? trip.members.length + '명 중 ' + w.ok.length + '명 (' + w.missing.map(function (m) { return m.name; }).join(', ') + ' 안 됨)'
      : trip.members.length + '명 모두 돼요';
    var stay = w.nights ? w.nights + '박 ' + (w.nights + 1) + '일' : '당일';
    var row = $('<li class="between best-date">').append($('<div>').append(
      $('<div class="strong">').text(formatPeriod(w.start, w.end).split(' · ')[0]),
      $('<div class="muted small">').text(stay + ' · ' + who)));
    if (me.host) {
      row.append($('<button type="button" class="btn primary">').text('이 날짜로').click(function () {
        api('PUT', '/api/trips/' + tripId + '/period', { startDate: w.start, endDate: w.end }).done(function (t) {
          showToast('날짜를 정했어요. 모두의 화면에 바로 보여요.');
          loadTrip(tripId).done(render);
        });
      }));
    }
    list.append(row);
  });
}

// 방 규칙 카드. 방장은 눌러서 고르고, 참여자는 정해진 것을 읽기만 한다
// 모일 시간·장소. 방장은 빠른 고르기(오전 7~10시) + 30분씩 −/+, 장소 칸. 바꾸면 0.6초 뒤 저장. 참여자는 한 줄.
// 브라우저 시간 칸은 어두운 화면에서 흰 상자로 떠서(우리 디자인과 안 맞음) 우리 단추로 바꿨다
// PUT /api/trips/1/meet  { "time": "08:00", "place": "합정역 2번 출구" }
var meetTimer = null;
var QUICK_TIMES = ['07:00', '08:00', '09:00', '10:00'];

// "08:30" → "오전 8:30", "13:00" → "오후 1시"
function koreanTime(hhmm) {
  if (!hhmm) return '시간 미정';
  var h = Number(hhmm.slice(0, 2)), m = hhmm.slice(3, 5);
  var label = (h < 12 ? '오전 ' : '오후 ') + (h % 12 || 12);
  return m === '00' ? label + '시' : label + ':' + m;
}

function renderMeet(me) {
  var box = $('#meet');
  var meet = trip.meet || {};
  var text = meet.time || meet.place ? [meet.time ? koreanTime(meet.time.slice(0, 5)) : null, meet.place].filter(Boolean).join(' · ') : null;
  if (!me.host) {
    box.empty().append($('<h2>').text('모이는 곳'), $('<p>').text(text || '방장이 아직 안 정했어요.'));
    return;
  }
  if (box.find('input').length) return;   // 방장이 적는 중에 SSE 로 다시 그려 입력이 끊기지 않게
  var value = meet.time ? meet.time.slice(0, 5) : null;
  var place = $('<input type="text" maxlength="40" placeholder="예: 합정역 2번 출구" aria-label="모일 곳">').val(meet.place || '');
  var save = function () {
    clearTimeout(meetTimer);
    meetTimer = setTimeout(function () {
      api('PUT', '/api/trips/' + tripId + '/meet', { time: value, place: place.val().trim() || null })
        .done(function (t) { trip = t; showToast('모일 곳을 저장했어요.'); renderDday(); });
    }, 600);
  };
  var shown = $('<b class="meet-time" aria-live="polite">');
  var quick = $('<div class="toggles meet-quick" role="group" aria-label="자주 모이는 시간">');
  var paint = function () {
    shown.text(koreanTime(value));
    quick.children().each(function () { $(this).attr('aria-pressed', $(this).data('t') === value); });
  };
  var set = function (t) { value = t; paint(); delight(shown[0]); save(); };
  QUICK_TIMES.forEach(function (t) {
    $('<button type="button" class="toggle">').text(koreanTime(t)).data('t', t).click(function () { set(t); }).appendTo(quick);
  });
  // 30분씩. 처음 누르면 오전 8시부터, 0시 아래·23:30 위로는 안 간다
  var shift = function (minutes) {
    var base = value ? Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5)) : 8 * 60 - minutes;
    var next = Math.min(23 * 60 + 30, Math.max(0, base + minutes));
    set(String(Math.floor(next / 60)).padStart(2, '0') + ':' + String(next % 60).padStart(2, '0'));
  };
  var stepper = $('<div class="stepper meet-stepper">').append(
    $('<button type="button" class="btn" aria-label="30분 이르게">').text('−').click(function () { shift(-30); }),
    shown,
    $('<button type="button" class="btn" aria-label="30분 늦게">').text('+').click(function () { shift(30); }));
  place.on('input', save);
  paint();
  box.empty().append($('<h2>').text('모이는 곳'), quick, stepper, place,
    $('<p class="muted small">').text('적어 두면 하루 전부터 모두의 방 위쪽에 떠요.'));
}

function renderDday() {
  var d = dday(trip.destination, trip.startDate, trip.endDate, trip.meet);
  $('#dday').text(d || '').prop('hidden', !d);
}

function renderRules(me) {
  var list = $('#rules').empty();
  $('#rules-note').text(me.host ? '처음에 정해 두면 나중에 안 싸워요. 모두의 화면과 결과 글에 보여요.' : '방장이 정해요. 결과 글에도 같이 나가요.');
  ROOM_RULES.forEach(function (r) {
    var value = trip.rules[r.key];
    // 방장은 이름 아래 두 단추, 참여자는 한 줄(이름 … 정한 것)
    var row = $('<li>').addClass(me.host ? 'pref' : 'between').append($('<span class="strong">').text(r.title));
    if (!me.host) {
      var picked = r.options.find(function (o) { return o.value === value; });
      row.append($('<span>').toggleClass('muted', !picked).text(picked ? picked.label : '아직 안 정함'));
    } else {
      var group = $('<div class="segmented" role="group">').attr('aria-label', r.title);
      r.options.forEach(function (o) {
        $('<button type="button">').text(o.label).attr('aria-pressed', value === o.value).click(function () {
          // 같은 걸 다시 누르면 "안 정함"으로 되돌린다
          saveRule(r.key, value === o.value ? null : o.value);
        }).appendTo(group);
      });
      row.append(group);
    }
    list.append(row);
  });
}

// PUT: /api/trips/1/rules  { "driverPaysFuel": false, "refundNoShow": false, "drinksSeparate": true }
var lastDone = null; // 준비 체크에서 지난번에 끝나 있던 줄. 새로 끝난 줄만 튀게
function saveRule(key, value) {
  var next = $.extend({}, trip.rules);
  next[key] = value;
  trip.rules = next;
  renderRules(trip.members.find(function (m) { return m.id === trip.meId; })); // 누르자마자 칠하고, 저장은 뒤에서
  delight($('#rules button[aria-pressed="true"]').filter(function () { return $(this).closest('li').text().indexOf(ROOM_RULES.find(function (r) { return r.key === key; }).title) >= 0; }));
  api('PUT', '/api/trips/' + tripId + '/rules', next)
    .done(render)
    .fail(function (xhr) { if (xhr.status !== 401) { showNotice('규칙을 저장하지 못했어요. 다시 눌러 주세요.'); loadTrip(tripId).done(render); } });
}

// 링크만 던지면 무슨 링크인지 몰라 안 누른다. 할 일(1분 설문)과 까닭을 한 줄 붙인다
function sendInvite() {
  var rules = rulesText(trip.rules);
  var ask = trip.startDate ? '1분 설문만 해 줘' : '되는 날 누르고 1분 설문만 해 줘';
  shareText('[' + trip.title + '] 여행방 만들었어! ' + ask + '. 운전·총무랑 숙소 같이 정하자\n'
    + (rules ? '규칙: ' + rules + '\n' : '') + $('#invite-link').val(),
    '초대 글을 복사했어요. 단톡방에 붙여 넣어 주세요.');
}

// 방·침대 뽑기. POST /api/trips/1/room-draw { "rooms": 2 } -> 방 응답의 roomDraw { rooms: [["재현","민지"],…], count, by }
var drawRooms = null;
var drawing = false;
function renderDraw() {
  var n = trip.members.length;
  $('#draw-card').prop('hidden', n < 2);
  if (n < 2) return;
  if (drawRooms === null) drawRooms = trip.roomDraw ? trip.roomDraw.rooms.length : Math.max(2, Math.ceil(n / 4));
  drawRooms = Math.min(Math.max(2, drawRooms), Math.min(6, n));
  $('#draw-count').text(drawRooms + '개 방');
  if (drawing) return;
  var d = trip.roomDraw;
  $('#draw-meta').text(d ? withSubject(d.by) + ' 뽑음 · ' + d.count + '번째' : '');
  $('#btn-draw').text(d ? '다시 뽑기' : '뽑기!');
  $('#btn-draw-share').prop('hidden', !d);
  paintRooms(d ? d.rooms : []);
}

function paintRooms(rooms) {
  var list = $('#draw-result').empty();
  rooms.forEach(function (names, i) {
    $('<li class="draw-room">').append($('<b>').text((i + 1) + '번 방'),
      $('<span>').append(names.map(function (n) { return $('<span class="chip">').text(n); }))).appendTo(list);
  });
}

// 이름이 0.9초 동안 방 사이를 돌다가 서버 결과에서 멈춘다(움직임 줄이기면 바로)
function spin(final) {
  var names = trip.members.map(function (m) { return m.name; });
  var started = Date.now();
  drawing = true;
  var tick = function () {
    if (REDUCE_MOTION || Date.now() - started > 900) {
      drawing = false;
      renderDraw();
      delight($('#draw-result')[0]);
      return;
    }
    var shuffled = names.slice().sort(function () { return Math.random() - 0.5; });
    var rooms = final.rooms.map(function () { return []; });
    shuffled.forEach(function (name, i) { rooms[i % rooms.length].push(name); });
    paintRooms(rooms);
    setTimeout(tick, 80);
  };
  tick();
}

function shareDraw() {
  var d = trip.roomDraw;
  var lines = ['[' + trip.title + '] 방 뽑기 (' + d.count + '번째, ' + withSubject(d.by) + ' 뽑음)'];
  d.rooms.forEach(function (names, i) { lines.push((i + 1) + '번 방: ' + names.join(', ')); });
  shareText(lines.join('\n') + '\n' + location.origin + '/trips/' + tripId, '뽑기 결과를 복사했어요. 단톡방에 붙여 넣어 주세요.');
}

// PUT: /api/trips/1/host  { "memberId": 3 }   방장 넘기기. 넘기면 나는 참여자가 된다
function handOver(m) {
  if (!window.confirm(m.name + ' 님에게 방장을 넘길까요? 날짜 정하기·규칙·빼기는 이제 ' + m.name + ' 님이 해요.')) return;
  api('PUT', '/api/trips/' + tripId + '/host', { memberId: m.id }).done(function (t) {
    showToast(m.name + ' 님이 이제 방장이에요.');
    render(t);
  });
}

// PUT: /api/trips/1  { "title", "destination" }   방장만. 사진 위 단추로 연다
function renderRename(me) {
  $('#btn-rename').prop('hidden', !me.host);
  if (!me.host) $('#rename-form').prop('hidden', true);
}

// DELETE: /api/trips/1/members/7   나가기(본인) 또는 빼기(방장). 설문·숙소 표·장보기 맡은 것도 같이 빠진다
function leaveTrip(m, self) {
  var ask = self
    ? '이 방에서 나갈까요? 내 설문·숙소 표·장보기 맡은 것이 같이 빠지고, 1인 금액은 남은 사람끼리 다시 나눠요.'
    : m.name + ' 님을 방에서 뺄까요? 설문·숙소 표·장보기 맡은 것이 같이 빠지고, 1인 금액은 남은 사람끼리 다시 나눠요.';
  if (!window.confirm(ask)) return;
  api('DELETE', '/api/trips/' + tripId + '/members/' + m.id).done(function () {
    if (self) {
      try { sessionStorage.setItem('tripmate.toast', '방에서 나왔어요.'); } catch (e) { /* 알림만 못 띄운다 */ }
      location.href = '/';
    } else {
      showToast(m.name + ' 님을 뺐어요. 1인 금액을 다시 나눴어요.');
      loadTrip(tripId).done(render);
    }
  }).fail(function (xhr) { if (xhr.status === 400) showNotice('혼자 남은 방은 나갈 수 없어요.'); });
}

function sendReminder() {
  var names = trip.members.filter(function (m) { return !m.surveyDone; }).map(function (m) { return m.name; });
  shareText('[' + trip.title + '] ' + names.join(', ') + ' 님 설문 1분만 부탁해요. 다 모이면 역할이 자동으로 정해져요\n'
    + $('#invite-link').val(), '알림 글을 복사했어요. 단톡방에 붙여 넣어 주세요.');
}

$(function () {
  $('#btn-invite').click(sendInvite);
  $('#btn-rename').click(function () {
    $('#rename-title').val(trip.title);
    $('#rename-destination').val(trip.destination || '');
    $('#rename-form').prop('hidden', false)[0].scrollIntoView({ behavior: REDUCE_MOTION ? 'auto' : 'smooth', block: 'center' });
    $('#rename-title').trigger('focus');
  });
  $('#rename-cancel').click(function () { $('#rename-form').prop('hidden', true); });
  $('#draw-less').click(function () { drawRooms--; renderDraw(); });
  $('#draw-more').click(function () { drawRooms++; renderDraw(); });
  $('#btn-draw-share').text(shareLabel('카톡으로 결과 보내기', '결과 복사하기')).click(shareDraw);
  $('#btn-draw').click(function () {
    var button = $(this).prop('disabled', true);
    api('POST', '/api/trips/' + tripId + '/room-draw', { rooms: drawRooms }).done(function (t) {
      trip = t;
      spin(t.roomDraw);
    }).always(function () { button.prop('disabled', false); });
  });
  $('#rename-form').on('submit', function (e) {
    e.preventDefault();
    api('PUT', '/api/trips/' + tripId, { title: $('#rename-title').val().trim(), destination: $('#rename-destination').val().trim() || null })
      .done(function () {
        $('#rename-form').prop('hidden', true);
        showToast('방 이름·지역을 바꿨어요.');
        loadTrip(tripId).done(render);   // 머리 줄·사진(지역)까지 다시
      });
  });
  // 공유도 복사도 막힌 곳에서 칸을 누르면 링크 전체가 골라져 바로 복사할 수 있게
  $('#invite-link').on('focus', function () { this.select(); });
  loadTrip(tripId).done(function (t) {
    render(t);
    listen(tripId, function () { loadTrip(tripId).done(render); });
  });
});
