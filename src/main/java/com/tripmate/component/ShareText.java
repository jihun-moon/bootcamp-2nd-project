package com.tripmate.component;

import java.util.regex.Pattern;

// 앱의 "공유" 단추로 복사한 글을 그대로 붙여 넣어도 되게 링크와 숙소 이름을 뽑는다.
// 네이버 지도는 미리보기 요청을 막아서(429) 이름은 공유 글에서만 알 수 있다. 글 꼴(2026-11-21 확인):
//   [네이버 지도]\n가평 숲속독채\n경기 가평군 북면 ...\nhttps://naver.me/xxxx
//   [카카오맵] 가평펜션\n경기 가평군 북면 가화로 1477\nhttps://kko.to/xxxx
//   https://www.airbnb.co.kr/rooms/123   (링크만 붙여 넣은 것)
public record ShareText(String url, String name) {

    // 한글이 링크에 바로 붙어 오기도 해서("...xxxx가평펜션") 한글에서 끊는다
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"'\\u3131-\\uD7A3]+");
    private static final Pattern APP_TAG = Pattern.compile("^\\[[^\\]]{1,20}]\\s*");
    // 시·도 이름으로 시작하는 줄은 주소. 한 줄로 이어 붙어 오면("가평펜션 경기 가평군 ...") 이름 뒤 주소를 자른다
    private static final String REGION = "(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|충청|전북|전남|전라|경북|경남|경상|제주)\\S*\\s";
    private static final Pattern ADDRESS = Pattern.compile("^" + REGION);
    private static final Pattern ADDRESS_AFTER = Pattern.compile("\\s" + REGION);
    private static final Pattern PHONE = Pattern.compile("^[\\d\\s()+-]{8,}$");
    private static final int MAX_NAME = 60;

    public static ShareText of(String pasted) {
        var m = URL.matcher(pasted);
        if (!m.find()) return new ShareText(pasted.strip(), null);
        var url = m.group().replaceAll("[).,!]+$", "");
        String name = null;
        for (var line : pasted.replace(m.group(), "\n").split("\\R")) {
            var s = APP_TAG.matcher(line.strip()).replaceFirst("").strip();
            if (s.isEmpty() || ADDRESS.matcher(s).find() || PHONE.matcher(s).matches()
                    || s.contains("확인해 보세요") || s.contains("Check out")) continue;
            var address = ADDRESS_AFTER.matcher(s);
            if (address.find()) s = s.substring(0, address.start());
            name = s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
            break;
        }
        return new ShareText(url, name);
    }
}
