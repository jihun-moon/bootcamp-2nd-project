# 같이 작업하는 법

`main` 에는 바로 올릴 수 없습니다. 항상 브랜치 → PR → 리뷰 1명 → 합치기.

## 처음 한 번

```bash
git clone https://github.com/jihun-moon/bootcamp-2nd-project.git
```

초대 메일(또는 https://github.com/jihun-moon/bootcamp-2nd-project/invitations)에서 수락해야 올릴 수 있습니다.

## 일 하나 하는 순서

1. **이슈 만들기**: Issues → New issue → 틀 고르기. 자기를 Assignee 로
2. **최신 받기**
   ```bash
   git switch main
   git pull
   ```
3. **브랜치 만들기**: `종류/이슈번호-짧은설명`
   ```bash
   git switch -c feat/12-trip-edit
   ```
   종류: `feat`(기능) · `fix`(버그) · `docs`(문서) · `design`(화면·CSS) · `chore`(설정)
4. **작업하고 커밋**
   ```bash
   git add .
   git commit -m "feat: 방 정보 고치기 추가 (#12)"
   ```
5. **올리고 PR**
   ```bash
   git push -u origin feat/12-trip-edit
   ```
   GitHub 에 뜨는 "Compare & pull request" → 틀 채우기 → Reviewers 에 팀원 1명
6. **리뷰 받고 합치기**: 승인 1개 받으면 "Squash and merge". 브랜치는 자동으로 지워진다

## 커밋 메시지

`종류: 무엇을 했는지 (#이슈번호)` 한 줄, 한국어.

```
feat: 숙소 지우기 추가 (#3)
fix: 설문 점수를 빠르게 누르면 앞 값이 사라지던 문제 (#15)
docs: ERD 에 역할 할 일 표 추가 (#7)
```

## 리뷰할 때 보는 것

- 화면에서 직접 눌러 보고 되는지 (개발용 로그인으로 두 사람을 띄워 보면 실시간·권한까지 보인다)
- `./gradlew test` 가 통과하는지
- [팀 코드 규칙](docs/코드-규칙.md)을 지켰는지 (층 나누기, 주소 규칙, 권한 확인 `tripAccess`)
- 스택 표에 없는 기술이 들어갔으면 왜인지 묻는다
- 지적은 코드 줄에 댓글로, 말투는 "~하면 어때요?"

## 충돌이 나면

```bash
git switch main
git pull
git switch feat/12-trip-edit
git merge main
```

충돌 난 파일을 열어 `<<<<<<<` ~ `>>>>>>>` 부분을 고치고 다시 커밋·푸시. 모르겠으면 그 파일 주인과 같이 본다.

## 하지 말 것

- `main` 에 바로 푸시, `git push --force`
- 카카오 키·시크릿, `backend/secrets.properties` 올리기
- 남의 기능 파일을 말없이 고치기 (이슈나 PR 댓글로 먼저)
