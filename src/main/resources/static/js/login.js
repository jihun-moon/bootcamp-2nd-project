// 로그인: 카카오 하나. 로그인 뒤 돌아갈 곳(next)을 맡겨 두고 카카오로 간다
var NEXT_KEY = 'tripmate.next';
var next = safeNext(new URLSearchParams(location.search).get('next'));

$(function () {
  // GET: /api/auth/options  -> { "kakao": true }  키가 없는 서버면 단추 대신 안내를 보인다
  api('GET', '/api/auth/options').done(function (options) {
    $('#btn-kakao').prop('hidden', !options.kakao);
    $('#kakao-off').prop('hidden', options.kakao);
  });

  // 카카오 로그인은 Spring 으로 가는 페이지 이동이라 돌아올 곳을 잠깐 맡겨 둔다(login-done.html 이 꺼낸다)
  $('#btn-kakao').click(function () {
    sessionStorage.setItem(NEXT_KEY, next);
  });
});
