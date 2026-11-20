// 첫 화면: 로그인했으면 "내 여행방", 첫 화면 휴대폰 장면 재생, 스크롤 등장
var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
var wait = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };

// GET: /api/me/trips  -> [{ "id": 1, "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15",
//                           "memberCount": 8, "surveyDone": 3, "host": true }]
// api() 는 401 이면 로그인 화면으로 보낸다. 첫 화면은 로그인 안 한 사람이 보는 게 정상이라 $.ajax 를 직접 쓴다
function loadMyTrips() {
  $.ajax({ url: '/api/me/trips', type: 'GET' }).done(function (trips) {
    if (!trips.length) return;
    var list = $('#trip-list').empty();
    trips.forEach(function (t) {
      var meta = [t.destination || '지역 미정', t.startDate || '날짜 미정', '설문 ' + t.surveyDone + '/' + t.memberCount].join(' · ');
      var info = $('<div>').append(
        $('<div class="strong">').text(t.title).append(t.host ? $('<span class="tag me">').text('방장') : null),
        $('<div class="muted small">').text(meta));
      $('<li>').append($('<a class="card trip-item">').attr('href', '/trips/' + t.id)
        .append(info, $('<span class="link">').text('들어가기'))).appendTo(list);
    });
    $('#my-trips').prop('hidden', false);
  });
}

// 휴대폰 장면: 단톡방 메시지가 "…"(입력 중) 뒤에 하나씩 → 역할 보드로 넘어가 줄이 하나씩 → 교통비 숫자가 올라감 → 처음부터.
// 화면 밖이면 멈춘다(보지도 않는데 계속 돌지 않게). 움직임 줄이기면 재생하지 않고 결과 화면만 둔다
function playPhone(phone) {
  var visible = true;
  new IntersectionObserver(function (entries) { visible = entries[0].isIntersecting; }).observe(phone);
  var untilVisible = async function () { while (!visible) await wait(300); };

  var chatSteps = phone.querySelectorAll('.chat-scene [data-step]');
  var boardSteps = phone.querySelectorAll('.board-scene [data-step]');
  var counter = phone.querySelector('[data-count]');
  var unread = phone.querySelector('.unread');
  var typing = $('<li class="typing" aria-hidden="true"><i></i><i></i><i></i></li>');

  phone.classList.add('no-anim', 'playing');
  void phone.offsetHeight; // 숨긴 상태를 먼저 그리게 한 뒤 전환을 다시 켠다
  phone.classList.remove('no-anim');
  var firstRound = true;
  (async function loop() {
    for (;;) {
      phone.classList.remove('on-board');
      $(chatSteps).add(boardSteps).removeClass('shown');
      if (unread) unread.textContent = '7';
      // 첫 바퀴는 첫 메시지를 바로 띄운다. 들어오자마자 빈 휴대폰이 1.5초 보이면 "고장났나?"로 읽혔다
      if (!firstRound) await wait(600);
      for (var i = 0; i < chatSteps.length; i++) {
        await untilVisible();
        var mine = chatSteps[i].classList.contains('mine');
        if (firstRound && i === 0) { chatSteps[0].classList.add('shown'); await wait(700); continue; }
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
      await wait(1200);
      phone.classList.add('on-board');
      await wait(500);
      for (var j = 0; j < boardSteps.length; j++) {
        await untilVisible();
        boardSteps[j].classList.add('shown');
        if (boardSteps[j].contains(counter)) countUp(counter, Number(counter.dataset.count));
        await wait(450);
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

function countUp(el, target) {
  var start = performance.now();
  var step = function (now) {
    var t = Math.min(1, (now - start) / 700);
    // 회비처럼 100원 단위로 올라가게
    el.textContent = (Math.round(target * (1 - Math.pow(1 - t, 3)) / 100) * 100).toLocaleString('ko-KR');
    if (t < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
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
}

// "나는 무슨 역할형?" 미리 보기. 점수는 RoleScorer 규칙 일부(차 있음 +4, 계획형 +3, 숙소 휴식 +2, 밖 활동 +2)를 그대로 쓴다.
// 차가 없으면 운전은 고르지 않는다(실제 추천과 같다)
var QUIZ_ROLES = [
  { key: 'drive', name: '운전대 잡는 든든형', why: '차 있음 +4', score: function (a) { return a.car === 'yes' ? 4 : -1000; } },
  { key: 'money', name: '엑셀부터 켜는 총무형', why: '계획형 +3', score: function (a) { return a.plan === 'planner' ? 3 : 0; } },
  { key: 'bbq', name: '고기 굽는 숙소 지킴이형', why: '숙소 휴식 선호 +2', score: function (a) { return a.act === 'in' ? 2 : 0; } },
  { key: 'photo', name: '사진 남기는 기록형', why: '밖 활동 선호 +2', score: function (a) { return a.act === 'out' ? 2 : 0; } }
];

function quizAnswers() {
  var pick = function (name) { var c = document.querySelector('input[name="' + name + '"]:checked'); return c ? c.value : null; };
  return { car: pick('car'), plan: pick('plan'), act: pick('act') };
}

function showQuizResult() {
  var a = quizAnswers();
  if (!a.car || !a.plan || !a.act) return;
  var best = QUIZ_ROLES.slice().sort(function (x, y) { return y.score(a) - x.score(a); })[0];
  $('#quiz-role').text(best.name);
  $('#quiz-why').text('근거: ' + best.why);
  $('#quiz-result').prop('hidden', false);
}

$(function () {
  $('.quiz input').on('change', showQuizResult);
  $('#quiz-share').text(shareLabel('카톡으로 결과 던지기', '결과 복사해서 단톡방에 던지기')).on('click', function () {
    shareText('나 여행 가면 "' + $('#quiz-role').text() + '"래 ㅋㅋ 너는? ' + location.origin + '/#quiz', '복사했어요. 단톡방에 던져 보세요.');
  });
  loadMyTrips();
  stickyAfterHero();
  if (reduceMotion || !('IntersectionObserver' in window)) return;
  var phone = document.querySelector('.phone');
  if (phone) {
    playPhone(phone);
    tiltPhone(phone);
  }
  revealOnScroll();
  countStats();
});
