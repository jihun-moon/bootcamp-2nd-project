// 카카오 로그인 뒤 돌아갈 곳
// login.js 가 카카오로 떠나기 전에 맡겨 둔 곳으로 돌아간다
var next = sessionStorage.getItem('tripmate.next');
sessionStorage.removeItem('tripmate.next');
location.replace(safeNext(next));
