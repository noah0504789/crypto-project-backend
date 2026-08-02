package org.example.notification.application.port.out;

import org.example.notification.domain.model.Notification;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notification master 정보 1차 캐시(Redis) 포트.
 *
 * <p>master 는 <b>불변</b>(생성 후 상태 변화 없음)이라 stale 이 존재할 수 없다. 따라서 PER·SWR·분산락 없이
 * <b>생성 시 선적재(warm-up) + 긴 TTL + LFU 축출</b>로 운영한다. cache miss 는 긴 TTL·선적재로 드물게 만들고,
 * 설령 겹쳐도 재조회가 Mongo 포인트 조회로 싸고 fail-open 이라 무해하다. 인덱스(정렬/커서)는 캐싱하지 않는다.
 */
public interface NotificationCachePort {

    /**
     * 주어진 id 들의 master 정보를 캐시에서 조회한다. 존재하는 항목만 반환한다(불변이라 stale 판정 불필요).
     * 캐시 장애 시 fail-open(빈 맵) 으로 동작한다.
     */
    Map<String, Notification> findByIds(Set<String> ids);

    /**
     * master 정보를 캐시에 적재(warm-up)한다. 생성 시 선적재와 조회 miss 시 lazy 적재 양쪽에서 쓴다.
     */
    void warmUpAll(List<Notification> notifications);

    /**
     * master 캐시를 무효화한다(soft-delete 등, 현재 호출부 없음).
     */
    void invalidate(String id);
}
