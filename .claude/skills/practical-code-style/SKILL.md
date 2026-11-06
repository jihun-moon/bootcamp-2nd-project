---
name: practical-code-style
description: 부트캠프 2기 팀 프로젝트 코드를 짜거나 고칠 때 사용. 수업(Spring 노션)에서 배운 수준 그대로, 사람이 짠 것처럼 짧고 읽히게. "코드 짜줘", "기능 만들어줘", "코드 정리", "교수님 스타일로", "리뷰해줘", "주제 정하자", "설계하자", "발표 준비"가 오면 이 규칙대로.
---

# 우리 팀 코드 규칙

**수업에서 배운 것만 쓴다.** 안 배운 기술(Spring Security, JPA 고급 기능, 캐시, 메시지 큐 등)은 넣지 않는다.
발표 때 팀원 모두가 설명할 수 있어야 하기 때문이다. 꼭 필요하면 이슈로 먼저 묻는다.

## 1. 스택 (수업 기준)

| 무엇 | 쓰는 것 |
|---|---|
| 백엔드 | Spring Boot 4, Java 17, Gradle |
| DB | MySQL 8 |
| 화면 | Thymeleaf, HTML5, JavaScript, jQuery |
| 도구 | IntelliJ, Talend API Tester(크롬 확장), ERD 는 Lucidchart 또는 ERDCloud |

## 2. 폴더와 층

```
src/main/java/com/bootcamp/<프로젝트>/
  controller/   화면 컨트롤러(@Controller), API 컨트롤러(@RestController)
  service/      업무 규칙
  repository/   DB 접근
  dto/          record 그릇, 폼 객체
src/main/resources/
  templates/    <기능>/list.html, form.html, detail.html, fragments/header.html
  static/       css/, js/
```

- 요청 흐름: 브라우저 → Controller → Service → Repository → DB. 발표 때 이 순서로 설명한다
- Controller 에 계산이나 SQL 을 쓰지 않는다. 한 층은 한 가지 일
- 의존성은 생성자로 받는다: `private final MemberService service;` + 생성자

## 3. 코드 꼴

```java
// GET /members/{id}  예) /members/3
@GetMapping("/{id}")
public String detail(@PathVariable Long id, Model model) {
    var member = service.findById(id);
    if (member == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    model.addAttribute("member", member);
    return "members/detail";
}
```

- 메서드 위에 **부르는 예 한 줄**(`// GET /members/{id}  예) /members/3`)
- 주석은 **왜**만 쓴다. 코드가 이미 말하는 것(`// 회원을 찾는다`)은 쓰지 않는다
- 칸만 있는 그릇은 `record`. getter/setter 를 손으로 쓰지 않는다 (Thymeleaf 폼 객체는 수업처럼 getter/setter 클래스 가능)
- JSON 키가 `car_list` 처럼 다르면 `@JsonProperty("car_list")`
- 타입이 오른쪽에 보이면 `var`
- 기본값은 쓰는 자리 가까이 한 줄: `var sex = (gender == null) ? Gender.MALE : gender;`
- 숫자를 박지 않는다: `private static final int FREE_DELIVERY_MIN = 50_000;`
- 이름은 한 뜻에 한 이름. `data`, `info`, `payload` 를 섞지 않는다
- `System.out.println` 은 확인용. PR 올리기 전에 지운다

## 4. 주소(URI) 규칙

| 하지 말 것 | 이렇게 |
|---|---|
| `GET /members/delete/1` | `DELETE /members/1` (API) |
| `/Members`, `/user_groups` | 소문자, 하이픈: `/members`, `/user-groups` |
| `/member` 목록 | 복수 명사: `/members` |
| `/members?id=1` 로 한 명 | 식별은 경로 `/members/1`, 검색·페이지는 쿼리 `/members?name=kim&page=2` |

화면(Thymeleaf) 폼은 GET/POST 만 되므로 수정·삭제는 이렇게 한다:

| 기능 | 메서드 | 주소 |
|---|---|---|
| 목록 | GET | `/members` |
| 등록 폼 / 등록 | GET / POST | `/members/new` / `/members` |
| 상세 | GET | `/members/{id}` |
| 수정 폼 / 수정 | GET / POST | `/members/{id}/edit` |
| 삭제 | POST | `/members/{id}/delete` |

## 5. 응답과 화면

- API 응답: `ResponseEntity.status(HttpStatus.CREATED).body(dto)`. 조회 200, 생성 201, 삭제 204, 없음 404
- 폼: `th:object` + `th:field`, 검증은 `@Valid` 바로 뒤에 `BindingResult`
- 저장 뒤에는 `return "redirect:/members";` (새로고침 중복 저장 방지)
- 공통 머리·메뉴는 `fragments/header.html` 의 `th:fragment` 로 한 번만
- 사용자가 넣은 값은 `th:text` 로만 출력. `th:utext` 는 쓰지 않는다(XSS)

## 6. DB

- 표 이름·칸 이름은 snake_case 복수(`members`, `created_at`), 자바는 camelCase
- 모든 표에 `id BIGINT AUTO_INCREMENT PRIMARY KEY`, 관계는 외래 키로
- 설계 순서: 요구사항 → 화면 목록 → ERD → 정규화(1·2·3정규형 확인) → `schema.sql`
- 반복문 안에서 쿼리를 부르지 않는다. 한 번에 가져온다
- 비밀번호·DB 접속 정보는 `application-local.yml`(gitignore)에. 저장소에 올리지 않는다

## 7. 주제 정할 때

평가 기준(수업 과제 기준): 화면 출력 20 · CRUD 40 · 검증 20 · 코드 구조/재사용 20.

- 표 3~5개, 서로 관계가 있는 것(1:N 하나 이상). DB 수업에서 배운 ERD·정규화를 보여줄 수 있게
- 3명이 **기능 하나씩** 화면부터 DB 까지 맡을 수 있게 나뉘는 주제
- 4주 안에 CRUD 를 다 끝낼 수 있는 크기. 로그인은 세션 정도까지만
- `docs/01-주제와-요구사항.md` 의 후보 표에 점수를 매겨 고른다

## 8. 발표 준비

- 각자 `docs/parts/<GitHub아이디>.md` 를 자기 기능 기준으로 채운다(틀은 `docs/parts/_틀.md`)
- 자기 기능의 요청 하나를 골라 브라우저 → Controller → Service → Repository → DB → 화면 순서로 말할 수 있어야 한다
- 코드 위치는 파일 경로와 메서드 이름으로 적는다

## AI 티 나는 것 (지운다)

- 모든 메서드에 붙는 긴 설명 주석, 코드를 되풀이하는 주석
- 한 번만 부르는 작은 메서드 여러 겹, 쓰지 않는 인자, "나중에 쓸지도" 만든 클래스
- 모든 줄을 감싼 try-catch, 로그만 찍고 삼키는 예외
- 영어·한국어 주석 섞기. 주석은 짧은 한국어
