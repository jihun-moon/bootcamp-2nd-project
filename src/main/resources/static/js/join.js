// 초대 링크로 들어오기. 로그인돼 있으면 바로 참여, 아니면 "이름만 쓰고 바로 하기"(손님, docs/19)와 카카오로 시작
// POST: /api/invites/3f9a1c2b7d4e                                 -> { "tripId": 1 }   (로그인한 사람)
// POST: /api/invites/3f9a1c2b7d4e/guest  { "name": "민재" }   -> { "tripId": 1 }   (로그인 없이, 손님 계정이 생기고 로그인된다)
// 들어오면 할 일(설문)로 바로 간다
var inviteCode = encodeURIComponent($('body').data('invite-code'));

function enterTrip(res) {
  location.replace('/trips/' + res.tripId + '/survey');
}

function wrongLink() {
  $('#message').text('초대 링크가 맞지 않아요. 방장에게 링크를 다시 받아 주세요.').prop('hidden', false);
  $('#guest-form').prop('hidden', true);
}

function askName() {
  $('#message').prop('hidden', true);
  $('#guest-form').prop('hidden', false);
  $('#guest-name').trigger('focus');
}

$(function () {
  // 카카오로 시작하면 로그인 뒤 이 초대 화면으로 돌아와 바로 들어간다(login-done.js 가 tripmate.next 를 읽는다)
  $('#guest-kakao').on('click', function () {
    try { sessionStorage.setItem('tripmate.next', location.pathname); } catch (e) { /* 저장이 막혀도 로그인은 된다 */ }
  });
  $('#guest-form').on('submit', function (e) {
    e.preventDefault();
    var name = $('#guest-name').val().trim();
    if (!name) { $('#guest-name').trigger('focus'); return; }
    var button = $(this).find('button[type="submit"]').prop('disabled', true);
    api('POST', '/api/invites/' + inviteCode + '/guest', { name: name }).done(enterTrip).fail(function (xhr) {
      button.prop('disabled', false);
      if (xhr.status === 404) wrongLink();
      else if (xhr.status === 429) $('#guest-error').text('지금은 손님으로 들어갈 수 없어요. 카카오로 시작해 주세요.').prop('hidden', false);
      else if (xhr.status === 400) $('#guest-error').text('이름을 20자 안으로 적어 주세요.').prop('hidden', false);
    });
  });
  // 로그인돼 있는지 먼저 본다(401 이면 로그인 화면으로 보내지 않고 이름 칸을 연다)
  $.ajax({ url: '/api/me', type: 'GET' }).done(function () {
    api('POST', '/api/invites/' + inviteCode).done(enterTrip).fail(function (xhr) { if (xhr.status === 404) wrongLink(); });
  }).fail(function (xhr) {
    if (xhr.status === 401) askName(); else wrongLink();
  });
});
