// 첫 화면: 로그인했으면 "내 여행방", 첫 화면 휴대폰 장면 재생, 스크롤 등장
var wait = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };

// 로그인하고 첫 화면에 오면 방마다 "내가 아직 안 한 일"을 먼저 보여 주고, 누르면 그 일로 바로 간다.
// 알림을 보내는 대신 들어온 김에 하게 한다
var MY_TODO = {
  invite: { label: '친구 부르기', path: '' },
  survey: { label: '내 설문 (1분)', path: '/survey' },
  dates: { label: '되는 날 누르기', path: '#dates-card' }
};

// 사진 위 알약에 넣을 짧은 날짜 말(방 머리의 dday() 는 길어서 카드에 안 들어간다)
function shortDday(startDate, endDate) {
  if (!startDate) return '날짜 미정';
  var day = function (s) { return new Date(s + 'T00:00:00'); };
  var now = new Date(); now = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  var toStart = Math.round((day(startDate) - now) / 86400000);
  if (toStart > 0) return 'D-' + toStart;
  if (toStart === 0) return 'D-DAY';
  return now <= day(endDate || startDate) ? '여행 중' : '다녀왔어요';
}

// 방 카드: 소개 페이지의 방 머리처럼 지역 사진 + D-day 알약, 아래 이름·지역·설문, 할 일 한 줄, 오른쪽 단추 하나.
// 할 일 있는 방을 앞에 둔다(들어온 김에 바로 하게)
// GET: /api/me/trips  -> [{ "id": 1, "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15",
//                           "memberCount": 8, "surveyDone": 3, "host": true }]
// api() 는 401 이면 로그인 화면으로 보낸다. 첫 화면은 로그인 안 한 사람이 보는 게 정상이라 $.ajax 를 직접 쓴다
function loadMyTrips() {
  $.ajax({ url: '/api/me/trips', type: 'GET' }).done(function (trips) {
    if (!trips.length) return;
    var list = $('#trip-list').empty();
    var todoCount = 0;
    trips.slice().sort(function (x, y) { return (y.todo || []).length - (x.todo || []).length; }).forEach(function (t) {
      var todo = t.todo || [];
      var first = todo[0] && MY_TODO[todo[0]];
      todoCount += todo.length;
      var photo = tripPhoto(t.destination, t.startDate);
      var thumb = $('<span class="trip-thumb">').append(
        photo ? $('<img loading="lazy" width="400" height="200">').attr({ src: photo.src, alt: photo.alt }) : null,
        $('<span class="trip-dday">').text(shortDday(t.startDate, t.endDate)));
      var meta = [t.destination || '지역 미정', t.startDate ? formatPeriod(t.startDate, t.endDate) : '날짜 미정'].join(' · ');
      var body = $('<div class="trip-body">').append(
        $('<div class="strong trip-title">').text(t.title).append(t.host ? $('<span class="tag me">').text('방장') : null),
        $('<div class="muted small">').text(meta),
        $('<div class="trip-survey small">').append(
          $('<span class="bar">').append($('<span class="fill">').css('width', Math.round(100 * t.surveyDone / t.memberCount) + '%')),
          $('<span class="muted">').text('설문 ' + t.surveyDone + '/' + t.memberCount + '명')),
        first ? $('<div class="todo-line small">').text('할 일 · ' + todo.map(function (k) { return MY_TODO[k].label; }).join(', ')) : null);
      $('<li>').append($('<a class="trip-card">').toggleClass('has-todo', !!first)
        .attr('href', '/trips/' + t.id + (first ? first.path : ''))
        .append(thumb, body, $('<span class="btn small">').toggleClass('primary', !!first).text(first ? '하러 가기' : '들어가기'))).appendTo(list);
    });
    // 방이 많으면 첫 화면이 목록이 된다. 앞의 셋(할 일 있는 방이 먼저)만 두고 나머지는 한 번 눌러 펼친다
    var more = list.children().slice(3).prop('hidden', true);
    if (more.length) {
      $('<li class="trip-more">').append($('<button type="button" class="btn small block">').text('방 ' + more.length + '개 더 보기')
        .on('click', function () { more.prop('hidden', false); $(this).parent().remove(); })).appendTo(list);
    }
    $('#my-trips-todo').text(todoCount ? '할 일 ' + todoCount : '').prop('hidden', !todoCount);
    $('#my-trips-clear').prop('hidden', todoCount > 0);
    $('#my-trips').prop('hidden', false);
  });
}

// 휴대폰 장면: 겨울 펜션 계획으로 시끄러운 단톡방. 메시지가 "…"(입력 중) 뒤에 하나씩, 마지막에 방장이 TripMate 링크를 던지고 끝 → 처음부터.
// 앱 화면은 바로 아래 영상 띠와 실제 화면 열 장이 보여 준다(여기서 또 보이면 겹쳤다).
// 화면 밖이면 멈춘다(보지도 않는데 계속 돌지 않게). 움직임 줄이기면 재생하지 않고 결과 화면만 둔다
function playPhone(phone) {
  var visible = true;
  new IntersectionObserver(function (entries) { visible = entries[0].isIntersecting; }).observe(phone);
  var untilVisible = async function () { while (!visible) await wait(300); };

  var chatSteps = phone.querySelectorAll('.chat-scene [data-step]');
  var unread = phone.querySelector('.unread');
  var typing = $('<li class="typing" aria-hidden="true"><i></i><i></i><i></i></li>');

  phone.classList.add('no-anim', 'playing');
  void phone.offsetHeight; // 숨긴 상태를 먼저 그리게 한 뒤 전환을 다시 켠다
  phone.classList.remove('no-anim');
  var firstRound = true;
  (async function loop() {
    for (;;) {
      $(chatSteps).removeClass('shown');
      if (unread) unread.textContent = '7';
      // 첫 바퀴는 싸움거리 세 줄(운전·숙소·날짜)을 바로 띄운다. 들어오자마자 빈 휴대폰이 보이면 "고장났나?"로 읽혔고,
      // 말풍선 하나만으로는 "우리 단톡방 얘기네"가 안 됐다
      if (!firstRound) await wait(600);
      for (var i = 0; i < chatSteps.length; i++) {
        await untilVisible();
        var mine = chatSteps[i].classList.contains('mine');
        if (firstRound && i < 3) { chatSteps[i].classList.add('shown'); await wait(i < 2 ? 250 : 900); continue; }
        // "…"(입력 중)은 남이 쓸 때만. 내가 보내는 링크는 바로 붙는다
        if (!mine) {
          typing.insertBefore(chatSteps[i]);
          await wait(900);
          typing.detach();
        }
        chatSteps[i].classList.add('shown');
        // 카톡처럼 친구들이 읽을 때마다 노란 숫자가 줄어든다(7 → 1). 다 읽기 전에 민지가 답한다
        if (mine && unread) readOneByOne(unread, [6, 5, 3, 2, 1]);
        await wait(mine ? 1800 : 900); // 초대 카드는 읽을 시간을 조금 더
      }
      await wait(4000);
      firstRound = false;
    }
  })();
}

// 히어로에 마우스를 올리면 휴대폰이 마우스 쪽으로 아주 조금(최대 4도) 기운다. 손에 반응한다는 느낌만 주고 읽기를 방해하지 않게 작게.
// 마우스가 있는 기기에서만. 프레임마다 한 번만 바꿔(requestAnimationFrame) 마우스를 빨리 움직여도 버벅이지 않게
function tiltPhone(phone) {
  if (!window.matchMedia('(hover: hover)').matches) return;
  var hero = phone.closest('.hero');
  var frame = null;
  hero.addEventListener('pointermove', function (e) {
    if (frame) return;
    frame = requestAnimationFrame(function () {
      var box = hero.getBoundingClientRect();
      var x = (e.clientX - box.left) / box.width - 0.5;
      var y = (e.clientY - box.top) / box.height - 0.5;
      phone.style.transform = 'perspective(900px) rotateY(' + (x * 8).toFixed(2) + 'deg) rotateX(' + (-y * 6).toFixed(2) + 'deg)';
      frame = null;
    });
  });
  hero.addEventListener('pointerleave', function () { phone.style.transform = ''; });
}

// 안 읽은 수를 한 칸씩 줄인다. 바뀔 때 살짝 깜빡여 눈이 간다
async function readOneByOne(el, steps) {
  for (var i = 0; i < steps.length; i++) {
    await wait(300 + Math.random() * 250);
    el.textContent = String(steps[i]);
    el.classList.remove('tick');
    void el.offsetWidth;
    el.classList.add('tick');
  }
}

// 공감 숫자(76·74·82%)는 화면에 들어올 때 0부터 0.9초 동안 올라간다. 한 번만
function countStats() {
  var nums = document.querySelectorAll('[data-count-to]');
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (!e.isIntersecting) return;
      observer.unobserve(e.target);
      var el = e.target, to = Number(el.dataset.countTo), start = performance.now();
      var step = function (now) {
        var t = Math.min(1, (now - start) / 900);
        el.textContent = Math.round(to * (1 - Math.pow(1 - t, 3)));
        if (t < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
      // 뒤쪽 탭처럼 그리기가 멈춘 곳에서도 0 으로 남지 않게 끝 값을 박아 둔다
      setTimeout(function () { el.textContent = to; }, 1000);
    });
  }, { threshold: 0.6 });
  nums.forEach(function (el) { el.textContent = '0'; observer.observe(el); });
}

// 첫 화면 영수증: 화면에 반쯤 들어오면 그때 프린터에서 뽑힌다(미리 뽑혀 있으면 연출을 못 본다). 한 번만
function printOnScroll() {
  var r = document.getElementById('home-receipt');
  if (!r) return;
  r.classList.add('await-print'); // 움직임이 켜진 때만 프린터 안에 숨겨 둔다(꺼져 있으면 이 함수가 안 불려 그대로 보인다)
  var observer = new IntersectionObserver(function (entries) {
    if (!entries[0].isIntersecting) return;
    r.classList.remove('await-print');
    r.classList.add('printing');
    observer.disconnect();
  }, { threshold: 0.4 });
  observer.observe(r.closest('.receipt-box'));
}

// 절이 화면에 들어올 때 한 번만 살짝 올라온다. JS 가 붙인 .will-reveal 이 없으면 처음부터 그대로 보인다
function revealOnScroll() {
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (!e.isIntersecting) return;
      e.target.classList.add('revealed');
      observer.unobserve(e.target);
    });
  }, { rootMargin: '0px 0px -10% 0px' });
  document.querySelectorAll('[data-reveal]').forEach(function (el) {
    el.classList.add('will-reveal');
    observer.observe(el);
  });
}

// 이렇게 굴러가요: 화면 가운데를 지나는 단락의 실제 화면을 오른쪽 휴대폰에 띄우고, 누르는 곳에 동그라미를 옮긴다(PC 만, 휴대폰은 단락마다 그림이 있다)
function tourScroll() {
  var stage = document.querySelector('.tour-stage .device-view');
  if (!stage || !('IntersectionObserver' in window)) return;
  var ring = stage.querySelector('.tap-ring');
  var steps = document.querySelectorAll('.tour-step');
  var show = function (step) {
    steps.forEach(function (s) { s.classList.toggle('on', s === step); });
    stage.querySelectorAll('.tour-shot').forEach(function (img) { img.classList.toggle('on', img.dataset.shot === step.dataset.shot); });
    ring.style.setProperty('--x', step.style.getPropertyValue('--x'));
    ring.style.setProperty('--y', step.style.getPropertyValue('--y'));
    // 새 장면마다 동그라미를 처음부터 다시 퍼지게
    ring.style.animation = 'none'; void ring.offsetWidth; ring.style.animation = '';
  };
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) { if (e.isIntersecting) show(e.target); });
  }, { rootMargin: '-45% 0px -45% 0px' });
  steps.forEach(function (s) { observer.observe(s); });
  show(steps[0]);
}

// 휴대폰 둘러보기: 옆으로 넘기는 카드 열 장. 몇 번째인지 세고, ‹ › 단추로도 한 장씩(넘기기를 모르는 사람, 마우스)
function tourSwipe() {
  var list = document.querySelector('.tour-steps');
  var nav = document.querySelector('.tour-nav');
  if (!list || !nav) return;
  var cards = list.children;
  var prev = nav.querySelector('[data-tour-go="-1"]');
  var next = nav.querySelector('[data-tour-go="1"]');
  var count = nav.querySelector('.tour-count');
  var step = function () { return cards[1] ? cards[1].offsetLeft - cards[0].offsetLeft : list.clientWidth; };
  // 넘길 수 있는지는 실제로 남은 거리로 본다. 처음에 PC 폭으로 열렸다가 창을 줄이면 목록이 안 넘어가는 상태로 셌다가
  // "10 / 10"에 › 가 꺼진 채 남았다(창 크기가 바뀔 때도 다시 센다)
  var update = function () {
    var max = list.scrollWidth - list.clientWidth;
    var atEnd = max <= 4 || list.scrollLeft >= max - 4;
    var i = max <= 4 ? 0 : atEnd ? cards.length - 1 : Math.min(cards.length - 1, Math.round(list.scrollLeft / step()));
    count.innerHTML = '<b>' + (i + 1) + '</b> / ' + cards.length + (i === 0 ? ' · 옆으로 넘겨 보세요' : '');
    prev.disabled = list.scrollLeft <= 4;
    next.disabled = atEnd;
  };
  var ticking = false;
  list.addEventListener('scroll', function () {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(function () { ticking = false; update(); });
  }, { passive: true });
  $(nav).on('click', '[data-tour-go]', function () {
    list.scrollBy({ left: Number(this.dataset.tourGo) * step(), behavior: REDUCE_MOTION ? 'auto' : 'smooth' });
  });
  window.addEventListener('resize', update);
  update();
}

// 실제 앱 영상: 화면에 들어오면 그때 받아 소리 없이 틀고, 나가면 멈춘다(데이터·배터리).
// 움직임 줄이기면 저절로 틀지 않고 첫 장면 그림 + 재생 단추(사용자가 누를 때만)
function lazyVideos() {
  var videos = document.querySelectorAll('video[data-src]');
  if (!videos.length) return;
  var load = function (v) { if (!v.src) { v.src = v.dataset.src; v.load(); } };
  if (REDUCE_MOTION || !('IntersectionObserver' in window)) {
    // preload none 이라 주소를 넣어 둬도 재생을 누르기 전에는 받지 않는다
    videos.forEach(function (v) { v.controls = true; v.preload = 'none'; v.src = v.dataset.src; });
    return;
  }
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      var v = e.target;
      if (e.isIntersecting) { load(v); var p = v.play(); if (p && p.catch) p.catch(function () { v.controls = true; }); }
      else if (!v.paused) v.pause();
    });
  }, { rootMargin: '200px 0px' });
  videos.forEach(function (v) { observer.observe(v); });
}

// 첫 문구는 새로고침할 때마다 다른 말(바로 앞과 같은 말은 피한다). 서버가 그린 첫 문구가 기본이라 스크립트가 없어도 읽힌다
var HERO_LINES = [
  '"운전 누가 해?"<br>이제 단톡방에서 안 싸워요.',
  '"언제 돼?" 무한 루프,<br>링크 하나로 끝.',
  'N빵 말고 우리 규칙빵.<br>운전자 기름값은 빼고.',
  '"나만 또 계획 짜?"<br>이번엔 다 같이, 1분이면.',
  '총무 혼자 다 하던 시대 끝.<br>역할은 근거까지 1분.',
  '여행 가기 전에 싸우지 말고,<br>가서 놀아요.',
  '단톡방 공지 말고,<br>링크 하나 던지면 끝.'
];
function rotateHero() {
  var h = document.getElementById('hero-title');
  if (!h) return;
  var last = -1;
  try { last = Number(sessionStorage.getItem('tripmate.hero') || -1); } catch (e) { /* 저장 못 해도 고르기만 */ }
  var i = Math.floor(Math.random() * HERO_LINES.length);
  if (i === last) i = (i + 1) % HERO_LINES.length;
  try { sessionStorage.setItem('tripmate.hero', String(i)); } catch (e) { /* 바로 앞 문구 기억만 못 한다 */ }
  h.innerHTML = HERO_LINES[i];   // 고정 문자열만 넣는다(사용자 값 아님)
}

// 아래에 붙은 시작 단추는 첫 화면의 같은 단추가 화면 밖으로 나간 뒤에만 올라온다
function stickyAfterHero() {
  var sticky = document.querySelector('.sticky-cta');
  var heroCta = document.querySelector('.hero .btn.primary');
  if (!sticky || !heroCta || !('IntersectionObserver' in window)) return;
  // 첫 화면 단추나 마지막 권유 단추가 보이는 동안은 숨는다. 같은 단추가 한 화면에 두 개 보이지 않게
  var showing = new Set();
  sticky.classList.add('waiting');
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) { if (e.isIntersecting) showing.add(e.target); else showing.delete(e.target); });
    sticky.classList.toggle('waiting', showing.size > 0);
  });
  observer.observe(heroCta);
  var endCta = document.querySelector('.cta .btn.primary');
  if (endCta) observer.observe(endCta);
  // 로그인해서 내 여행방이 보이는 동안도 숨는다("+ 새 방"이 바로 위에 있고, 카드 단추를 가렸다)
  observer.observe(document.getElementById('my-trips'));
}

// "나는 무슨 역할형?" 미리 보기. 점수는 RoleScorer 규칙 일부(차 있음 +4, 계획형 +3, 숙소 휴식 +2, 밖 활동 +2)를 그대로 쓴다.
// 차가 없으면 운전은 고르지 않는다(실제 추천과 같다)
// 결과는 글 한 줄보다 "나 같다" 싶은 사진·한마디·짝꿍이 있어야 캡처해서 스토리에 올린다(MBTI 결과 카드처럼).
// 사진은 Unsplash 무료(운전 DaWKnhdOrIo, 총무 djb1whucfBY, 고기 UE6KHjZDAUY, 기록 hz_m6C_YG_k)
var QUIZ_ROLES = [
  { key: 'drive', name: '운전대 잡는 든든형', why: '차 있음 +4', score: function (a) { return a.car === 'yes' ? 4 : -1000; },
    line: '"휴게소 호두과자는 내가 쏜다"', desc: '조수석에 DJ 하나만 있으면 몇 시간이고 달려요. 우리 방 규칙이면 기름값은 빼 줘요.', mate: 'photo' },
  { key: 'money', name: '엑셀부터 켜는 총무형', why: '계획형 +3', score: function (a) { return a.plan === 'planner' ? 3 : 0; },
    line: '"1원까지 맞아야 잠이 와"', desc: '회비 걷고 영수증 뽑는 게 제일 신나요. 단톡방 정산은 이 사람 없으면 안 굴러가요.', mate: 'drive' },
  { key: 'bbq', name: '고기 굽는 숙소 지킴이형', why: '숙소 휴식 선호 +2', score: function (a) { return a.act === 'in' ? 2 : 0; },
    line: '"집게는 내 거야"', desc: '숙소 도착하면 숯부터 피워요. 8명 2박이면 고기 몇 kg인지 감으로 알아요.', mate: 'money' },
  { key: 'photo', name: '사진 남기는 기록형', why: '밖 활동 선호 +2', score: function (a) { return a.act === 'out' ? 2 : 0; },
    line: '"한 장만 더! 진짜 마지막"', desc: '여행 끝나고 단톡방 앨범을 채우는 사람. 다들 프사는 이 사람 덕이에요.', mate: 'bbq' }
];
var quizBest = null;

function quizAnswers() {
  var pick = function (name) { var c = document.querySelector('input[name="' + name + '"]:checked'); return c ? c.value : null; };
  return { car: pick('car'), plan: pick('plan'), act: pick('act') };
}

function showQuizResult() {
  var a = quizAnswers();
  if (!a.car || !a.plan || !a.act) return;
  var best = QUIZ_ROLES.slice().sort(function (x, y) { return y.score(a) - x.score(a); })[0];
  var mate = QUIZ_ROLES.find(function (r) { return r.key === best.mate; });
  var changed = quizBest !== best;
  quizBest = best;
  $('#quiz-photo').attr('src', '/img/role/' + best.key + '.jpg');
  $('#quiz-role').text(best.name);
  $('#quiz-line').text(best.line);
  $('#quiz-desc').text(best.desc);
  $('#quiz-mate').text(mate.name);
  $('#quiz-why').text('근거: ' + best.why + ' (실제 방에서 역할을 고르는 점수와 같아요)');
  var result = $('#quiz-result').prop('hidden', false);
  if (changed) delight(result.find('.quiz-card')[0]);
}

// 결과 카드 그림(1080x1920, 인스타 스토리 크기). 사진 위에 이름, 아래 한마디·짝꿍, 바닥에 주소.
// 그림이 퍼질 때 바닥 주소가 광고가 된다(docs/15). 같은 출처 사진이라 canvas 를 그림으로 뽑을 수 있다
function drawQuizCard(role) {
  var W = 1080, H = 1920, font = '"Pretendard Variable", Pretendard, sans-serif';
  var c = document.createElement('canvas'); c.width = W; c.height = H;
  var g = c.getContext('2d');
  var mate = QUIZ_ROLES.find(function (r) { return r.key === role.mate; });
  return new Promise(function (resolve, reject) {
    var img = new Image();
    img.onload = function () {
      g.fillStyle = '#f5f6f8'; g.fillRect(0, 0, W, H);
      // 사진은 위 60% 를 꽉 채우게 잘라 넣는다
      var ph = 1150, scale = Math.max(W / img.width, ph / img.height);
      var sw = W / scale, sh = ph / scale;
      g.drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, 0, 0, W, ph);
      var grad = g.createLinearGradient(0, ph - 520, 0, ph);
      grad.addColorStop(0, 'rgba(0,0,0,0)'); grad.addColorStop(1, 'rgba(0,0,0,0.72)');
      g.fillStyle = grad; g.fillRect(0, ph - 520, W, 520);
      g.fillStyle = '#ffffff'; g.textAlign = 'left';
      g.font = '600 44px ' + font; g.fillText('나는 여행 가면', 72, ph - 200);
      g.font = '800 84px ' + font; g.fillText(role.name, 72, ph - 90, W - 144);
      g.fillStyle = '#1b1f24';
      g.font = '800 60px ' + font; g.fillText(role.line, 72, ph + 140, W - 144);
      g.fillStyle = '#5f6873'; g.font = '400 40px ' + font;
      wrapText(g, role.desc, 72, ph + 230, W - 144, 60);
      g.fillStyle = '#0e7c72'; g.font = '700 40px ' + font;
      g.fillText('찰떡 짝꿍  ' + mate.name, 72, ph + 470, W - 144);
      g.fillStyle = '#1b1f24'; g.font = '800 44px ' + font; g.fillText('TripMate', 72, H - 120);
      g.fillStyle = '#5f6873'; g.font = '400 34px ' + font; g.fillText('너는 무슨 역할형? → ' + location.host + '/#quiz', 72, H - 66, W - 144);
      c.toBlob(resolve, 'image/jpeg', 0.9); // PNG 는 2MB 라 공유가 느렸다. 사진이 든 카드라 JPG 로
    };
    img.onerror = reject;
    img.src = '/img/role/' + role.key + '.jpg';
  });
}

function wrapText(g, text, x, y, max, lh) {
  var line = '';
  text.split(' ').forEach(function (word) {
    var next = line ? line + ' ' + word : word;
    if (g.measureText(next).width > max && line) { g.fillText(line, x, y); y += lh; line = word; }
    else line = next;
  });
  if (line) g.fillText(line, x, y);
}

// 휴대폰은 공유 창(카톡·인스타 스토리)으로 그림째, PC 는 그림 저장
function shareQuizCard() {
  if (!quizBest) return;
  var button = $('#quiz-card').prop('disabled', true);
  drawQuizCard(quizBest).then(function (blob) {
    return shareImage(blob, 'tripmate-' + quizBest.key + '.jpg',
      '나 여행 가면 "' + quizBest.name + '"래 ㅋㅋ 너는? ' + location.origin + '/#quiz', '결과 카드를 저장했어요. 스토리나 단톡방에 올려 보세요.');
  }).catch(function () { showToast('카드를 만들지 못했어요. 다시 눌러 주세요.'); })
    .finally(function () { button.prop('disabled', false); });
}

$(function () {
  $('.quiz input').on('change', showQuizResult);
  $('#quiz-card').text(shareLabel('결과 카드 공유하기', '결과 카드 저장하기')).on('click', shareQuizCard);
  $('#quiz-share').text(shareLabel('친구한테도 해 보라고 보내기', '친구 부를 글 복사하기')).on('click', function () {
    shareText('나 여행 가면 "' + $('#quiz-role').text() + '"래 ㅋㅋ 너는? ' + location.origin + '/#quiz', '복사했어요. 단톡방에 던져 보세요.');
  });
  loadMyTrips();
  stickyAfterHero();
  rotateHero();
  lazyVideos();
  tourScroll(); // 움직임 줄이기여도 화면은 바뀌어야 한다(바꾸는 것은 내용, 움직임은 CSS 가 끈다)
  tourSwipe();
  if (REDUCE_MOTION || !('IntersectionObserver' in window)) return;
  var phone = document.querySelector('.phone');
  if (phone) {
    playPhone(phone);
    tiltPhone(phone);
  }
  revealOnScroll();
  countStats();
  printOnScroll();
});
