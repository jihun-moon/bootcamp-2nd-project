# 4. URL 설계

규칙: 주소는 명사(복수, 소문자, 하이픈), 행위는 HTTP 메서드. 한 개는 경로(`/posts/3`), 검색·페이지는 쿼리(`/posts?keyword=java&page=2`).

## 화면 주소 (Thymeleaf, @Controller)

HTML form 은 GET/POST 만 되므로 수정·삭제도 POST 로 한다.

| 요구사항 | 기능 | 메서드 | 주소 | 템플릿 / 이동 | 담당 |
|---|---|---|---|---|---|
| | 목록 | GET | `/posts` | `posts/list` | |
| | 등록 폼 | GET | `/posts/new` | `posts/form` | |
| | 등록 | POST | `/posts` | `redirect:/posts` | |
| | 상세 | GET | `/posts/{id}` | `posts/detail` | |
| | 수정 폼 | GET | `/posts/{id}/edit` | `posts/edit` | |
| | 수정 | POST | `/posts/{id}/edit` | `redirect:/posts/{id}` | |
| | 삭제 | POST | `/posts/{id}/delete` | `redirect:/posts` | |

## API 주소 (jQuery ajax 로 부를 때만, @RestController)

| 요구사항 | 기능 | 메서드 | 주소 | 요청 본문 | 응답 | 담당 |
|---|---|---|---|---|---|---|
| | 댓글 목록 | GET | `/api/posts/{id}/comments` | 없음 | 200, 목록 | |
| | 댓글 쓰기 | POST | `/api/posts/{id}/comments` | `{"content": "..."}` | 201 | |
| | 댓글 삭제 | DELETE | `/api/comments/{id}` | 없음 | 204 | |

API 는 Talend API Tester 로 먼저 시험한 뒤 화면에 붙인다.

`posts`, `comments` 는 예시다. 주제가 정해지면 바꾼다.
