// 초대 링크로 들어오기
// POST: /api/invites/3f9a1c2b7d4e  -> { "tripId": 1 }
// 로그인 전이면 api() 가 로그인 화면으로 보냈다가 이 주소로 돌아온다. 들어오면 할 일(설문)로 바로 간다
api('POST', '/api/invites/' + encodeURIComponent($('body').data('invite-code')))
  .done(function (res) { location.replace('/trips/' + res.tripId + '/survey'); })
  .fail(function (xhr) {
    if (xhr.status !== 401) $('#message').text('초대 링크가 맞지 않아요. 방장에게 링크를 다시 받아 주세요.');
  });
