package com.tripmate.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 두 사람이 같은 것을 거의 동시에 바꾸면 DB 가 막는다. 역할 카드는 @Version, 장보기 줄·정산 링크·투표·다음 방은 유일 제약.
// 늦게 온 쪽은 "누가 먼저 했다"라 409 를 주고, 화면이 다시 읽어 지금 상태를 보인다.
// 컨트롤러마다 같은 처리기를 두지 않으려고 한 곳에 모았다. 컨트롤러 안에 따로 둔 처리기가 있으면 그쪽이 먼저다
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
