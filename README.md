# TripMate

친구 3~15명이 같이 가는 국내 여행을 **가기 전 정하기부터 다녀와서 나누기까지** 한 방에서 하는 웹 서비스다.
설치 없이 카톡 링크로 들어오고, 친구는 이름만 쓰고 바로 참여할 수 있다.
부트캠프 2기 팀 프로젝트(박재현·문지훈·김현수).

| 기능 | 하는 일 |
|---|---|
| 초대·참여 | 카톡 링크 하나. 친구는 이름만 쓰고 바로(손님), 카카오로 이으면 다른 기기에서도 나로. 다음 날 같은 휴대폰은 저절로 로그인 |
| 날짜 맞추기 | 각자 되는 날을 누르면 겹친 날과 2박·1박·당일 후보 셋. 방장이 한 번에 정한다. 안 누른 사람은 누구나 "콕 찌르기" |
| 설문 → 역할 추천 | 1분 설문으로 운전·총무·장보기를 정하고, 카드마다 근거("차 있음 +4")를 보여 준다. 손으로 옮긴 카드가 없으면 늦게 온 사람도 저절로 다시 짠다 |
| 실시간 역할 보드 | 카드를 옮기면 같은 방 사람들 화면에도 바로 바뀐다. 둘이 동시에 옮기면 늦은 쪽은 409. 결과는 여행지 사진·역할 사진이 든 그림 카드로 단톡방에 |
| 방 규칙 | 운전자 기름값, 전날 불참 예약금, 술값. 정산 계산에 그대로 들어간다 |
| 회비 | 1인 얼마를 총무에게 미리 걷고 "냈어요" 한 번. 다녀오면 남은 회비를 돌려주거나 모자란 만큼 더 걷는 줄이 정산에 저절로 나온다 |
| 숙소 투표 | 링크나 지도 앱 공유 글을 통째로 붙이면 제목·사진·최저가·편의시설을 채우고 1인 얼마를 보인다. 조건별 **최솟값**으로 "의견 갈림"을 드러낸다 |
| 교통비 | 카카오 길찾기로 기름값·톨비, 규칙대로 운전자를 빼고 나눈다 |
| 장보기 | 인원·술 마시는 사람·박 수로 양을 계산, "내가 살게"와 쓴 돈. 공용 준비물은 "내가 챙길게" |
| 모이는 곳·방 뽑기 | 모일 시간·장소는 하루 전부터 방 머리에. 방·침대는 서버가 섞어 공평하게 |
| 차 나누기 | 운전하는 사람마다 차 하나, 안 고르면 저절로 나뉜다. 차마다 태우는 곳·시간, 기차로 따로 가면 "따로 가요". 기름값은 그 차에 탄 사람끼리 |
| 영수증·정산 | 1인 얼마 영수증 그림, 다녀와서 누가 누구에게 얼마(나눌 사람 고르기, 보냈어요·받았어요). 정산 글 끝 링크는 로그인 없이 금액만 보는 화면(`/s/코드`) |
| 2탄 방 | 같은 사람·같은 규칙으로 다음 여행 방을 한 번에 |
| 소개 페이지 | 첫 화면 휴대폰은 겨울 펜션 계획으로 시끄러운 단톡방, 바로 아래 실제 앱을 24초 동안 눌러 가는 영상, 실제 화면 열 장(스크롤하면 바뀜), 카톡 투표 VS TripMate. 첫 문구는 새로고침마다 바뀐다. PC·휴대폰 웹앱(홈 화면에 추가) |

카톡은 투표와 1/N 을 한다. TripMate 는 왜 그 사람이 그 역할인지, 우리 규칙대로 누가 누구에게 얼마인지를 보여 준다.

## 바로 실행

JDK 17 이상만 있으면 된다. DB 는 메모리 H2 라 설치할 것이 없다.

1. IntelliJ 로 저장소 폴더를 열고 Gradle 을 불러온다
2. `secrets.properties` 에 카카오 REST 키를 넣는다(아래 카카오 절). 개발용 로그인은 2026-11-20 에 지웠다. 서비스와 같은 카카오 로그인 하나로 들어간다
3. 오른쪽 위 실행 메뉴 **`tripmate`** ▶ 뒤 http://localhost:8080 에서 방을 만든다

```bash
./gradlew bootRun
```

서버를 다시 켜면 데이터는 처음으로 돌아간다. 로그인 없이 화면만 보려면 http://localhost:8080/demo (체험 방). 실시간은 카카오 계정 둘로 창을 두 개 띄우면 보인다.

## API

데이터는 모두 `/api/**` 에서 JSON 으로 주고받는다. 화면(Thymeleaf)의 jQuery `$.ajax` 가 부른다.
모든 방 API 는 로그인 안 했으면 **401**, 그 방 참여자가 아니면 **404**(방이 있는지도 알려 주지 않는다).

| 기능 | HTTP 메소드 | 경로(URI) | 응답 | 실패조건 | 담당 |
|---|---|---|---|---|---|
| 로그인 버튼 고르기 | GET | `/api/auth/options` | 200 `{"kakao": true}` | | 박재현 |
| 내 정보 | GET | `/api/me` | 200 `{"id": 2, "nickname": "민지", "guest": false}` | 로그인 안 했으면 401 | 박재현 |
| 로그아웃 | POST | `/api/logout` | 204 | | 박재현 |
| 여행방 만들기 | POST | `/api/trips` | 201 방 JSON(만든 사람이 방장)<br>요청 `{"title": "겨울 펜션", "destination": "가평", "startDate": "2027-01-15", "endDate": "2027-01-17"}` | 여행 이름이 비면 400, 손님이면 403 | 박재현 |
| 여행방 보기 | GET | `/api/trips/{id}` | 200 `{"id": 1, "title": "...", "inviteCode": "...", "meId": 2, "members": [...]}` | | 박재현 |
| 방 지우기 | DELETE | `/api/trips/{id}` | 204 (설문·역할·숙소·장보기·정산이 같이 지워지고 되돌릴 수 없다. 보던 사람은 SSE `deleted` 로 첫 화면으로) | 방장이 아니면 403, 방이 없으면 404 | 문지훈 |
| 손님으로 참여(로그인 없이) | POST | `/api/invites/{inviteCode}/guest` | 200 `{"tripId": 1}`, 손님 계정이 생기고 로그인·기억 쿠키(`tm_remember`, 60일)<br>요청 `{"name": "민재"}` | 이름 비거나 20자 넘음 400, 코드 틀림 404, form 전송 415, 한 방 손님 30명·같은 곳 한 시간 10번 넘음 429 | 박재현(설계 docs/19) |
| 초대 링크로 참여 | POST | `/api/invites/{inviteCode}` | 200 `{"tripId": 1}` (이미 있으면 그대로) | 코드가 틀리면 404 | 박재현 |
| 바뀜 알림 (SSE) | GET | `/api/trips/{id}/events` | `event: change` `data: roles` \| `members` \| `stays` \| `dates` \| `trip` \| `shopping` \| `settle` \| `deleted`, 15초마다 `ping` | | 박재현 |
| 설문 저장 | PUT | `/api/trips/{id}/members/{memberId}/survey` | 200 저장한 설문<br>요청 `{"activity": "INDOOR", "planning": "PLANNER", "hasCar": true, ..., "preferences": {"DETACHED": 5}}` | activity·planning 이 없거나 점수가 1~5 밖이면 400<br>남의 설문이면 403 | 문지훈 |
| 설문 보기 | GET | `/api/trips/{id}/members/{memberId}/survey` | 200 설문(본인·방장이 아니면 `mobilityLimited` 칸이 빠진다) | 설문 전이면 404 | 문지훈 |
| 역할 보드 | GET | `/api/trips/{id}/roles` | 200 `{"roles": [...], "assignments": [{"roleId": 4, "memberId": 2, "reasons": ["차 있음 +4"], "version": 0}]}` | | 문지훈 |
| 역할 추천 | POST | `/api/trips/{id}/roles/recommend` | 200 새 보드(손으로 옮긴 것도 다시 짠다) | 방장이 아니면 403 | 문지훈 |
| 이동·회비 근거 | GET | `/api/trips/{id}/roles/travel` | 200 `{"legs": [{"name": "재현", "driver": true, "km": 62, "minutes": 70, "toll": 3200, "fuel": 8800}], "perPerson": 6000, ...}` | 키가 없으면 200 + `message` 안내 | 문지훈 |
| 역할 더하기 | POST | `/api/trips/{id}/roles` | 201 보드<br>요청 `{"title": "설거지", "required": false, "capacity": 2, "tasks": ["저녁 설거지"]}` | 방장이 아니면 403, 역할이 12개 넘거나 이름이 비거나 정원이 1~8 밖이면 400 | 문지훈 |
| 역할 고치기 | PATCH | `/api/trips/{id}/roles/{roleId}` | 200 보드<br>요청은 더하기와 같다(칸을 다 보낸다) | 방장이 아니면 403, 검사에 걸리면 400, 역할이 없으면 404 | 문지훈 |
| 역할 지우기 | DELETE | `/api/trips/{id}/roles/{roleId}` | 204 (그 역할에 있던 사람은 미배정으로) | 방장이 아니면 403, 역할이 없으면 404 | 문지훈 |
| 역할 카드 옮기기 | PATCH | `/api/trips/{id}/roles/assignments/{memberId}` | 200 보드<br>요청 `{"roleId": 4, "version": 0}` (roleId null = 미배정) | 그 사이 누가 옮겼으면(version 다름) 409 | 문지훈 |
| 숙소 목록·그룹 취향 | GET | `/api/trips/{id}/stays` | 200 `{"amenities": [...], "preferences": {"DETACHED": {"avg": 4.5, "min": 3}}, "surveyed": 3, "stays": [...]}` | | 김현수 |
| 숙소 링크 미리보기 | POST | `/api/trips/{id}/stays/preview` | 200 `{"title": "가평 무지개 독채펜션", "imageUrl": "https://...", "price": 238640, "amenities": ["DETACHED"]}`<br>요청 `{"url": "https://..."}` 또는 지도 앱 공유 글 통째(`"[카카오맵] 가평 숲속펜션 ... https://kko.to/x"`), 이름은 공유 글에서 꺼낸다 | 글 안에 `http(s)://` 링크가 하나도 없으면 400 | 김현수 |
| 숙소 올리기 | POST | `/api/trips/{id}/stays` | 201 숙소<br>요청 `{"url": "https://...", "title": "숲속 독채", "price": 240000, "amenities": ["DETACHED"]}` | 링크나 공유 글 안에 `http(s)://` 링크가 없거나 가격이 음수면 400, 숙소가 30개 넘으면 400 | 김현수 |
| 숙소 투표 | PUT | `/api/trips/{id}/votes/{memberId}` | 204<br>요청 `{"stayId": 3}` (null = 취소) | 남의 표면 403, 숙소가 없으면 404 | 김현수 |
| 내 여행방 | GET | `/api/me/trips` | 200 `[{"id": 1, "title": "...", "memberCount": 8, "surveyDone": 5, "host": true, "todo": ["survey"]}]`<br>todo = 내가 안 한 일(invite·survey·dates, 급한 순) | 로그인 안 했으면 401 | 박재현 |
| 날짜 맞추기 판 | GET | `/api/trips/{id}/dates` | 200 `{"memberCount": 8, "responded": 5, "available": {"2027-01-15": [1, 2]}}` | | 문지훈 |
| 내 되는 날 | PUT | `/api/trips/{id}/members/{memberId}/dates` | 200 판<br>요청 `{"dates": ["2027-01-15", "2027-01-16"]}` | 남의 것 403, 지난 날·1년 넘는 날 400 | 문지훈 |
| 날짜 정하기 | PUT | `/api/trips/{id}/period` | 200 방<br>요청 `{"startDate": "2027-01-15", "endDate": "2027-01-17"}` | 방장이 아니면 403, 도착이 출발보다 앞이면 400 | 문지훈 |
| 방 규칙 | PUT | `/api/trips/{id}/rules` | 200 방<br>요청 `{"driverPaysFuel": false, "refundNoShow": false, "drinksSeparate": true}` | 방장이 아니면 403 | 문지훈 |
| 방 이름·지역 고치기 | PUT | `/api/trips/{id}` | 200 방<br>요청 `{"title": "강릉 우정 여행", "destination": "강릉"}` | 방장이 아니면 403, 이름이 비면 400 | 문지훈 |
| 방장 넘기기 | PUT | `/api/trips/{id}/host` | 200 방<br>요청 `{"memberId": 3}` | 방장이 아니면 403, 자기·손님에게 400 | 문지훈 |
| 방·침대 뽑기 | POST | `/api/trips/{id}/room-draw` | 200 방(`roomDraw: {"rooms": [["재현","민지"], ...], "count": 2, "by": "민지"}`)<br>요청 `{"rooms": 2}` | 방 2~6개가 아니면 400 | 문지훈 |
| 차 나누기 | GET / PUT | `/api/trips/{id}/cars`, `/cars/rides/{memberId}`, `/cars/{driverId}/meet` | 200 `{"cars": [{"driverName": "재현", "meetTime": "08:30", "meetPlace": "합정역", "riderIds": [3, 4]}], "separateIds": [8]}`<br>요청 `{"driverId": 1}`(null = 따로 가요), `{"time": "08:30", "place": "수원역"}` | 남의 자리는 방장만 403, 운전자가 다른 차·운전 안 하는 사람 차 400, 태우는 곳은 그 운전자·방장만 403 | 문지훈 |
| 모일 시간·장소 | PUT | `/api/trips/{id}/meet` | 200 방(`meet: {"time": "08:00", "place": "합정역 2번 출구"}`), 하루 전부터 방 머리에 "내일 08:00 · 합정역 2번 출구" | 방장이 아니면 403, 장소 40자 넘으면 400 | 문지훈 |
| 같은 멤버로 다음 여행 | POST | `/api/trips/{id}/next` | 201 `{"tripId": 7, "created": true, "memberCount": 8}` (이미 있으면 200 같은 방) | 방에 없으면 404, 손님이면 403, 동시에 겹치면 409 | 문지훈 |
| 나가기·빼기 | DELETE | `/api/trips/{id}/members/{memberId}` | 204 (설문·역할·숙소 표·장보기·정산이 같이 빠짐) | 남을 빼려는 참여자 403, 혼자 남은 방 400 | 문지훈 |
| 장보기 | GET / PUT / DELETE | `/api/trips/{id}/shopping`, `/shopping/plan`, `/shopping/items/{item}` | 200 `{"people": 8, "items": [...], "spent": 52000, "perPerson": 6500}` | 남이 맡은 줄 409, 범위 밖 400 | 문지훈 |
| 술 마셔요 표시 | PUT | `/api/trips/{id}/members/{memberId}/drinks` | 200 방<br>요청 `{"drinks": false}` (기본은 마심) | 남의 것을 방장이 아닌 사람이 바꾸면 403 | 문지훈 |
| 누가 누구에게 얼마 | GET | `/api/trips/{id}/settlement` | 200 `{"total": 560000, "transfers": [{"fromName": "도윤", "toName": "재현", "amount": 53200}]}` | | 문지훈 |
| 회비 정하기 | PUT | `/api/trips/{id}/dues` | 200 정산 판(`dues: {"amount": 100000, "collectorName": "도윤", "paidIds": [2], "collected": 100000, "pending": true}`)<br>요청 `{"amount": 100000, "collectorId": 3}` | 방장·지금 총무가 아니면 403, 1천 원~1천만 원 밖·방에 없는 사람 400 | 문지훈 |
| 회비 냈어요·되돌리기 | PUT / DELETE | `/api/trips/{id}/dues/paid/{memberId}` | 200 정산 판(몇 번 눌러도 한 번) | 본인·총무·방장이 아니면 403, 총무 본인 400, 회비를 안 정했으면 404 | 문지훈 |
| 정산 받는 링크 | PUT | `/api/trips/{id}/settlement/link` | 200 `{"code": "Z5jq4fKf-iABoZdPWWGhtA"}` (없으면 만들고 있으면 그대로). 화면은 `GET /s/{code}`, 로그인 없이 보낼 줄·낸 돈·끝난 줄만 | 방 사람이 아니면 404, 틀린 코드·지운 방 404 | 문지훈 |
| 보냈어요·받았어요 | POST / DELETE | `/api/trips/{id}/payments`, `/payments/{paymentId}` | 201 / 200 판(끝난 줄은 `payments`, 남은 줄만 `transfers`)<br>요청 `{"fromId": 2, "toId": 1, "amount": 20000}` | 보낸·받은 사람·방장이 아니면 403, 같은 사람·방에 없는 사람 400 | 문지훈 |
| 먼저 낸 돈 적기·지우기 | POST / DELETE | `/api/trips/{id}/expenses`, `/expenses/{expenseId}` | 201 / 200 판<br>요청 `{"kind": "FUEL", "memo": "주유", "amount": 64000}` (kind: STAY·FUEL·FOOD·DRINK·OTHER, `sharerIds` 를 주면 그 사람들끼리만, 안 주면 모두· 규칙이 켜지면 FUEL 은 운전자 빼고, DRINK·장보기 술은 마신 사람끼리) | 금액 0 이하 400, 남이 적은 것 403 | 문지훈 |

화면 주소(`/trips/1/roles` 같은 것)는 [화면 설계](docs/02-화면설계.md)에 있다. API 를 직접 불러 보려면 브라우저에서 카카오로 로그인한 뒤 `JSESSIONID` 쿠키를 IntelliJ HTTP Client 나 Talend API Tester 에 붙인다. 테스트는 `TestLogin`(src/test)이 같은 세션을 바로 만든다.

## 구조

수업에서 배운 짜임 그대로다. 화면 컨트롤러는 템플릿만 돌려주고, 데이터는 jQuery 가 API 로 읽는다.

```
브라우저 ─GET /trips/1/roles──────▶ RolePageController ─▶ templates/role/roles.html
   │
   └ roles.js $.ajax ─GET /api/trips/1/roles─▶ RoleController ─▶ RoleService ─▶ RoleRepository ─▶ DB
                                                (권한: TripAccess)     (계산)        (JPA)
```

| 무엇 | 쓰는 것 |
|---|---|
| 서버 | Spring Boot 4 · Java 17 · Gradle |
| 화면 | Thymeleaf · HTML · CSS · jQuery |
| DB | Spring Data JPA · H2(로컬) / PostgreSQL(배포) |
| 로그인 | Spring Security + 카카오 OAuth2, 세션 쿠키 |
| 실시간 | SSE(`SseEmitter`) |

```
src/main/java/com/tripmate/
  user/      로그인·손님 참여·로그인 기억하기      박재현
  trip/      여행방·초대·날짜·규칙·2탄·뽑기·SSE  박재현
  survey/    설문                                  문지훈
  role/      역할 추천·보드·교통비                 문지훈
  stay/      숙소·투표                             김현수
  shopping/  장보기·챙겨 갈 것                     문지훈
  settle/    다녀와서 정산                         문지훈
src/main/resources/
  templates/   화면. 기능별 폴더 + fragments/common.html(공통 머리·탭)
  static/      js/(화면마다 하나 + common.js), css/style.css
docs/          설계 문서, 각자 발표 정리(parts/)
```

## 설계

요구사항 → 화면 밑그림 → ERD → URL → UML 순서로 정했다.

| 문서 | 들어 있는 것 |
|---|---|
| [1. 주제와 요구사항](docs/01-주제와-요구사항.md) | 주제를 고른 까닭, REQ-01~18, 담당 |
| [2. 화면 설계](docs/02-화면설계.md) | 화면 목록 SCR-01~11, **와이어프레임**, 화면 흐름 |
| [3. DB 설계](docs/03-DB설계.md) | ERD, 표마다 정한 것과 까닭, 정규화 확인 |
| [4. URL 설계](docs/04-URL설계.md) | API 표(코드 위치까지) |
| [5. 역할 분담과 일정](docs/05-역할분담.md) | 사람별 남은 일, 4주 일정, 발표 순서 |
| [6. UML](docs/06-UML.md) | **유스케이스, 클래스 다이어그램, 시퀀스 다이어그램** |
| [디자인 시스템](docs/DESIGN.md) | **화면을 만들 때 볼 것 하나**: 색·글자·간격·부품·움직임·금지·글쓰기, PR 전 확인 |
| [7. 비슷한 서비스와 공공데이터](docs/07-비슷한-서비스와-공공데이터.md) | 비슷한 앱 비교, 쓸 수 있는 공공 API, 휴대폰 화면 점검, 할 순서 |
| [8. 화면 개편](docs/08-화면-개편.md) | 참고한 페이지, 첫 화면 짜임과 까닭, 방 화면에서 고친 것 |
| [9. 더 쓸 데이터와 운영자 계정](docs/09-더-쓸-데이터와-운영자-계정.md) | 받아 둔 키로 더 할 것, 새로 받을 API, 개발자 문구 걷기, 카카오 운영자 계정 설계, 글꼴·크기 기준 |
| [10. 사진과 쓰고 싶게 만드는 기능](docs/10-사진과-쓰고-싶게-만드는-기능.md) | 사진·AI 이미지 자리와 프롬프트, 센스 있는 기능 후보와 담당 |
| [11. 커뮤니티 시장조사](docs/11-커뮤니티-시장조사.md) | 커뮤니티 불편 |
| [12. 한국 비슷한 서비스](docs/12-한국-비슷한-서비스.md) | 국내 비슷한 서비스 |
| [13. AI와 더 엮을 것](docs/13-AI와-더-엮을-것.md) | AI·연동 |
| [14. 실제 해결법과 시스템 아이디어](docs/14-실제-해결법과-시스템-아이디어.md) | 실제 해결법 |
| [15. 팀별 평가와 퍼지는 장치](docs/15-팀별-평가와-퍼지는-장치.md) | PM·디자인·홍보·MZ·개발 시선, SNS 퍼지는 고리 |
| [16. 지도·맛집·AI 확장과 재미](docs/16-지도-맛집-AI-확장과-재미.md) | 지도·장소 API 약관, 우리만 할 수 있는 지도 |
| [17. 7명 여행 시뮬레이션](docs/17-7명-여행-시뮬레이션과-소개-페이지.md) | 처음부터 끝까지 돌려 찾은 것, 커뮤니티 불편 22개 대조 |
| [18. 채택 시뮬레이션](docs/18-사람들이-정말-쓸까-채택-시뮬레이션.md) | 단계별 이탈 추정, 홍보팀 시선 |
| [19. 로그인 없이 참여 설계](docs/19-로그인-없이-참여-설계.md) | 손님 계정, 기억하기, 카카오로 옮기기, 보안 |
| [20. 현실 시나리오 점검](docs/20-현실-시나리오-점검.md) | 차 두 대·따로 가는 사람·서버 시간·같은 IP 손님, 손일 세기, 남은 것 |
| [21. 영상과 AI 자리](docs/21-영상과-AI-자리.md) | 실제 앱 영상 만드는 법, AI 영상·그림을 넣을 자리와 프롬프트, 지키는 것 |
| [역할 추천 알고리즘](docs/matching.md) | 점수 규칙, 헝가리안을 쓴 까닭 |
| [팀 코드 규칙](docs/코드-규칙.md) | 층 나누기, 주석, 주소 규칙 |

<details>
<summary>유스케이스 미리 보기</summary>

```mermaid
flowchart LR
    host([방장])
    member([참여자])
    subgraph TripMate
        create((여행방 만들기))
        recommend((역할 추천받기))
        join((초대 링크로 참여))
        survey((설문하기))
        move((역할 카드 옮기기))
        vote((숙소 올리기·투표))
    end
    host --- create
    host --- recommend
    member --- join
    member --- survey
    member --- move
    member --- vote
```

</details>

## 테스트

```bash
./gradlew test
```

API 와 화면 테스트 163개(3·7·8·15명 방을 처음부터 끝까지 도는 시나리오, 당일치기·4박·차 없음 같은 유연성, 손님 참여, 정산 셈, 방 지우기, 공유 글, 정산 받는 링크, 회비, 차 두 대 포함). PR 을 올리기 전에 통과해야 한다.

## 같이 작업하기

브랜치 → PR → 리뷰 1명 → 합치기. 자세한 순서는 [CONTRIBUTING.md](CONTRIBUTING.md). 각자 발표 정리는 [내 파트 틀](docs/parts/_틀.md)을 복사해 쓴다.

## 카카오 로그인 켜기 (키가 있는 사람만)

키가 없으면 로그인을 할 수 없다(개발용 로그인은 2026-11-20 에 지웠다). 시험은 키 없이 `TestLogin` 으로 돈다.

1. 저장소 맨 위 폴더에 `secrets.properties` 를 만든다. git 에 안 올라간다. 값은 팀장에게 따로 받는다

   ```properties
   KAKAO_CLIENT_ID=REST API 키
   KAKAO_CLIENT_SECRET=클라이언트 시크릿
   ```

2. 서버를 다시 켜면 로그인 화면에 카카오 버튼이 나온다

역할 보드의 이동·회비 근거도 같은 `KAKAO_CLIENT_ID`(REST API 키)로 카카오 지도·길찾기를 부른다. 콘솔에서 카카오맵을 켜야 한다.
기름값은 오피넷 키가 있으면 그날 전국 평균, 없으면 어림값(1,700원/L)이다.

```properties
OPINET_KEY=오피넷 오픈 API 키
```

카카오 개발자 콘솔의 Redirect URI 는 `http://localhost:8080/login/oauth2/code/kakao` 다.
배포할 때는 `{배포 주소}/login/oauth2/code/kakao` 를 하나 더 넣는다.
