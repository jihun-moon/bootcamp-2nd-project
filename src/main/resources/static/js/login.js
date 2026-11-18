// 개발용 로그인에서 바로 고를 수 있는 예시 방 참여자(DemoTripLoader 와 같은 이름)
var DEMO_NAMES = ['재현', '민지', '도윤', '서연', '하준', '지우', '예린', '시우'];
var NEXT_KEY = 'tripmate.next';
var next = safeNext(new URLSearchParams(location.search).get('next'));

// POST: /api/dev/login  { "name": "민지" }  -> 204, 세션 쿠키
function devLogin(name) {
  api('POST', '/api/dev/login', { name: name })
    .done(function () { location.href = next; })
    .fail(function () { $('#login-error').text('개발용 로그인에 실패했어요.').prop('hidden', false); });
}

$(function () {
  // GET: /api/auth/options  -> { "kakao": true, "dev": false }  어떤 로그인 버튼을 보여 줄지
  api('GET', '/api/auth/options').done(function (options) {
    $('#btn-kakao').prop('hidden', !options.kakao);
    $('#kakao-off').prop('hidden', options.kakao);
    $('#dev-login').prop('hidden', !options.dev);
  });

  // 카카오 로그인은 Spring 으로 가는 페이지 이동이라 돌아올 곳을 잠깐 맡겨 둔다(login-done.html 이 꺼낸다)
  $('#btn-kakao').click(function () {
    sessionStorage.setItem(NEXT_KEY, next);
  });

  DEMO_NAMES.forEach(function (name) {
    $('<button type="button" class="btn">').text(name).click(function () { devLogin(name); }).appendTo('#demo-names');
  });
  $('#dev-form').submit(function (e) {
    e.preventDefault();
    devLogin($('#dev-name').val());
  });
});
