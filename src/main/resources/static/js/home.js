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
  var typing = $('<li class="typing" aria-hidden="true"><i></i><i></i><i></i></li>');

  phone.classList.add('no-anim', 'playing');
  void phone.offsetHeight; // 숨긴 상태를 먼저 그리게 한 뒤 전환을 다시 켠다
  phone.classList.remove('no-anim');
  (async function loop() {
    for (;;) {
      phone.classList.remove('on-board');
      $(chatSteps).add(boardSteps).removeClass('shown');
      await wait(600);
      for (var i = 0; i < chatSteps.length; i++) {
        await untilVisible();
        var mine = chatSteps[i].classList.contains('mine');
        // "…"(입력 중)은 남이 쓸 때만. 내가 보내는 링크는 바로 붙는다
        if (!mine) {
          typing.insertBefore(chatSteps[i]);
          await wait(900);
          typing.detach();
        }
        chatSteps[i].classList.add('shown');
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
  sticky.classList.add('waiting');
  new IntersectionObserver(function (entries) {
    sticky.classList.toggle('waiting', entries[0].isIntersecting);
  }).observe(heroCta);
}

$(function () {
  loadMyTrips();
  stickyAfterHero();
  if (reduceMotion || !('IntersectionObserver' in window)) return;
  var phone = document.querySelector('.phone');
  if (phone) {
    playPhone(phone);
    tiltPhone(phone);
  }
  revealOnScroll();
});
