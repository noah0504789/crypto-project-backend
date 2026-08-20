# gRPC Client 규칙

- 공용 `*-client`는 Reactor에 의존하지 않고 `CompletableFuture<GrpcResponse>`를 반환한다.
- 공용 client는 request 생성, future stub 호출, deadline, 완료·오류·취소 전달까지만 담당한다.
- gRPC DTO를 application result로 매핑하고 gRPC 오류를 번역하는 책임은 소비 서비스 adapter에 둔다.
- 작업이 본질적으로 비동기이면 application port의 `CompletableFuture<ApplicationResult>`를 허용한다.
- WebFlux 소비자는 마지막 adapter 경계에서만 `Mono.fromFuture()`로 변환하며 event-loop에서 `join()`하지 않는다.
- 동기 소비자는 adapter 경계에서만 `GrpcFutures.join()`하고 즉시 application 타입으로 매핑한다.
- future 변환·매핑은 `common-grpc-client`의 `GrpcFutures`를 사용해 취소 전파와 오류 복원 규칙을 통일한다.
- 상세 근거와 예시는 `docs/GRPC_CLIENTS.md`를 따른다.
