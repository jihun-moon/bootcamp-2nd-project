// 정산 받는 링크 화면: 내 이름을 누르면 내가 보내거나 받을 줄만 진하게. 줄은 서버가 그렸고 여기서는 표시만 바꾼다.
// 고른 이름은 이 휴대폰에만 기억한다(다음에 열어도 바로 내 줄). 저장소가 막힌 창이면 매번 고른다
var WHO_KEY = 'tripmate.settle.who.' + location.pathname;

function markMine(name) {
  $('.settle-row').each(function () {
    var row = $(this);
    row.toggleClass('mine', !!name && (row.attr('data-from') === name || row.attr('data-to') === name));
  });
  $('#who button').each(function () { $(this).attr('aria-pressed', $(this).text() === name); });
}

$(function () {
  var names = [];
  // .data() 는 "123"·"true" 같은 이름을 숫자·참거짓으로 바꿔 버려 버튼 글자와 안 맞는다. 글자 그대로 읽는다
  $('.settle-row').each(function () {
    [$(this).attr('data-from'), $(this).attr('data-to')].forEach(function (n) { if (names.indexOf(n) < 0) names.push(n); });
  });
  if (names.length < 2) return;
  var who = $('#who').prop('hidden', false).append($('<span class="muted small">').text('나는'));
  names.forEach(function (n) {
    $('<button type="button" class="toggle" aria-pressed="false">').text(n).click(function () {
      var picked = $(this).attr('aria-pressed') === 'true' ? null : n;
      markMine(picked);
      try { picked ? localStorage.setItem(WHO_KEY, picked) : localStorage.removeItem(WHO_KEY); } catch (e) { /* 기억만 못 한다 */ }
    }).appendTo(who);
  });
  try { markMine(localStorage.getItem(WHO_KEY)); } catch (e) { /* 기억만 못 한다 */ }
});
