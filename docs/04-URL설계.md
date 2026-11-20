# 4. URL 설계

규칙: 주소는 명사(복수, 소문자, 하이픈), 행위는 HTTP 메서드. 한 개는 경로(`/trips/3`), 검색·페이지는 쿼리.

- 화면 주소(Thymeleaf, `@Controller`)는 [2. 화면 설계](02-화면설계.md)에 있다
- 데이터는 모두 `/api/**`(JSON, `@RestController`)로 주고받는다. 화면의 jQuery `$.ajax` 가 부른다
- 로그인 안 했으면 401, 그 방 참여자가 아니면 404(방이 있는지도 알려 주지 않는다), 본인·방장만 되는 일은 403
- 각 Controller 메서드 위에 부르는 예 한 줄이 있다(`// PUT: /api/trips/1/members/2/survey`)

## 로그인·초대·여행방 (박재현)

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 코드 |
|---|---|---|---|---|---|---|
| REQ-01 | 카카오 로그인 시작 | GET | `/oauth2/authorization/kakao` | | 302 → 카카오 | Spring Security |
| REQ-01 | 카카오가 돌려보내는 곳 | GET | `/login/oauth2/code/kakao` | | 302 → `/login/done` | Spring Security, `KakaoUserService` |
| REQ-01 | 내 정보 | GET | `/api/me` | | 200 `{id, nickname, guest}` / 401 | `AuthController` |
| REQ-01 | 로그인 버튼 고르기 | GET | `/api/auth/options` | | 200 `{kakao}` | `AuthController` |
| REQ-01 | 로그아웃 | POST | `/api/logout` | | 204 | Spring Security |
| REQ-02 | 방 만들기 | POST | `/api/trips` | `{"title", "destination", "startDate", "endDate"}` | 201 방 / 빈 이름 400 / 손님 403 | `TripController`, `TripService` |
| REQ-04 | 방 보기 | GET | `/api/trips/{id}` | | 200 방 `{id, title, destination, startDate, endDate, inviteCode, meId, members, rules, nextTripId, meet, roomDraw}` | `TripController`, `TripService.find`, `TripDto.Response` |
| REQ-03 | 손님으로 참여(로그인 없이) | POST | `/api/invites/{inviteCode}/guest` | `{"name": "민재"}` | 200 `{tripId}` + 로그인·기억 쿠키 / 400 / 404 / 415 / 429 | `TripController.joinAsGuest`, `GuestService`, docs/19 |
| REQ-03 | 초대 링크로 참여 | POST | `/api/invites/{inviteCode}` | | 200 `{tripId}` | `TripController`, `TripService` |
| REQ-02 | 내 여행방 | GET | `/api/me/trips` | | 200 `[{id, title, destination, startDate, memberCount, surveyDone, host, todo}]` 최근 50개 / 401 | `TripController`, `TripService.myTrips` |
| REQ-11 | 바뀜 알림(SSE) | GET | `/api/trips/{id}/events` | | `event: change`(data: `roles`·`members`·`stays`·`dates`·`trip`·`shopping`·`settle`·`deleted`) / 15초마다 `ping` | `TripController`, `TripEvents`, `TripChanged` |
| REQ-05 | 방 지우기(방장) | DELETE | `/api/trips/{id}` | | 204 / 방장 아님 403 / 방 없음 404 | `TripController.delete`, `TripService.delete`, `TripDeleting` 이벤트(설문·역할·숙소·장보기·정산이 각자 지움), 남은 화면엔 SSE `deleted` |
| REQ-06 | 나가기(본인)·빼기(방장) | DELETE | `/api/trips/{id}/members/{memberId}` | | 204 / 남을 빼려는 참여자 403 / 혼자 남은 방 400 | `TripController`, `TripService.leave`, `MemberLeaving` 이벤트(설문·역할·숙소 표·장보기·정산이 각자 지움) |
| | 날짜 맞추기 판 | GET | `/api/trips/{id}/dates` | | 200 `{memberCount, responded, available: {"2027-01-15": [1, 2]}}` | `TripController`, `TripService.dates` |
| | 내 되는 날 | PUT | `/api/trips/{id}/members/{memberId}/dates` | `{"dates": ["2027-01-15"]}` | 200 판 / 남의 것 403 / 지난 날·1년 넘음 400 | `TripService.saveDates` |
| | 날짜 정하기(방장) | PUT | `/api/trips/{id}/period` | `{"startDate", "endDate"}` | 200 방 / 403 / 도착이 출발보다 앞 400 | `TripService.setPeriod` |
| | 방 규칙(방장) | PUT | `/api/trips/{id}/rules` | `{"driverPaysFuel", "refundNoShow", "drinksSeparate"}` | 200 방 / 403 | `TripService.updateRules` |
| REQ-05 | 방 이름·지역 고치기(방장) | PUT | `/api/trips/{id}` | `{"title", "destination"}` | 200 방 / 403 / 빈 이름 400 | `TripService.rename` |
| | 방장 넘기기(방장) | PUT | `/api/trips/{id}/host` | `{"memberId": 3}` | 200 방 / 403 / 자기·손님에게 400 | `TripService.handOver` |
| | 방·침대 뽑기 | POST | `/api/trips/{id}/room-draw` | `{"rooms": 2}` | 200 방(roomDraw) / 2~6 밖 400 | `TripService.drawRooms`(SecureRandom) |
| | 모일 시간·장소(방장) | PUT | `/api/trips/{id}/meet` | `{"time": "08:00", "place": "합정역 2번 출구"}` | 200 방 / 403 / 40자 넘음 400 | `TripService.updateMeet` |
| | 같은 멤버로 다음 여행 | POST | `/api/trips/{id}/next` | | 201 `{tripId, created, memberCount}` / 이미 있으면 200 같은 방 / 손님 403 / 동시에 겹치면 409 | `TripService.next`, `Trip.previousTripId`(unique) |

## 설문·역할 (문지훈)

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 코드 |
|---|---|---|---|---|---|---|
| REQ-07 | 설문 저장(처음·다시) | PUT | `/api/trips/{id}/members/{memberId}/survey` | `{"activity", "planning", "hasCar", "separateDeparture", "mobilityLimited", "avoid": [], "preferences": {}}` | 200 | `SurveyController`, `SurveyService` |
| REQ-08 | 설문 보기 | GET | `/api/trips/{id}/members/{memberId}/survey` | | 200 (본인·방장 외에는 `mobilityLimited` 없음) | `SurveyController`, `SurveyService` |
| REQ-09 | 역할 보드 | GET | `/api/trips/{id}/roles` | | 200 `{roles, assignments}` | `RoleController`, `RoleService` |
| REQ-09 | 역할 추천(방장) | POST | `/api/trips/{id}/roles/recommend` | | 200 보드 | `RoleController`, `RoleService.recommend`, `RoleScorer`, `HungarianAssigner` |
| REQ-10 | 카드 옮기기 | PATCH | `/api/trips/{id}/roles/assignments/{memberId}` | `{"roleId": 4, "version": 0}` | 200 보드 / 409 | `RoleController`, `RoleService.move` |
| REQ-18 | 이동·회비 근거 | GET | `/api/trips/{id}/roles/travel` | | 200 `{message, legs, perPerson, fuelPrice, fuelSource, kmPerLiter, tips}` | `RoleController`, `RoleService.travelPlan`, `TravelService`, `KakaoMapClient`, `FuelPriceClient` |
| REQ-12 | 역할 더하기·고치기·지우기(방장) | POST / PATCH / DELETE | `/api/trips/{id}/roles`, `/api/trips/{id}/roles/{roleId}` | `{"title": "설거지", "required": false, "capacity": 2, "tasks": ["저녁 설거지"]}` | 201 보드 / 200 보드 / 204, 400(검사·12개 넘음), 403 | `RoleController`, `RoleService.addRole·updateRole·deleteRole` |

## 숙소·투표 (김현수)

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 코드 |
|---|---|---|---|---|---|---|
| REQ-15 | 숙소 목록·그룹 취향 | GET | `/api/trips/{id}/stays` | | 200 `{amenities, preferences, surveyed, stays}` | `StayController` |
| REQ-13 | 숙소 올리기 | POST | `/api/trips/{id}/stays` | `{"url", "title", "price", "amenities": []}` (url 에 지도 앱 공유 글 통째도 된다) | 201 / 링크가 하나도 없음·가격 음수·숙소 30개 넘음 400 | `StayController`, `ShareText`, `LinkPreview` |
| REQ-14 | 투표·바꾸기·취소 | PUT | `/api/trips/{id}/votes/{memberId}` | `{"stayId": 3}` (null 이면 취소) | 204 | `StayController` |
| REQ-16 | 숙소 고치기·지우기 *(남은 일)* | PATCH / DELETE | `/api/trips/{id}/stays/{stayId}` | 조건 | 200 / 204 | |
| | 숙소 링크 미리보기 | POST | `/api/trips/{id}/stays/preview` | `{"url"}` (링크나 공유 글 통째) | 200 `{title, imageUrl, price, amenities}` / 링크가 하나도 없음 400 | `StayController`, `ShareText`(첫 링크·이름 줄), `LinkPreview` |

### 장보기·정산

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 코드 |
|---|---|---|---|---|---|---|
| | 장보기 목록 | GET | `/api/trips/{id}/shopping` | | 200 `{people, nights, drinkers, meatGrams, items, spent, perPerson}` | `ShoppingController`, `ShoppingService` |
| | 양 정하기(방장) | PUT | `/api/trips/{id}/shopping/plan` | `{"drinkers": 6, "meatGrams": 300}` | 200 / 403 / 범위 밖 400 | `ShoppingService.savePlan` |
| | 내가 살게·쓴 돈 | PUT | `/api/trips/{id}/shopping/items/{item}` | `{"amount": 12000}` (null = 아직) | 200 / 남이 맡은 줄 409 | `ShoppingService.claim` |
| | 내려놓기 | DELETE | `/api/trips/{id}/shopping/items/{item}` | | 200 / 맡은 사람·방장 아니면 403 | `ShoppingService.release` |
| | 술 마셔요 표시 | PUT | `/api/trips/{id}/members/{memberId}/drinks` | `{"drinks": false}` | 200 방 / 남의 것은 방장만(아니면 403) | `TripService.setDrinks`, `Member.drinks` |
| | 정산 판(누가 누구에게 얼마) | GET | `/api/trips/{id}/settlement` | | 200 `{total, lines, balances, transfers: [{fromName, toName, amount}], payments, driverPaysFuel, drinksSeparate, refundNoShow}` (transfers 는 남은 줄, payments 는 끝난 줄) | `SettleController`, `SettleService` |
| | 먼저 낸 돈 적기 | POST | `/api/trips/{id}/expenses` | `{"kind": "STAY", "memo": "잔금", "amount": 476000, "sharerIds": [1, 2]}` (sharerIds 없으면 모두·규칙대로) | 201 판 / 금액 0 이하·종류 없음·방에 없는 사람 400 | `SettleService.add` |
| | 보냈어요·받았어요 | POST | `/api/trips/{id}/payments` | `{"fromId": 2, "toId": 1, "amount": 20000}` | 201 판 / 보낸·받은 사람·방장 아니면 403 / 같은 사람·방에 없는 사람·금액 0 이하 400 | `SettleService.markPaid`, `Payment` |
| | 보냈어요 되돌리기 | DELETE | `/api/trips/{id}/payments/{paymentId}` | | 200 판 / 누른 사람·보낸·받은 사람·방장 아니면 403 | `SettleService.unmarkPaid` |
| | 회비 정하기 | PUT | `/api/trips/{id}/dues` | `{"amount": 100000, "collectorId": 3}` | 200 판(`dues`) / 방장·지금 총무 아니면 403 / 금액 범위 밖·방에 없는 사람 400 | `SettleService.setDues`, `Dues` |
| | 회비 냈어요·되돌리기 | PUT / DELETE | `/api/trips/{id}/dues/paid/{memberId}` | | 200 판 / 본인·총무·방장 아니면 403 / 총무 본인 400 / 회비 없음 404 | `SettleService.markDues`·`unmarkDues`, `Payment.dues` |
| | 정산 받는 링크 코드 | PUT | `/api/trips/{id}/settlement/link` | | 200 `{"code"}`(몇 번 불러도 같은 코드) / 방 사람 아니면 404 / 둘이 동시에 처음 만들면 409 | `SettleService.linkCode`, `SettleLink` |
| | 정산 받는 링크 화면 | GET | `/s/{code}` | | 200 HTML(로그인 없이, `Cache-Control: no-store`, noindex) / 틀린 코드·지운 방 404 | `SettlePageController`, `SettleService.shared` |
| | 적은 돈 지우기 | DELETE | `/api/trips/{id}/expenses/{expenseId}` | | 200 판 / 낸 사람·방장 아니면 403 | `SettleService.remove` |

API 는 IntelliJ HTTP Client 나 Talend API Tester 로 시험할 수 있다. 로그인이 필요하니 브라우저에서 카카오로 로그인한 뒤 `JSESSIONID` 쿠키를 붙인다.
