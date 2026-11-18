package com.tripmate.trip;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

// 방마다 열린 SSE 연결을 들고 있다가 바뀌면 "다시 읽어라"만 보낸다. 데이터는 화면이 기존 API 로 읽는다.
// shortcut: 연결 목록이 서버 메모리에 있어 서버 한 대에서만 맞다. 여러 대로 늘리면 Redis pub/sub 같은 것으로 바꾼다
@Component
public class TripEvents {

    // 끊긴 연결이 계속 남지 않게 시간을 둔다. 브라우저 EventSource 는 끊기면 알아서 다시 붙는다
    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    SseEmitter subscribe(Long tripId) throws IOException {
        var emitter = new SseEmitter(TIMEOUT);
        var set = emitters.computeIfAbsent(tripId, id -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        emitter.onCompletion(() -> set.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> set.remove(emitter));
        // 첫 바이트를 바로 보내야 프록시가 응답 머리를 붙잡지 않고 넘긴다
        emitter.send(SseEmitter.event().comment("connected"));
        return emitter;
    }

    // 커밋 뒤에 보낸다. 먼저 보내면 화면이 커밋 전 옛 값을 다시 읽어 간다
    @TransactionalEventListener
    void send(TripChanged event) {
        sendAll(emitters.getOrDefault(event.tripId(), Set.of()), "change", event.what());
    }

    // 서버가 꺼져도 중간 프록시가 브라우저 쪽 연결을 열어 두면 브라우저는 끊긴 줄 모른다.
    // 주기적으로 ping 을 보내 두면 화면이 "한동안 조용하다"로 끊김을 알아채고 다시 붙는다(LiveRefresh.tsx)
    @Scheduled(fixedRate = 15_000)
    void ping() {
        emitters.values().forEach(set -> sendAll(set, "ping", ""));
    }

    private void sendAll(Set<SseEmitter> set, String name, String data) {
        for (var emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
