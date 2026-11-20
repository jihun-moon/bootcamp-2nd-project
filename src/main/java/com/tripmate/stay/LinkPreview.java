package com.tripmate.stay;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

// 숙소 링크의 og:title, og:image, og:description 을 읽는다. 크롤링은 하지 않고 공개 메타 태그만 본다.
// 사용자가 준 주소로 서버가 직접 요청하므로 내부망(localhost, 10.x, 클라우드 메타데이터 169.254...)으로 못 가게 막는다
@Component
public class LinkPreview {

    // description 은 저장하지 않고 조건 낱말 찾기(Amenity.guess)에만 쓴다
    public record Result(String title, String imageUrl, String description, Integer price) {
        static final Result EMPTY = new Result(null, null, null, null);
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    // 예약 사이트는 검색엔진용 구조화 데이터(schema.org Offer)에 그날 1박 값을 넣어 둔다. 원화(KRW)일 때만 읽는다
    private static final Pattern LD_JSON = Pattern.compile("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PRICE = Pattern.compile("\"(?:price|lowPrice)\"\\s*:\\s*\"?(\\d{4,9})(?:\\.\\d+)?\"?");
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>([^<]{1,300})</title>", Pattern.CASE_INSENSITIVE);
    // 메타 태그마다 정규식을 새로 만들면 숙소 하나에 수백 개가 생긴다. 쓰는 속성 셋만 미리 만들어 둔다
    // 지도 앱 첫 화면 제목. 장소 페이지가 아니라 앱 껍데기를 읽은 것이라 이름으로 쓰지 않는다(공유 글의 이름이 대신 들어간다)
    private static final Set<String> APP_TITLES = Set.of("네이버 지도", "NAVER Map", "네이버 플레이스", "카카오맵", "Kakao Map", "다음지도");
    private static final Pattern KAKAO_ITEM = Pattern.compile("(?:^|&)itemId=(\\d{1,15})");
    private static final Map<String, Pattern> ATTRS = Map.of(
            "property", attrPattern("property"), "name", attrPattern("name"), "content", attrPattern("content"));

    // 리다이렉트는 직접 따라간다. 자동으로 따라가면 공개 주소가 내부 주소로 넘겨도 검사를 건너뛴다
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    // 미리보기는 있으면 좋은 것이다. 못 가져오면 빈 값을 주고 숙소는 그대로 저장한다
    public Result fetch(String url) {
        try {
            var uri = URI.create(url);
            for (var hop = 0; hop <= MAX_REDIRECTS; hop++) {
                uri = placePage(uri);
                requirePublic(uri);
                // 카톡·슬랙 미리보기처럼 "compatible; 이름" 꼴로 우리라고 밝힌다. 브라우저인 척하지 않는다
                var request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                        .header("User-Agent", "Mozilla/5.0 (compatible; TripMate-LinkPreview/1.0)")
                        .header("Accept-Language", "ko-KR,ko;q=0.9").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var body = response.body()) {
                    var location = response.headers().firstValue("location");
                    if (response.statusCode() / 100 == 3 && location.isPresent()) {
                        uri = uri.resolve(location.get());
                        continue;
                    }
                    if (response.statusCode() != 200) return Result.EMPTY;
                    var html = readWithin(body);
                    return html == null ? Result.EMPTY : parse(html, uri);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return Result.EMPTY;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Result.EMPTY;
    }

    // HttpRequest.timeout 은 응답 머리까지만 잰다. 본문을 1바이트씩 천천히 보내는 서버면 readNBytes 가 끝없이 붙잡혀
    // 요청 스레드가 하나씩 묶인다. 읽기를 따로 돌려 TIMEOUT 이 지나면 버리고, 호출한 쪽 try 가 스트림을 닫아 읽기도 풀린다
    private static String readWithin(java.io.InputStream body) {
        var read = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(body.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        });
        try {
            return read.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // 카카오맵 공유 링크(kko.to)는 map.kakao.com/?itemId=123 으로 넘어간다. 그 주소는 미리보기 태그가 없는 지도 화면이라
    // 같은 장소의 소개 페이지(place.map.kakao.com/123, og:title·사진 있음)로 바꿔 읽는다
    static URI placePage(URI uri) {
        if (!"map.kakao.com".equals(uri.getHost()) || uri.getRawQuery() == null) return uri;
        var item = KAKAO_ITEM.matcher(uri.getRawQuery());
        return item.find() ? URI.create("https://place.map.kakao.com/" + item.group(1)) : uri;
    }

    // shortcut: 검사한 뒤 HttpClient 가 DNS 를 다시 찾으므로 DNS 리바인딩은 못 막는다. 공개 서비스로 열면 검사한 IP 로 직접 붙게 바꾼다
    static void requirePublic(URI uri) throws IOException {
        var scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) throw new IllegalArgumentException("http(s) 만");
        if (uri.getHost() == null) throw new IllegalArgumentException("호스트 없음");
        // 내부 관리 포트(8080, 6379 등)를 두드리는 데 쓰이지 않게 웹 기본 포트만
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) throw new IllegalArgumentException("포트");
        for (var address : InetAddress.getAllByName(uri.getHost())) {
            var bytes = address.getAddress();
            var ipv6UniqueLocal = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
            // 100.64.0.0/10 공유 주소. 일부 클라우드는 여기에 메타데이터 서버를 둔다(100.100.100.200)
            var sharedAddress = bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress() || ipv6UniqueLocal || sharedAddress) {
                throw new IllegalArgumentException("내부 주소");
            }
        }
    }

    static Result parse(String html, URI base) {
        String title = null, image = null, description = null;
        var tags = META.matcher(html);
        while (tags.find()) {
            var tag = tags.group();
            var key = attr(tag, "property");
            if (key == null) key = attr(tag, "name");
            var content = attr(tag, "content");
            if (key == null || content == null) continue;
            if (title == null && key.equalsIgnoreCase("og:title")) title = content;
            if (image == null && key.equalsIgnoreCase("og:image")) image = content;
            if (description == null && (key.equalsIgnoreCase("og:description") || key.equalsIgnoreCase("description"))) {
                description = content;
            }
        }
        if (title == null) {
            var t = TITLE.matcher(html);
            if (t.find()) title = t.group(1);
        }
        var name = shortTitle(clean(title));
        // 제목이 없거나 앱 이름뿐이면 사진도 앱 기본 그림(네이버 지도 og-map)이라 같이 버린다
        if (name == null || APP_TITLES.contains(name)) return Result.EMPTY;
        return new Result(name, absoluteHttp(image, base), clean(description), lowestPrice(html));
    }

    // 회원가·정가처럼 값이 여럿이면 가장 낮은 값. 할인 금액(-81360)처럼 음수나 천 원 아래 값은 버린다
    static Integer lowestPrice(String html) {
        Integer lowest = null;
        var blocks = LD_JSON.matcher(html);
        while (blocks.find()) {
            var json = blocks.group(1);
            if (!json.contains("KRW")) continue;
            var prices = PRICE.matcher(json);
            while (prices.find()) {
                var value = Integer.parseInt(prices.group(1));
                if (value >= 1000 && (lowest == null || value < lowest)) lowest = value;
            }
        }
        return lowest;
    }

    private static Pattern attrPattern(String name) {
        return Pattern.compile("\\b" + name + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
    }

    private static String attr(String tag, String name) {
        var m = ATTRS.get(name).matcher(tag);
        if (!m.find()) return null;
        return m.group(2) != null ? m.group(2) : m.group(3);
    }

    private static String clean(String text) {
        if (text == null) return null;
        var s = text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").strip();
        if (s.isEmpty()) return null;
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    // 예약 사이트 제목은 "가평 BROOK5(브룩5), 남이섬·가평읍, 가평 - 여기어때 특가" 꼴이다. 카드에는 숙소 이름만 있으면 된다.
    // 끝의 " - 사이트 이름"·" | 사이트 이름"을 떼고, 쉼표 뒤 지역 설명도 뗀다. 다 떼어 비면 원래 제목을 쓴다
    static String shortTitle(String title) {
        if (title == null) return null;
        var s = title;
        for (var sep : new String[]{" - ", " | ", " : "}) {
            var at = s.lastIndexOf(sep);
            if (at > 0) s = s.substring(0, at);
        }
        var comma = s.indexOf(", ");
        if (comma > 0) s = s.substring(0, comma);
        return s.isBlank() ? title : s.strip();
    }

    // 화면에 <img src> 로 넣으니 http(s) 만 남긴다. javascript:, data: 같은 것은 버린다
    private static String absoluteHttp(String url, URI base) {
        if (url == null || url.isBlank()) return null;
        try {
            var resolved = base.resolve(url.strip().replace("&amp;", "&"));
            var scheme = resolved.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme)) ? resolved.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
