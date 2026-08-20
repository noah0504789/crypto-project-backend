# common — 공통 모듈 작업 지침

이 파일은 `common/` 안에서 코드를 작업할 때만 적용된다. 공통 규칙은 루트 `../CLAUDE.md`와 `../.claude/rules/*.md`를 따르며 여기서는 반복하지 않는다. 상세 구조·역할·계약·근거는 [`../docs/modules/COMMON.md`](../docs/modules/COMMON.md)를 참고한다.

이 모듈군은 **전 서비스가 의존하는 공유 기반**이라 변경의 파급이 가장 크다. 계약(enum/이벤트/키/예외 형식) 변경은 `../.claude/rules/external-contracts.md` 절차(전 저장소 사용처 검색 → producer/consumer/test/config 확인 → 호환성 설명 → 승인)를 따르고, 여러 모듈에 영향을 주므로 `../.claude/rules/git-safety.md`의 Plan Mode 우선 대상이다.

## 모듈군 역할과 적용 범위

`common`(부모)은 16개 모듈(`common-core`·`-exception`·`-validation`·`-time`·`-jpa`·`-event`·`-web`·`-grpc`·`-id`·`-inbox`·`-outbox`·`-redis`·`-redisson`·`-util`·`-tx`·`-mongo`)을 `api`로 재수출하는 파사드다. 실제 소비는 `common-arch-test`의 `testRuntimeOnly` 하나뿐이라, 재수출 목록은 편의가 아니라 **ArchUnit 커버리지 통로**다. `common-test`·`common-arch-test`·`common-actuator-*`는 파사드에서 제외되어 필요한 곳만 개별 의존한다. 각 모듈 역할은 [`../docs/modules/COMMON.md §3`](../docs/modules/COMMON.md)의 표를 본다. `common/`에 코드 변경이 없는 작업엔 이 파일을 적용하지 않는다.

## 주요 변경 규칙

- **예외는 `common-exception`, 검증은 `common-validation`, 시각은 `common-time`**: `Clock`은 wall-clock 조회와 경과시간 측정용 `monotonicTimeNanos()` 계약을 제공하고 `ClockService`만 시스템 시간을 직접 읽는다. 소비 코드는 `System.currentTimeMillis()`/`System.nanoTime()` 대신 주입된 `Clock`에 의존한다. `common-core`는 계약 enum·properties만 갖고 외부 의존은 `spring-boot` 코어뿐이다. 검증 starter를 `common-core`로 되돌리면 전 모듈에 다시 전파된다.
- **계약 문자열은 `common-core`에서만**: `RedisKey`·`KafkaTopic`·`KafkaHeaderKey`·`StompDestination`·`JwtClaimKey`·`HttpHeaderKey`·`AuthTokenKey`·`RoleKey` 등의 이름/값/의미를 별도 설명 없이 바꾸지 않는다. 소비처(전 서비스·프론트·저장된 데이터)에 영향(→ external-contracts). `RedisKey`는 pattern + expectedArgCount, hash tag(`{chat}`/`{auth}`/`{session}`)를 유지한다.
- **common은 서비스 모듈에 의존 금지**: `common-*`가 서비스(`chat`/`user`/… ) 모듈이나 그 패키지를 import하면 `common-arch-test`가 실패한다. 방향은 항상 서비스 → common.
- **아키텍처 변경 시 게이트 실행**: 의존/계층/패키지 구조를 건드리면 `./gradlew :common:common-arch-test:test`(ArchUnit)를 반드시 실행한다. 규칙 자체(`ModuleArchitectureTest`/`PackageArchitectureTest`)를 완화해 통과시키지 않는다(→ git-safety).
- **파사드 목록 관리**: 새 공통 모듈을 만들면 `settings.gradle` 등록 + 파사드 재수출 여부(`common/build.gradle`)를 명시적으로 결정한다. 테스트/CI/모니터링 성격 모듈은 파사드에 넣지 않는다.
- **Outbox/DLQ 흐름 보존**(`common-outbox`): 상태 변경은 도메인 메서드로만(`markPublished`·`markFailed`·`increaseRetryCnt`·`markCompleted`). 발행은 `EventUtils.raise` → `OutboxEventListListener` → `OutboxService.saveAll` 경로를 지키고 `ApplicationEventPublisher`를 직접 쓰지 않는다. `__TypeId__`·`event_id`·`transaction_id`·`dlq_id` 헤더는 계약이다. `event_id`는 Outbox/DLQ 레코드 ID로 같은 레코드 재발행에서도 유지된다. `DlqStatus`는 `@Enumerated(STRING)`으로 이름이 저장되는 계약이다(소비 실패 상태 = `CONSUME_FAILED`). 값 추가/변경 시 저장된 row 영향을 함께 본다.
- **Inbox 경계 유지**(`common-inbox`): 비멱등 consumer는 `InboxService`의 `(consumer_name,event_id)` unique INSERT와 비즈니스 처리를 같은 event DB 트랜잭션에 묶는다. `AbstractInboxEvent`가 header용 ID를 만들고 `extractEventId(Message<?>)`로 consumer의 `event_id` header를 읽는다.
- **Read Replica 규칙**(`common-jpa`): `@ReadReplica`가 read 라우팅 트리거다. `@Transactional(readOnly=true)`만으로 read로 보내지 않는다. `ReadReplicaAspect`/`DataSourceContextHolder`/`ReplicationRoutingDataSource` 동작을 바꾸면 전 JPA 서비스에 영향.
- **예외 매핑 일관성**: REST 예외는 `common-web/GlobalExceptionHandler`(`ErrorResponse`/`ValidationResult` 형식), gRPC 서버 예외는 `common-grpc-server/AbstractGrpcExceptionAdvice`가 담당한다. 응답 형식을 흔들지 않는다.
- **actuator 선택**: MVC 서비스는 `common-actuator-webmvc`, gateway(WebFlux)는 `common-actuator-webflux`를 쓴다(공용 코어는 `common-actuator-core`). 배포 제어 토큰은 `deployment.control.token`(`${DEPLOY_TOKEN}`).

## 주요 파일 안내

| 파일 | 역할 |
|---|---|
| [`common-core/.../enums/`](common-core/src/main/java/org/example/common/enums/) | 계약 상수(Redis/Kafka/STOMP/JWT/HTTP/Role 키) |
| [`common-exception/.../exception/`](common-exception/src/main/java/org/example/common/exception/) | 공통 예외 계층·`ErrorResponse`(외부 의존 0) |
| [`common-validation/.../validation/`](common-validation/src/main/java/org/example/common/validation/) | `ValidationResult`·`NotBlankIfPresent`·메시지 번들. validation starter 보유 |
| [`common-time/.../time/`](common-time/src/main/java/org/example/common/time/) | `Clock`은 주입 인터페이스라 테스트에서 고정 시각을 넣는다. static 유틸로 바꾸지 않는다 |
| [`common-jpa/.../aop/ReadReplicaAspect.java`](common-jpa/src/main/java/org/example/common/jpa/aop/ReadReplicaAspect.java) | read 라우팅 트리거 |
| [`common-event/.../EventUtils.java`](common-event/src/main/java/org/example/common/event/EventUtils.java) | 도메인 이벤트 수집·발행 진입점 |
| [`common-event/.../KafkaEventFactory.java`](common-event/src/main/java/org/example/common/event/KafkaEventFactory.java) | Kafka `Message`와 공통 헤더 생성 책임 |
| [`common-inbox/.../AbstractInboxEvent.java`](common-inbox/src/main/java/org/example/common/inbox/domain/event/AbstractInboxEvent.java) | Inbox 대상 이벤트의 `eventId` 상속·무작위 UUID 생성 |
| [`common-inbox/.../inbox/`](common-inbox/src/main/java/org/example/common/inbox/) | `(consumer_name,event_id)` unique 기반 consumer 중복 선점·header 추출 |

Inbox 예외는 HTTP 상태나 인프라 실패를 뜻하지 않는 `InboxException` 계층으로 묶는다. 구체 예외는 이 타입을 상속하고 Kafka adapter가 처리 정책을 결정한다.
| [`common-outbox/.../outbox/`](common-outbox/src/main/java/org/example/common/outbox/) · [`.../dlq/`](common-outbox/src/main/java/org/example/common/dlq/) | Outbox/DLQ 도메인·서비스 |
| [`common-web/.../web/exception/GlobalExceptionHandler.java`](common-web/src/main/java/org/example/common/web/exception/GlobalExceptionHandler.java) | REST 예외 매핑 |
| [`common-grpc-server/.../exception/AbstractGrpcExceptionAdvice.java`](common-grpc-server/src/main/java/org/example/common/grpc/exception/AbstractGrpcExceptionAdvice.java) | gRPC 서버 예외 매핑 |
| [`common-arch-test/src/test/java/org/example/arch/`](common-arch-test/src/test/java/org/example/arch/) | ArchUnit 계층/의존 게이트 |
| [`common/build.gradle`](build.gradle) | 파사드 재수출 목록 |

## 검증 명령

- 컴파일: `./gradlew :common:common-core:compileJava`(대상 서브모듈)
- 서브모듈 테스트: `./gradlew :common:common-jpa:test`, `./gradlew :common:common-redis:test` 등
- 아키텍처 게이트: `./gradlew :common:common-arch-test:test`(구조 변경 시 필수)
- `commonCi`/`protobufCi` 같은 집계 task는 없다. 전체 build/test·`bootRun`·배포는 명시적 요청 없이 수행하지 않는다.

## 확인 필요 항목

확정된 결함으로 단정하지 않는다. 코드 변경 전 사용자 확인이 필요하다. 상세·근거는 [`../docs/modules/COMMON.md §8`](../docs/modules/COMMON.md)과 [`../TODO.md`](../TODO.md).

- 현재 common 관련 미해결 항목 없음.
