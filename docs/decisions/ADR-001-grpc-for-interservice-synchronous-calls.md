# ADR-001: 서비스 간 동기 호출에 gRPC contract/client 모듈 사용

- 상태: 채택됨 (기존 구현을 문서화)
- 범위: backend 서비스 간 동기 요청

## 맥락과 결정

여러 서비스가 즉시 응답을 필요로 한다. 서비스 간 동기 요청은 `protobuf/src/main/proto`의 gRPC 계약과 소비자용 `*-client` 모듈을 사용한다. 제공 서비스의 implementation module을 consumer가 직접 Gradle 의존하지 않는다.

주소는 Eureka discovery를 사용하는 `discovery:///` 형식이고, client별 deadline은 설정으로 주입한다. 비동기 전달·fan-out은 이 ADR의 대상이 아니라 ADR-002의 Outbox/Kafka 흐름을 따른다.

여기서 “동기 호출”은 요청자가 즉시 결과를 필요로 하는 request-response 의미다. Java client 구현이 thread를 막아야 한다는 뜻은 아니다. 공용 client는 future stub 결과를 `CompletableFuture<GrpcResponse>`로 제공하고, 소비 adapter가 자신의 실행 모델에 맞춰 application 타입으로 매핑한다. WebFlux는 `Mono.fromFuture()`로 연결하고 동기 framework 경계만 완료를 기다린다.

## 근거와 결과

- proto와 client wrapper로 계약을 분리해 구현체가 아닌 명시적 타입 계약에 의존한다.
- `ModuleArchitectureTest`가 서비스 간 구현 의존을 차단하고 contract/client만 허용한다.
- `AbstractGrpcExceptionAdvice`와 서비스별 `@GrpcAdvice`가 gRPC 오류를 REST와 분리해 status로 관리한다.
- proto field/service/method 변경은 외부 계약이다. producer·모든 consumer·설정·테스트를 함께 조사하고, `:protobuf:build` 뒤 소비 서비스를 재빌드한다.

## 관련 근거

- `docs/ARCHITECTURE.md` §3, §7.1
- `docs/GRPC_CLIENTS.md`
- `.claude/rules/external-contracts.md`의 gRPC/Proto 규칙
- `docs/modules/*.md`의 gRPC 계약 절
