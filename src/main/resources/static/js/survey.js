// 설문: 질문을 한 장씩 고르고, 마지막 장에서 숙소 조건을 고르고 저장한다.
// 귀찮으면 안 한다. 점수에 쓰는 것만 묻고(RoleScorer), 한 번 누르면 다음 장으로 넘어가고, 차 없는 사람에게는 차 질문을 건너뛴다.
// 차 없음 4장, 차 있음 5장
var tripId = $('body').data('trip-id');

// key 와 value 는 Spring SurveyDto.Request 의 칸 이름·enum 값과 같아야 한다
var QUESTIONS = [
  { key: 'activity', title: '여행 가서 더 끌리는 쪽은?', options: [
    { label: '숙소에서 쉬기', value: 'INDOOR' }, { label: '반반', value: 'BOTH' }, { label: '밖에서 놀기', value: 'OUTDOOR' }] },
  { key: 'planning', title: '일정은 어떻게 짜는 편?', options: [
    { label: '분 단위로 계획', value: 'PLANNER' }, { label: '큰 틀만', value: 'OUTLINE' }, { label: '그때그때', value: 'SPONTANEOUS' }] },
  { key: 'hasCar', title: '차를 가져갈 수 있어요?', options: [
    { label: '가능', value: true }, { label: '어려워요', value: false }] },
  // 차 있는 사람만. 출발 동네는 길찾기 출발점, 따로 출발은 운전 점수(-2)에만 쓰여서 차 없는 사람에게는 묻지 않는다
  { key: 'departure', title: '차는 어디서 출발해요?', type: 'departure',
    note: '동네까지만 적어 주세요. 길찾기에만 쓰고, 다른 사람에게는 거리·시간만 보여요.',
    when: function () { return answers.hasCar === true; } },
  // 맡기 싫은 일과 이동 제약을 한 장에. 여러 개 골라도 되고, 없으면 바로 다음
  { key: 'avoid', title: '이건 빼 주세요', type: 'avoid', note: '여러 개 골라도 돼요. 없으면 그냥 넘어가요.' }
];

var CHORES = [{ label: '운전', value: 'DRIVING' }, { label: '요리·바베큐', value: 'COOKING' }, { label: '돈 관리', value: 'MONEY' }];

// Spring Amenity enum 과 같은 목록
var AMENITIES = [
  { key: 'DETACHED', label: '독채' }, { key: 'PRIVATE_BBQ', label: '개별 바베큐' },
  { key: 'HEATED_POOL', label: '온수풀' }, { key: 'KARAOKE', label: '노래방' },
  { key: 'ROOMS_3_PLUS', label: '방 3개 이상' }, { key: 'BATHS_2_PLUS', label: '화장실 2개' },
  { key: 'BARRIER_FREE', label: '1층·엘리베이터' }
];
// 1~5 점은 서버 그대로 두고, 사람에게는 말 세 개만 보인다. 숫자 다섯 개보다 고르기 쉽고 "의견 갈림"(1점)과 "꼭"(5점)은 그대로 남는다
var LEVELS = [{ label: '없어도 돼', value: 1 }, { label: '상관없어', value: 3 }, { label: '꼭 있어야 해', value: 5 }];

var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
var me = null;
var step = 0;
var answers = { separateDeparture: false, mobilityLimited: false, avoid: [] };
var preferences = {};

// 지금 사람에게 보이는 장 수(차 없으면 출발 장이 빠진다)
function visibleQuestions() {
  return QUESTIONS.filter(function (q) { return !q.when || q.when(); });
}

function counterText() {
  var shown = visibleQuestions();
  var index = step < QUESTIONS.length ? shown.indexOf(QUESTIONS[step]) + 1 : shown.length + 1;
  return me.name + ' · ' + index + ' / ' + (shown.length + 1);
}

// when 이 거짓인 장(차 없는 사람의 출발 동네)은 답을 비우고 가던 쪽으로 건너뛴다
function go(next) {
  var forward = next >= step;
  var moved = next !== step;
  step = next;
  var q = QUESTIONS[step];
  if (q && q.when && !q.when()) {
    answers.departure = null;
    answers.separateDeparture = false;
    go(forward ? step + 1 : step - 1);
    return;
  }
  var shown = visibleQuestions().length + 1;
  var index = step < QUESTIONS.length ? visibleQuestions().indexOf(q) + 1 : shown;
  // 첫 장부터 한 칸 차 있어야 "시작했다"가 보인다
  $('#progress').css('width', (index / shown * 100) + '%');
  $('#step-question').prop('hidden', step >= QUESTIONS.length);
  $('#step-preferences').prop('hidden', step < QUESTIONS.length);
  $('#step-saved').prop('hidden', true);
  $('.btn-back').prop('hidden', step === 0);
  if (moved) slideIn(step < QUESTIONS.length ? '#step-question' : '#step-preferences', forward);

  if (step >= QUESTIONS.length) {
    $('#p-count').text(counterText());
    renderPreferences();
    return;
  }
  $('#q-count').text(counterText());
  $('#q-title').text(q.title);
  $('#q-note').text(q.note || '').prop('hidden', !q.note);
  var box = $('#q-options').empty();
  if (q.type === 'departure') renderDeparture(box);
  else if (q.type === 'avoid') renderAvoid(box);
  else renderChoices(q, box);
}

// 하나 고르면 바로 다음 장. 고른 것이 칠해지는 걸 잠깐 보여 주고 넘긴다(바로 넘기면 뭘 눌렀는지 못 본다)
function renderChoices(q, box) {
  q.options.forEach(function (o) {
    $('<button type="button" class="choice">').toggleClass('on', answers[q.key] === o.value).text(o.label).click(function () {
      if (box.hasClass('picking')) return; // 빠르게 두 번 눌러 한 장을 건너뛰지 않게
      answers[q.key] = o.value;
      box.addClass('picking');
      $(this).addClass('on').siblings().removeClass('on');
      setTimeout(function () { box.removeClass('picking'); go(step + 1); }, reduceMotion ? 0 : 180);
    }).appendTo(box);
  });
}

function renderDeparture(box) {
  var field = $('<input type="text" maxlength="30" placeholder="예: 서울 마포구" enterkeyhint="next">')
    .val(answers.departure || '');
  var together = $('<div class="segmented" role="group" aria-label="출발">');
  [{ label: '다 같이 출발', value: false }, { label: '따로 출발', value: true }].forEach(function (o) {
    $('<button type="button">').text(o.label).attr('aria-pressed', answers.separateDeparture === o.value).click(function () {
      answers.separateDeparture = o.value;
      together.children().attr('aria-pressed', false);
      $(this).attr('aria-pressed', true);
    }).appendTo(together);
  });
  var next = $('<button type="button" class="btn primary block">').text('다음').click(function () {
    answers.departure = field.val().trim() || null;
    go(step + 1);
  });
  field.on('keydown', function (e) { if (e.key === 'Enter') next.click(); });
  box.append(field, together, next);
  if (!answers.departure) field.trigger('focus');
}

// 여러 개 고르는 장. 고른 게 없으면 단추가 "빼 줄 것 없어요"라서 한 번에 넘어간다
function renderAvoid(box) {
  var picked = answers.avoid.slice();
  var mobility = answers.mobilityLimited;
  var chips = $('<div class="toggles">');
  var next = $('<button type="button" class="btn primary block">');
  var refresh = function () {
    next.text(picked.length || mobility ? '다음' : '빼 줄 것 없어요');
  };
  CHORES.forEach(function (c) {
    $('<button type="button" class="toggle">').text(c.label).attr('aria-pressed', picked.indexOf(c.value) >= 0).click(function () {
      var at = picked.indexOf(c.value);
      if (at >= 0) picked.splice(at, 1); else picked.push(c.value);
      $(this).attr('aria-pressed', at < 0);
      refresh();
    }).appendTo(chips);
  });
  // 이동 제약은 남에게 사정이 보이지 않아야 한다. 고른 사람과 방장만 보고, 다른 사람에게는 "이동 적은 역할 우선"으로만 보인다
  $('<button type="button" class="toggle">').text('오래 걷거나 이동 많은 일').attr('aria-pressed', mobility).click(function () {
    mobility = !mobility;
    $(this).attr('aria-pressed', mobility);
    refresh();
  }).appendTo(chips);
  next.click(function () {
    answers.avoid = picked;
    answers.mobilityLimited = mobility;
    go(step + 1);
  });
  refresh();
  box.append(chips, $('<p class="muted small">').text('이동 사정은 본인과 방장만 봐요.'), next);
}

// 다음 장은 오른쪽에서, 이전 장은 왼쪽에서 살짝 들어온다. 어느 쪽으로 움직였는지 손이 안다
function slideIn(selector, forward) {
  if (reduceMotion) return;
  var el = $(selector).removeClass('slide-next slide-back');
  void el[0].offsetWidth;
  el.addClass(forward ? 'slide-next' : 'slide-back');
}

// 예전에 2·4 점을 준 사람도 가까운 말에 칠해진다
function levelOf(score) {
  return score <= 2 ? 1 : score >= 4 ? 5 : 3;
}

function renderPreferences() {
  var list = $('#preferences').empty();
  AMENITIES.forEach(function (a) {
    var group = $('<div class="segmented" role="group">').attr('aria-label', a.label);
    LEVELS.forEach(function (l) {
      $('<button type="button">').text(l.label).attr('aria-pressed', levelOf(preferences[a.key]) === l.value).click(function () {
        preferences[a.key] = l.value;
        group.children().attr('aria-pressed', false);
        $(this).attr('aria-pressed', true);
      }).appendTo(group);
    });
    $('<li class="pref">').append($('<span class="strong">').text(a.label), group).appendTo(list);
  });
}

// PUT: /api/trips/1/members/2/survey
// { "activity": "INDOOR", "planning": "PLANNER", "hasCar": true, "separateDeparture": false,
//   "departure": "서울 마포구", "mobilityLimited": false, "avoid": ["DRIVING"], "preferences": { "DETACHED": 5, ... } }
function save() {
  $('#btn-save').prop('disabled', true).text('저장 중…');
  $('#save-error').prop('hidden', true);
  var body = $.extend({}, answers, { preferences: preferences });
  api('PUT', '/api/trips/' + tripId + '/members/' + me.id + '/survey', body)
    .done(showSaved)
    .fail(function () { $('#save-error').prop('hidden', false); })
    .always(function () { $('#btn-save').prop('disabled', false).text('다 했어요'); });
}

// GET: /api/trips/1/members/2/survey  -> SurveyDto.Response (처음이면 404)
// 다시 들어온 사람이 처음부터 다시 고르지 않게 지난 답을 채워 두고, 이미 했으면 저장 화면부터 보여 준다
function loadMine() {
  return api('GET', '/api/trips/' + tripId + '/members/' + me.id + '/survey').done(function (s) {
    answers = { activity: s.activity, planning: s.planning, hasCar: s.hasCar, separateDeparture: !!s.separateDeparture,
      departure: s.departure || null, mobilityLimited: !!s.mobilityLimited, avoid: s.avoid || [] };
    $.extend(preferences, s.preferences);
  });
}

function showSaved() {
  $('#step-question, #step-preferences').prop('hidden', true);
  $('#saved-title').text(me.name + '님 설문을 저장했어요');
  $('#progress').css('width', '100%');
  $('#step-saved').prop('hidden', false);
}

$(function () {
  AMENITIES.forEach(function (a) { preferences[a.key] = 3; });
  $('#go-roles').attr('href', '/trips/' + tripId + '/roles');
  $('#go-stays').attr('href', '/trips/' + tripId + '/stays');

  loadTrip(tripId).done(function (trip) {
    me = trip.members.find(function (m) { return m.id === trip.meId; });
    if (me.surveyDone) loadMine().done(showSaved).fail(function () { go(0); });
    else go(0);
  });

  $('.btn-back').click(function () { go(step - 1); });
  $('#btn-save').click(save);
  $('#btn-restart').click(function () { go(0); });
});
