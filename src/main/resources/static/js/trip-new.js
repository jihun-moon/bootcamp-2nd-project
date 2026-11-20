// 여행방 만들기 폼
// POST: /api/trips  { "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17" }
// 만든 사람이 방장이 되고, 만든 방 개요(초대 링크가 맨 위)로 간다
$('#trip-form').submit(function (e) {
  e.preventDefault();
  var start = $('#startDate').val();
  var end = $('#endDate').val();
  // 서버도 받아 주지만, 도착이 출발보다 앞이면 거의 늘 잘못 고른 것이라 보내기 전에 알린다
  if (start && end && end < start) {
    $('#form-error').text('도착일이 출발일보다 앞이에요.').prop('hidden', false);
    return;
  }
  $('#btn-create').prop('disabled', true);
  api('POST', '/api/trips', {
    title: $('#title').val().trim(),
    destination: $('#destination').val().trim() || null,
    startDate: start || null,
    endDate: end || null
  }).done(function (trip) { location.href = '/trips/' + trip.id; })
    .fail(function (xhr) {
      if (xhr.status !== 401) $('#form-error').text('방을 만들지 못했어요. 여행 이름을 확인해 주세요.').prop('hidden', false);
    })
    .always(function () { $('#btn-create').prop('disabled', false); });
});
