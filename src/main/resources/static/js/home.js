// 첫 화면: 로그인했으면 "내 여행방"을 위에 보여 준다. 처음 온 사람은 소개만 본다

// GET: /api/me/trips  -> [{ "id": 1, "title": "겨울 펜션 2박 3일", "destination": "가평", "startDate": "2027-01-15",
//                           "memberCount": 8, "surveyDone": 3, "host": true }]
// api() 는 401 이면 로그인 화면으로 보낸다. 첫 화면은 로그인 안 한 사람이 보는 게 정상이라 $.ajax 를 직접 쓴다
$(function () {
  $.ajax({ url: '/api/me/trips', type: 'GET' }).done(function (trips) {
    if (!trips.length) return;
    var list = $('#trip-list').empty();
    trips.forEach(function (t) {
      var meta = [t.destination || '지역 미정', t.startDate || '날짜 미정', '설문 ' + t.surveyDone + '/' + t.memberCount].join(' · ');
      var info = $('<div>').append(
        $('<div class="strong">').text(t.title).append(t.host ? $('<span class="tag me">').text('방장') : null),
        $('<div class="muted small">').text(meta));
      $('<li>').append($('<a class="card trip-item">').attr('href', '/trips/' + t.id)
        .append(info, $('<span class="link">').text('들어가기 →'))).appendTo(list);
    });
    $('#my-trips').prop('hidden', false);
  });
});
