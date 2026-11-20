// 체험 방: 탭만 바꾸고, 바꾸는 동작(카드 옮기기·투표)은 막고 실제 방을 만들라고 안내한다
function showTab(name) {
  $('.tab').each(function () {
    var on = this.dataset.tab === name;
    $(this).toggleClass('active', on).attr('aria-selected', on);
  });
  $('[data-panel]').each(function () { this.hidden = this.dataset.panel !== name; });
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

$(function () {
  $('.tab').on('click', function () { showTab(this.dataset.tab); });
  $('[data-goto]').on('click', function (e) { e.preventDefault(); showTab(this.dataset.goto); });
  $('.member-card, [data-demo-action]').on('click', function () {
    showToast('체험 방이라 바뀌지 않아요. 내 여행방을 만들면 바로 쓸 수 있어요.');
  });
});
