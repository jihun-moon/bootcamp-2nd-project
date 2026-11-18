// 설문: 질문 6개를 한 장씩 고르고, 마지막 장에서 숙소 조건 중요도를 매겨 한 번에 저장한다
var tripId = $('body').data('trip-id');

// key 와 value 는 Spring SurveyDto.Request 의 칸 이름·enum 값과 같아야 한다
var QUESTIONS = [
  { key: 'activity', title: '여행 가서 더 끌리는 쪽은?', options: [
    { label: '숙소에서 쉬기', value: 'INDOOR' }, { label: '반반', value: 'BOTH' }, { label: '밖에서 놀기', value: 'OUTDOOR' }] },
  { key: 'planning', title: '일정은 어떻게 짜는 편?', options: [
    { label: '분 단위로 계획', value: 'PLANNER' }, { label: '큰 틀만', value: 'OUTLINE' }, { label: '그때그때', value: 'SPONTANEOUS' }] },
  { key: 'hasCar', title: '차를 가져갈 수 있나요?', options: [
    { label: '가능', value: true }, { label: '어려움', value: false }] },
  { key: 'separateDeparture', title: '어디서 출발하나요?', options: [
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

function show(step) {
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
    q.options.forEach(function (o) {
      $('<button type="button" class="choice">').text(o.label).click(function () {
        answers[q.key] = o.value;
        go(step + 1);
      }).appendTo(box);
    });
  } else {
    $('#p-count').text(me.name + ' · ' + TOTAL + ' / ' + TOTAL);
    renderPreferences();
  }
}

function go(next) {
  step = next;
  show(step);
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
//   "mobilityLimited": false, "avoid": ["DRIVING"], "preferences": { "DETACHED": 5, ... } }
function save() {
  $('#btn-save').prop('disabled', true).text('저장 중…');
  $('#save-error').prop('hidden', true);
  var body = $.extend({}, answers, { preferences: preferences });
  api('PUT', '/api/trips/' + tripId + '/members/' + me.id + '/survey', body)
    .done(function () {
      $('#step-question, #step-preferences').prop('hidden', true);
      $('#saved-title').text(me.name + '님 설문을 저장했어요');
      $('#step-saved').prop('hidden', false);
    })
    .fail(function () { $('#save-error').prop('hidden', false); })
    .always(function () { $('#btn-save').prop('disabled', false).text('저장하기'); });
}

$(function () {
  AMENITIES.forEach(function (a) { preferences[a.key] = 3; });

  loadTrip(tripId).done(function (trip) {
    me = trip.members.find(function (m) { return m.id === trip.meId; });
    show(0);
  });

  $('.btn-back').click(function () { go(step - 1); });
  $('#btn-save').click(save);
  $('#btn-restart').click(function () { go(0); });
});
