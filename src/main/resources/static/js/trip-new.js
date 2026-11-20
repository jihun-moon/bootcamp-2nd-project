// 여행방 만들기 폼
// POST: /api/trips  { "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17" }
// 만든 사람이 방장이 되고, 만든 방 개요(초대 링크가 맨 위)로 간다
var DRAFT_KEY = 'tripmate.trip-draft';
var FIELDS = ['title', 'destination', 'startDate', 'endDate'];

function readForm() {
  var form = {};
  FIELDS.forEach(function (f) { form[f] = $('#' + f).val().trim(); });
  return form;
}

// 출발·도착을 고르면 "2박 3일"을 바로 보여 준다. 날짜를 잘못 골랐는지 누르기 전에 안다
function showPeriod() {
  var start = $('#startDate').val();
  var end = $('#endDate').val();
  $('#period').text(start && end && end >= start ? formatPeriod(start, end) : '지역과 날짜는 나중에 정해도 돼요.');
}

function create(form) {
  // 서버도 받아 주지만, 도착이 출발보다 앞이면 거의 늘 잘못 고른 것이라 보내기 전에 알린다
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    $('#form-error').text('도착일이 출발일보다 앞이에요.').prop('hidden', false);
    return;
  }
  // 로그인 안 한 채 누르면 api() 가 로그인으로 보낸다. 돌아왔을 때 다시 적지 않게 적은 것을 맡겨 둔다
  sessionStorage.setItem(DRAFT_KEY, JSON.stringify(form));
  $('#btn-create').prop('disabled', true).text('만드는 중…');
  api('POST', '/api/trips', {
    title: form.title,
    destination: form.destination || null,
    startDate: form.startDate || null,
    endDate: form.endDate || null
  }).done(function (trip) {
    sessionStorage.removeItem(DRAFT_KEY);
    location.href = '/trips/' + trip.id;
  }).fail(function (xhr) {
    if (xhr.status === 401) return;
    sessionStorage.removeItem(DRAFT_KEY); // 다음에 들어왔을 때 저절로 또 만들지 않게
    $('#form-error').text('방을 만들지 못했어요. 여행 이름을 확인해 주세요.').prop('hidden', false);
    $('#btn-create').prop('disabled', false).text('방 만들고 초대 링크 받기');
  });
}

$(function () {
  $('#startDate, #endDate').on('change', showPeriod);
  // 도착을 고를 때 출발 이전 날짜는 달력에서 못 고르게
  $('#startDate').on('change', function () { $('#endDate').attr('min', this.value); });
  $('#trip-form').submit(function (e) {
    e.preventDefault();
    create(readForm());
  });

  // 로그인하고 돌아왔으면 적어 둔 것을 채우고, 이미 한 번 눌렀던 것이니 바로 만든다
  var draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || 'null');
  if (draft && draft.title) {
    FIELDS.forEach(function (f) { $('#' + f).val(draft[f] || ''); });
    showPeriod();
    create(draft);
  } else {
    $('#title').trigger('focus');
  }
});
