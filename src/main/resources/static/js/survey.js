// 설문: 질문을 한 장씩 고르고, 마지막 장에서 숙소 조건 중요도를 매겨 한 번에 저장한다
var tripId = $('body').data('trip-id');

// key 와 value 는 Spring SurveyDto.Request 의 칸 이름·enum 값과 같아야 한다
var QUESTIONS = [
  { key: 'activity', title: '여행 가서 더 끌리는 쪽은?', options: [
    { label: '숙소에서 쉬기', value: 'INDOOR' }, { label: '반반', value: 'BOTH' }, { label: '밖에서 놀기', value: 'OUTDOOR' }] },
  { key: 'planning', title: '일정은 어떻게 짜는 편?', options: [
    { label: '분 단위로 계획', value: 'PLANNER' }, { label: '큰 틀만', value: 'OUTLINE' }, { label: '그때그때', value: 'SPONTANEOUS' }] },
  { key: 'hasCar', title: '차를 가져갈 수 있나요?', options: [
    { label: '가능', value: true }, { label: '어려움', value: false }] },
  // 차를 가져가는 사람만 받는다. 이동·회비 근거(역할 보드)의 길찾기 출발점
  { key: 'departure', title: '차는 어디서 출발해요?', input: '서울 마포구',
    note: '동네까지만 적어 주세요. 길찾기에만 쓰고, 다른 사람에게는 거리·시간만 보여요.',
    when: function () { return answers.hasCar === true; } },
  { key: 'separateDeparture', title: '다 같이 출발하나요?', options: [
    { label: '같이 출발', value: false }, { label: '따로 출발', value: true }] },
  { key: 'mobilityLimited', title: '이동이 불편한 사정이 있나요?',
    note: "답은 본인과 방장만 봅니다. 다른 사람에게는 '이동 적은 역할 우선'으로만 보여요.", options: [
    { label: '없음', value: false }, { label: '있음', value: true }] },
  { key: 'avoid', title: '이것만은 맡기 싫다', options: [
    { label: '운전', value: ['DRIVING'] }, { label: '요리·바베큐', value: ['COOKING'] },
    { label: '돈 관리', value: ['MONEY'] }, { label: '상관없음', value: [] }] }
];

// Spring Amenity enum 과 같은 목록
var AMENITIES = [
  { key: 'DETACHED', label: '독채' }, { key: 'PRIVATE_BBQ', label: '개별 바베큐' },
  { key: 'HEATED_POOL', label: '온수풀' }, { key: 'KARAOKE', label: '노래방' },
  { key: 'ROOMS_3_PLUS', label: '방 3개 이상' }, { key: 'BATHS_2_PLUS', label: '화장실 2개' },
  { key: 'BARRIER_FREE', label: '1층·엘리베이터' }
];
var TOTAL = QUESTIONS.length + 1;

var me = null;
var step = 0;
var answers = {};
var preferences = {};

// 몇 번째 장을 보여 줄지. 질문 장들 다음이 숙소 조건 장이다
// when 이 거짓인 장(차 없는 사람의 출발 동네)은 답을 비우고 가던 쪽으로 건너뛴다
function go(next) {
  var forward = next >= step;
  step = next;
  var skip = QUESTIONS[step] && QUESTIONS[step].when && !QUESTIONS[step].when();
  if (skip) {
    answers[QUESTIONS[step].key] = null;
    go(forward ? step + 1 : step - 1);
    return;
  }
  $('#progress').css('width', (step / TOTAL * 100) + '%');
  $('#step-question').prop('hidden', step >= QUESTIONS.length);
  $('#step-preferences').prop('hidden', step < QUESTIONS.length);
  $('#step-saved').prop('hidden', true);
  $('.btn-back').prop('hidden', step === 0);

  if (step < QUESTIONS.length) {
    var q = QUESTIONS[step];
    $('#q-count').text(me.name + ' · ' + (step + 1) + ' / ' + TOTAL);
    $('#q-title').text(q.title);
    $('#q-note').text(q.note || '').prop('hidden', !q.note);
    var box = $('#q-options').empty();
    if (q.input) {
      var field = $('<input type="text" maxlength="30">').attr('placeholder', '예: ' + q.input).val(answers[q.key] || '');
      var next = $('<button type="button" class="btn primary">').text('다음').click(function () {
        answers[q.key] = field.val().trim();
        go(step + 1);
      });
      field.on('keydown', function (e) { if (e.key === 'Enter') next.click(); });
      box.append(field, next);
      return;
    }
    q.options.forEach(function (o) {
      // 지난 답을 칠해 둔다. avoid 는 배열이라 글자로 바꿔 견준다
      var picked = JSON.stringify(answers[q.key]) === JSON.stringify(o.value);
      $('<button type="button" class="choice">').toggleClass('on', picked).text(o.label).click(function () {
        answers[q.key] = o.value;
        go(step + 1);
      }).appendTo(box);
    });
  } else {
    $('#p-count').text(me.name + ' · ' + TOTAL + ' / ' + TOTAL);
    renderPreferences();
  }
}

function renderPreferences() {
  var list = $('#preferences').empty();
  AMENITIES.forEach(function (a) {
    var scores = $('<div class="row">');
    [1, 2, 3, 4, 5].forEach(function (n) {
      $('<button type="button" class="score">').text(n)
        .toggleClass('on', preferences[a.key] === n)
        .attr('aria-label', a.label + ' ' + n + '점')
        .click(function () {
          preferences[a.key] = n;
          renderPreferences();
        })
        .appendTo(scores);
    });
    $('<li class="between">').append($('<span>').text(a.label), scores).appendTo(list);
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
    .always(function () { $('#btn-save').prop('disabled', false).text('저장하기'); });
}

// GET: /api/trips/1/members/2/survey  -> SurveyDto.Response (처음이면 404)
// 다시 들어온 사람이 처음부터 다시 고르지 않게 지난 답을 채워 두고, 이미 했으면 저장 화면부터 보여 준다
function loadMine() {
  return api('GET', '/api/trips/' + tripId + '/members/' + me.id + '/survey').done(function (s) {
    answers = { activity: s.activity, planning: s.planning, hasCar: s.hasCar, separateDeparture: s.separateDeparture,
      departure: s.departure || null, mobilityLimited: !!s.mobilityLimited, avoid: s.avoid };
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
