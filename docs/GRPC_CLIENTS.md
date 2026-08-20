# gRPC Client 비동기 명세

## 1. 목적

서비스 공용 `*-client` 모듈은 MVC, WebFlux, 배치 등 서로 다른 실행 환경에서 재사용된다. 따라서 특정 reactive 구현에 결합하지 않고 gRPC Java의 future stub을 JDK 표준 `CompletableFuture`로 노출한다. 호출 결과를 application 타입으로 해석하는 책임은 client가 아니라 각 서비스의 outbound adapter에 둔다.

이 명세는 Java 내부 client API에 관한 것이다. `protobuf/src/main/proto`의 service, method, message와 field number는 변경하지 않는다.

## 2. 계층별 책임

| 경계 | 반환 타입 | 책임 |
|---|---|---|
| 공용 `*-client` | `CompletableFuture<GrpcResponse>` | request 생성, future stub 호출, deadline 적용, 완료·오류·취소 전달 |
| 서비스 outbound adapter | 서비스 실행 방식에 맞춤 | gRPC DTO를 application result로 매핑하고 필요한 오류를 번역 |
| 본질적으로 비동기인 application port | `CompletableFuture<ApplicationResult>` 허용 | 비동기 완료를 계약으로 유지 |
| WebFlux 소비자 | `Mono<ApplicationResult>` | 구독 경계에서 `Mono.fromFuture()`로 변환 |
| 동기 소비자 | 동기 application 타입 | adapter 경계에서 `GrpcFutures.join()` 후 매핑 |

공용 client에는 Reactor 의존성과 `Mono`/`Flux`가 들어가지 않는다. 반대로 WebFlux adapter에서 `CompletableFuture.join()`이나 blocking stub을 호출하지 않는다.

## 3. 표준 구현

공용 client는 `newFutureStub(channel)`에 deadline을 적용하고 `GrpcFutures.toCompletableFuture()`로 변환한다.

```java
public CompletableFuture<GrpcResponse> find(String id) {
    GrpcRequest request = GrpcRequest.newBuilder().setId(id).build();
    return GrpcFutures.toCompletableFuture(stub().find(request));
}
```

WebFlux adapter는 구독 전에는 원격 호출을 시작하지 않도록 지연 생성하고 마지막 경계에서 Reactor 타입으로 바꾼다.

```java
return Mono.defer(() -> Mono.fromFuture(client.find(id)))
        .map(this::toResult);
```

동기 framework 또는 application port를 구현하는 adapter만 완료를 기다린다. `GrpcFutures.join()`은 `CompletionException`을 벗겨 원래 runtime 오류 의미를 보존한다.

```java
GrpcResponse response = GrpcFutures.join(client.find(id));
return toResult(response);
```

## 4. 완료·오류·취소 규칙

- client별 기존 deadline은 `Grpc*ClientProperties`에서 계속 주입한다.
- gRPC 실패는 `CompletableFuture`의 exceptional completion으로 전달한다.
- 결과 future가 취소되면 진행 중인 gRPC future/call도 취소한다.
- adapter의 매핑 future를 취소해도 원본 호출이 취소되도록 `GrpcFutures.map()`을 사용한다.
- gRPC status를 application 예외로 바꾸는 작업은 client가 아니라 소비 adapter에서 수행한다.
- 동기 adapter의 대기는 호출 thread를 점유한다. 이 방식은 동기 계약을 유지해야 하는 경계에만 사용하며 WebFlux event-loop에서는 사용하지 않는다.

## 5. 현재 적용 현황

| 공용 client | 소비 adapter | 연결 방식 |
|---|---|---|
| `ChatMessageClient` | websocket-gateway `GrpcChatMessageCommandAdapter` | application port가 `CompletableFuture<ApplicationResult>` 유지 |
| `MarketClient` | upbit-connector `UpbitWebsocketTickerStreamAdapter` | `Mono.fromFuture()` 후 구독 코드 매핑 |
| `PriceAlertSettingClient` | notification `PriceAlertRecipientQueryAdapter` | 동기 adapter에서 join 후 UUID 매핑 |
| `UserClient` | oauth2-client `GrpcUserAdapter`, authorization-server `GrpcUserQueryAdapter` | 동기 adapter에서 join 후 `UserResponse` 매핑 |
| `Oauth2AuthorizationServerClient` | API Gateway `GrpcBlacklistTokenClientAdapter` | `Mono.fromFuture()` 후 `BoolValue` 매핑 |
| `Oauth2AuthorizationServerClient` | oauth2-client `GrpcAuthServerTokenAdapter` | 동기 adapter에서 join 후 scalar 매핑 |

## 6. 테스트 기준

- 공용 변환기는 정상 완료, exceptional completion, 양방향 취소 전파, join 오류 복원을 단위 테스트한다.
- client는 future stub deadline과 gRPC 오류·취소 전파를 검증한다.
- WebFlux adapter는 lazy invocation, 값/오류 전파, subscriber 취소 시 client future 취소를 `StepVerifier`로 검증한다.
- 동기 adapter는 gRPC DTO의 application 타입 매핑과 오류 전파를 검증한다.
- proto를 변경했다면 이 문서 범위를 넘어 외부 계약 영향 검토와 모든 producer/consumer 재빌드가 필요하다.
