// 방 개요: 다음 할 일, 초대 링크, 설문 진행과 참여자. 누가 들어오거나 설문을 끝내면(SSE members) 다시 그린다
var tripId = $('body').data('trip-id');
var trip = null;

function render(t) {
  trip = t;
  var me = trip.members.find(function (m) { return m.id === trip.meId; });
  var notYet = trip.members.filter(function (m) { return !m.surveyDone; });
  var done = trip.members.length - notYet.length;

  $('#invite-link').val(location.origin + '/join/' + trip.inviteCode);
  $('#survey-count').text(done + '/' + trip.members.length + '명');
  $('#survey-bar').css('width', (done / trip.members.length * 100) + '%');
  renderNext(me, notYet);

  var list = $('#members').empty();
  trip.members.forEach(function (m) {
    var name = $('<div class="strong">').text(m.name);
    if (m.host) name.append($('<span class="tag">').text('방장'));
    if (m.id === trip.meId) name.append($('<span class="tag me">').text('나'));
    var info = $('<div>').append(name,
      $('<span class="chip">').toggleClass('on', m.surveyDone).text(m.surveyDone ? '설문 완료' : '설문 전'));
    $('<li class="row">').append(avatar(m.name, m.imageUrl), info).appendTo(list);
  });
}

// 내 상태에 따라 할 일 하나를 크게 보여 준다: 혼자면 초대 → 내 설문 → (방장) 안 한 사람 부르기 → 결과 보기
function renderNext(me, notYet) {
  var actions = $('#next-actions').empty();
  var link = function (label, href, primary) {
    return $('<a class="btn">').toggleClass('primary', !!primary).attr('href', href).text(label);
  };
  if (trip.members.length === 1) {
    $('#next-title').text('먼저 친구들을 불러요');
    $('#next-text').text('아래 초대 링크를 복사해 단톡방에 붙여 넣으세요. 친구가 누르면 카톡 안에서 바로 열려요.');
    actions.append($('<button type="button" class="btn primary">').text('초대 링크 복사').click(copyInvite));
  } else if (!me.surveyDone) {
    $('#next-title').text('내 설문부터 해요 (1분)');
    $('#next-text').text('차가 있는지, 맡기 싫은 일, 숙소에서 중요한 것만 물어요. 답에 따라 역할이 추천돼요.');
    actions.append(link('설문하기', '/trips/' + tripId + '/survey', true));
  } else if (notYet.length && me.host) {
    $('#next-title').text(notYet.length + '명이 아직 설문 전이에요');
    $('#next-text').text(notYet.map(function (m) { return m.name; }).join(', ') + ' 님에게 단톡방으로 한 번 더 알려 주세요.');
    actions.append($('<button type="button" class="btn primary">').text('재촉 글 복사').click(copyReminder),
      link('지금 역할 보기', '/trips/' + tripId + '/roles'));
  } else if (notYet.length) {
    $('#next-title').text('설문 고마워요!');
    $('#next-text').text('다른 사람 ' + notYet.length + '명이 끝나면 방장이 역할을 추천받아요. 숙소 링크는 지금 올려도 돼요.');
    actions.append(link('숙소 올리기', '/trips/' + tripId + '/stays', true), link('역할 보드', '/trips/' + tripId + '/roles'));
  } else {
    $('#next-title').text('모두 설문을 끝냈어요');
    $('#next-text').text(me.host ? '역할 보드에서 "설문으로 추천받기"를 눌러 주세요.' : '역할 보드와 숙소 투표를 확인해 주세요.');
    actions.append(link('역할 보드', '/trips/' + tripId + '/roles', true), link('숙소 투표', '/trips/' + tripId + '/stays'));
  }
}

// 복사가 막힌 브라우저면 링크 칸을 골라 둬서 길게 눌러 직접 복사하게 한다
function copyInvite() {
  copyText($('#invite-link').val()).then(
    function () { showToast('초대 링크를 복사했어요. 단톡방에 붙여 넣어 주세요.'); },
    function () { $('#invite-link')[0].select(); showNotice('복사가 막혀 있어요. 링크 칸을 길게 눌러 복사해 주세요.'); });
}

function copyReminder() {
  var names = trip.members.filter(function (m) { return !m.surveyDone; }).map(function (m) { return m.name; });
  var text = '[' + trip.title + '] ' + names.join(', ') + ' 님 설문 1분만 부탁해요. 역할이랑 숙소 정하려고요\n'
    + $('#invite-link').val();
  copyText(text).then(function () { showToast('재촉 글을 복사했어요. 단톡방에 붙여 넣어 주세요.'); });
}

$(function () {
  $('#btn-invite').click(copyInvite);
  loadTrip(tripId).done(function (t) {
    render(t);
    listen(tripId, function () { loadTrip(tripId).done(render); });
  });
});
