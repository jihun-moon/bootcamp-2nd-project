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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

// 숙소 링크의 og:title, og:image 를 읽는다. 크롤링은 하지 않고 공개 메타 태그 두 개만 본다.
// 사용자가 준 주소로 서버가 직접 요청하므로 내부망(localhost, 10.x, 클라우드 메타데이터 169.254...)으로 못 가게 막는다
@Component
public class LinkPreview {

    public record Result(String title, String imageUrl) {
        static final Result EMPTY = new Result(null, null);
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>([^<]{1,300})</title>", Pattern.CASE_INSENSITIVE);
    // 메타 태그마다 정규식을 새로 만들면 숙소 하나에 수백 개가 생긴다. 쓰는 속성 셋만 미리 만들어 둔다
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
                requirePublic(uri);
                var request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                        .header("User-Agent", "TripMate-LinkPreview").GET().build();
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
        String title = null, image = null;
        var tags = META.matcher(html);
        while (tags.find()) {
            var tag = tags.group();
            var key = attr(tag, "property");
            if (key == null) key = attr(tag, "name");
            var content = attr(tag, "content");
            if (key == null || content == null) continue;
            if (title == null && key.equalsIgnoreCase("og:title")) title = content;
            if (image == null && key.equalsIgnoreCase("og:image")) image = content;
        }
        if (title == null) {
            var t = TITLE.matcher(html);
            if (t.find()) title = t.group(1);
        }
        return new Result(clean(title), absoluteHttp(image, base));
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
