// 방 개요: 설문 진행과 참여자. 누가 들어오거나 설문을 끝내면(SSE members) 다시 그린다
var tripId = $('body').data('trip-id');
var inviteLink = '';

function render(trip) {
  var done = trip.members.filter(function (m) { return m.surveyDone; }).length;
  $('#survey-count').text(done + '/' + trip.members.length);
  $('#survey-bar').css('width', (done / trip.members.length * 100) + '%');

  inviteLink = location.origin + '/join/' + trip.inviteCode;
  var list = $('#members').empty();
  trip.members.forEach(function (m) {
    var info = $('<div>')
      .append($('<div class="strong">').text(m.name + (m.host ? ' · 방장' : '')))
      .append($('<div class="muted small">').text(m.surveyDone ? '설문 완료' : '설문 전'));
    $('<li class="card row">').append(avatar(m.name, m.imageUrl), info).appendTo(list);
  });
}

$(function () {
  loadTrip(tripId).done(function (trip) {
    render(trip);
    listen(tripId, function () { loadTrip(tripId).done(render); });
  });

  // 클립보드 권한이 없으면 링크를 직접 보여 줘서 손으로 복사하게 한다
  $('#btn-invite').click(function () {
    navigator.clipboard.writeText(inviteLink)
      .then(function () {
        $('#btn-invite').text('복사했어요');
        setTimeout(function () { $('#btn-invite').text('초대 링크 복사'); }, 2000);
      })
      .catch(function () { prompt('이 링크를 복사해 친구에게 보내 주세요', inviteLink); });
  });
});
