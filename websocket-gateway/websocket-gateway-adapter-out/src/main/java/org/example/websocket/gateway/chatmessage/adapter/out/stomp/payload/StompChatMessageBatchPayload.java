package org.example.websocket.gateway.chatmessage.adapter.out.stomp.payload;

import java.util.List;

/**
 * 방 하나의 메시지 여러 건을 한 프레임으로 묶는 봉투다.
 *
 * <p>{@code roomId} 를 메시지마다 반복하지 않는다. 같은 방의 묶음이므로 봉투에 한 번만 둔다.
 *
 * <p>배칭은 conflation(뱃지)과 다르다 — 메시지는 내용이라 <b>한 건도 버리지 않고 순서를 지킨다.</b>
 * 버퍼가 방 수만큼이 아니라 유입량만큼 커지므로 방당 상한이 필요하다.
 */
public record StompChatMessageBatchPayload(
        String roomId,
        List<StompChatMessagePayload> messages
) {
}
