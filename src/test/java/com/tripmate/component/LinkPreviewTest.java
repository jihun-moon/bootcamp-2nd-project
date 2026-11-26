package com.tripmate.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkPreviewTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/", "http://localhost:8080/api", "http://10.1.2.3/", "http://192.168.0.1/",
            "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://0.0.0.0/", "file:///etc/passwd",
            "http://100.100.100.200/latest/meta-data", "http://8.8.8.8:6379/",
    })
    void 내부_주소와_http_아닌_주소는_막는다(String url) {
        assertThatThrownBy(() -> LinkPreview.requirePublic(URI.create(url)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 막힌_주소면_빈_미리보기를_준다() {
        assertThat(new LinkPreview().fetch("http://127.0.0.1:8080/api/trips/1")).isEqualTo(LinkPreview.Result.EMPTY);
    }

    @Test
    void og_태그를_읽고_상대_이미지_주소를_절대로_바꾼다() {
        var html = """
                <html><head>
                <meta property="og:title" content="숲속 독채 &amp; 바베큐">
                <meta content='/img/a.jpg' property='og:image'>
                <title>다른 제목</title>
                </head></html>
                """;
        var result = LinkPreview.parse(html, URI.create("https://stay.example.com/rooms/1"));
        assertThat(result.title()).isEqualTo("숲속 독채 & 바베큐");
        assertThat(result.imageUrl()).isEqualTo("https://stay.example.com/img/a.jpg");
    }

    @Test
    void 구조화_데이터에서_가장_낮은_원화_가격을_읽는다() {
        var html = """
                <script type="application/ld+json">{"@type":"Hotel","makesOffer":[{"priceSpecification":{"price":238640,"priceCurrency":"KRW",
                "priceComponent":[{"price":320000,"priceCurrency":"KRW"},{"price":-81360,"priceCurrency":"KRW"}]}}]}</script>
                <script type="application/ld+json">{"@type":"Hotel","offers":{"price":"99","priceCurrency":"USD"}}</script>
                """;
        assertThat(LinkPreview.lowestPrice(html)).isEqualTo(238640);
        assertThat(LinkPreview.lowestPrice("<html></html>")).isNull();
    }

    @Test
    void 예약_사이트_제목에서_숙소_이름만_남긴다() {
        assertThat(LinkPreview.shortTitle("가평 BROOK5(브룩5), 남이섬·가평읍, 가평 - 여기어때 특가")).isEqualTo("가평 BROOK5(브룩5)");
        assertThat(LinkPreview.shortTitle("숲속 독채 | 에어비앤비")).isEqualTo("숲속 독채");
        assertThat(LinkPreview.shortTitle("강변 펜션")).isEqualTo("강변 펜션");
    }

    @Test
    void og_제목이_없으면_title_태그를_쓰고_이상한_이미지_주소는_버린다() {
        var html = "<title> 강변 펜션 </title><meta property=\"og:image\" content=\"javascript:alert(1)\">";
        var result = LinkPreview.parse(html, URI.create("https://stay.example.com/"));
        assertThat(result.title()).isEqualTo("강변 펜션");
        assertThat(result.imageUrl()).isNull();
    }
}
