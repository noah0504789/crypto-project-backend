package org.example.common.redisson.singleflight;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * In-process single-flight: 같은 key 에 대한 동시 로드 요청을 하나로 합쳐 loader 를 1회만 실행하고
 * 결과를 대기 중인 나머지 호출자에게 공유한다.
 *
 * <p>분산락과의 차이: 분산락은 클러스터 전역 상호배제(락 획득·lease·retry·timeout 왕복)이고, single-flight 는
 * <b>인스턴스 내</b> 중복 호출 제거다(코디네이션 없음, 결과 공유). 인스턴스가 N개면 최악 N회 로드가 발생하지만
 * cache miss 폭풍(수천 동시 로드)은 크게 줄인다.
 *
 * <p><b>주의</b>: 이것은 SWR(stale-while-revalidate)이 아니다. 대기 중인 호출자는 loader 완료까지 <b>동기 대기</b>하며
 * 결과는 fresh 값이다(만료값 즉시 반환 아님). 다만 분산락의 락 왕복·대기가 없어 대기는 loader 실행 시간뿐이다.
 * 저비용 reload 의 cold miss 완화나, write-through 로 대부분 채워지는 캐시의 드문 miss 방어에 쓴다.
 * 무거운 reload 를 전역 1회로 강제해야 하면 분산락을 쓴다.
 */
@Slf4j
@Component
public class SingleFlight {

    private final ConcurrentMap<String, CompletableFuture<?>> inFlight = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Supplier<T> loader) {
        CompletableFuture<T> mine = new CompletableFuture<>();
        CompletableFuture<T> running = (CompletableFuture<T>) inFlight.putIfAbsent(key, mine);

        if (running != null) {
            // 이미 같은 key 로 로드 중 → loader 를 재실행하지 않고 그 결과를 공유(대기). = miss 폭풍이 합쳐지는 지점.
            log.debug("[single-flight] join in-flight load. key={}", key);
            return join(running);
        }

        log.debug("[single-flight] start load. key={}", key);
        try {
            T value = loader.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }
}
