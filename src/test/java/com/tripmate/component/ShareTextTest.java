package com.tripmate.component;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

// 앱 공유 글을 그대로 붙여 넣어도 링크와 이름이 나오는지. 글 꼴은 2026-11-21 각 앱 공유 단추로 확인한 것
class ShareTextTest {

    @Test
    void 네이버_지도_공유_글에서_이름과_링크를_뽑는다() {
        var s = ShareText.of("[네이버 지도]\n가평 숲속독채 펜션\n경기 가평군 북면 가화로 1477\n031-123-4567\nhttps://naver.me/5abcDEF");
        assertThat(s.url()).isEqualTo("https://naver.me/5abcDEF");
        assertThat(s.name()).isEqualTo("가평 숲속독채 펜션");
    }

    @Test
    void 카카오맵_공유_글은_꼬리표_뒤가_이름이다() {
        var s = ShareText.of("[카카오맵] 가평펜션\n경기 가평군 북면 가화로 1477\n\nhttps://kko.to/AbC123xyz");
        assertThat(s.url()).isEqualTo("https://kko.to/AbC123xyz");
        assertThat(s.name()).isEqualTo("가평펜션");
    }

    @Test
    void 줄바꿈이_빠져_한_줄로_와도_주소_앞까지가_이름이다() {
        var s = ShareText.of("[네이버 지도] 가평 숲속독채 펜션 경기 가평군 북면 가화로 1477 https://naver.me/5abcDEF");
        assertThat(s.name()).isEqualTo("가평 숲속독채 펜션");
        assertThat(s.url()).isEqualTo("https://naver.me/5abcDEF");
    }

    @Test
    void 링크만이거나_안내_문장이면_이름은_비운다() {
        assertThat(ShareText.of("https://www.airbnb.co.kr/rooms/123?check_in=2026-12-01").name()).isNull();
        var airbnb = ShareText.of("에어비앤비에서 이 숙소를 확인해 보세요! https://www.airbnb.co.kr/rooms/123.");
        assertThat(airbnb.url()).isEqualTo("https://www.airbnb.co.kr/rooms/123");
        assertThat(airbnb.name()).isNull();
    }

    @Test
    void 링크에_한글이_붙어_와도_끊는다() {
        assertThat(ShareText.of("https://kko.to/AbC가평펜션").url()).isEqualTo("https://kko.to/AbC");
    }

    @Test
    void 카카오맵_지도_주소는_장소_소개_페이지로_바꿔_읽는다() {
        assertThat(LinkPreview.placePage(URI.create("https://map.kakao.com/?itemId=12345565")))
                .isEqualTo(URI.create("https://place.map.kakao.com/12345565"));
        var other = URI.create("https://www.yeogi.com/domestic-accommodations/1?itemId=9");
        assertThat(LinkPreview.placePage(other)).isEqualTo(other);
    }

    @Test
    void 지도_앱_첫_화면_제목은_숙소_이름으로_쓰지_않는다() {
        var html = "<meta property=\"og:title\" content=\"네이버 지도\"><meta property=\"og:image\" content=\"https://a/og-map.png\">";
        assertThat(LinkPreview.parse(html, URI.create("https://map.naver.com/p/entry/place/1"))).isEqualTo(LinkPreview.Result.EMPTY);
        // 지금 네이버 지도는 제목 없이 기본 사진만 준다(2026-11-21)
        var untitled = "<meta property=\"og:image\" content=\"https://ssl.pstatic.net/static/maps/assets/images/og-map-400x200.png\">";
        assertThat(LinkPreview.parse(untitled, URI.create("https://map.naver.com/p/entry/place/1"))).isEqualTo(LinkPreview.Result.EMPTY);
        var kakao = "<meta property=\"og:title\" content=\"가평펜션\"><meta property=\"og:image\" content=\"//img1.kakaocdn.net/a.jpg\"><title>가평펜션 | 카카오맵</title>";
        var result = LinkPreview.parse(kakao, URI.create("https://place.map.kakao.com/12345565"));
        assertThat(result.title()).isEqualTo("가평펜션");
        assertThat(result.imageUrl()).isEqualTo("https://img1.kakaocdn.net/a.jpg");
    }
}
