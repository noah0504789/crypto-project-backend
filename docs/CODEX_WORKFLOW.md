# CODEX_WORKFLOW.md

이 문서는 Codex/AI 코딩 에이전트가 `crypto-project-backend`에서 분석, 수정, 테스트, 문서화를 수행하는 절차를 정의한다.

## 1. 기본 작업 철학

Codex는 빠르게 코드를 고치는 도구가 아니라, 기존 구조를 읽고 안전한 변경 단위를 제안한 뒤 필요한 만큼만 수정하는 보조자다.

기본 순서:

```text
요청 이해
  -> 현재 구조 확인
  -> 관련 코드와 유사 패턴 검색
  -> 영향 범위 판단
  -> 최소 변경 계획
  -> 코드/문서 수정
  -> 테스트/검증
  -> 결과 요약
```

사용자가 “바로 수정해줘”, “완성본 줘”, “테스트 짜줘”라고 명확히 요청한 경우에는 분석 후 구현까지 진행한다. 그래도 외부 계약이나 대규모 리팩토링은 먼저 경고한다.

## 2. 작업 시작 체크리스트

루트 확인:

```bash
pwd
./gradlew projects
find . -maxdepth 2 -name settings.gradle -o -name build.gradle | sort
```

Git 상태 확인:

```bash
git status --short
```

하위 경로가 별도 Git repo 성격이면 추가 확인:

```bash
git -C chat status --short
git -C user status --short
git -C oauth2-client status --short
git -C oauth2-authorization-server status --short
git -C websocket-gateway status --short
git -C spring-cloud-config status --short
git -C git-config-repo status --short
```

수정 전 원칙:

- 사용자 변경사항은 되돌리지 않는다.
- build 산출물, `.idea`, `.gradle`, secret 파일을 수정 대상으로 삼지 않는다.
- 검색 결과가 많으면 관련 모듈부터 좁힌다.

## 3. 분석 요청 처리

분석만 요청받으면 코드를 수정하지 않는다.

응답 형식:

```text
요약
- ...

발견한 패턴
- ...

문제점/위험
- ...

추천 방향
- ...

검증 방법
- ...

바로 수정하면 위험한 항목
- ...
```

분석 기준:

- 실제 코드에서 확인한 것만 근거로 삼는다.
- 같은 문자열/패턴을 전체 검색한다.
- 한 파일만 보고 결론 내리지 않는다.
- 외부 계약 변경 여부를 먼저 판단한다.

유용한 검색:

```bash
grep -R "문자열" -n --include='*.java' .
grep -R "@ReadReplica\|DataSourceContextHolder" -n --include='*.java' .
grep -R "transaction_id\|dlq_id\|__TypeId__" -n --include='*.java' .
grep -R "@GrpcService\|@RestController\|@MessageMapping" -n --include='*.java' .
```

## 4. 수정 요청 처리

수정 요청을 받으면 다음 순서로 진행한다.

1. 관련 파일을 읽는다.
2. 동일 패턴을 검색한다.
3. 변경 범위를 service/module 단위로 제한한다.
4. 외부 계약 변경 여부를 판단한다.
5. 코드를 수정한다.
6. 단위 테스트 또는 통합 테스트를 보강한다.
7. 가능한 최소 테스트 명령을 실행한다.
8. 결과를 요약한다.

수정 후 응답 형식:

```text
변경 요약
- ...

검증
- 실행: ...
- 미실행: ...

영향 범위
- ...

후속 TODO
- ...
```

## 5. 코드 리뷰 요청 처리

리뷰 요청은 finding 중심으로 답한다. 먼저 수정하지 않는다.

우선순위:

1. 실제 버그
2. 데이터 정합성/장애 위험
3. 보안/인증 위험
4. 외부 계약 깨짐
5. 테스트 누락
6. 컨벤션/가독성

형식:

```text
[심각도] 파일:라인
문제
영향
수정 제안
```

문제를 찾지 못했으면 “특별한 문제를 찾지 못했다”고 명확히 말하고 남은 리스크를 적는다.

## 6. 문서화 요청 처리

문서화 요청을 받으면 다음을 반영한다.

- 실제 모듈 구조
- 현재 구현된 패턴
- 지켜야 하는 외부 계약
- 아직 정리되지 않은 예외/주의사항
- 추천 컨벤션
- 테스트/검증 방법

문서가 코드보다 앞서가면 안 된다. 이상적인 구조는 `목표`, `후보`, `권장 방향`으로 표시한다.

문서 수정 시 우선순위:

1. `AGENTS.md`: 에이전트 작업 규칙
2. `ARCHITECTURE.md`: 서비스 구조/흐름/계약
3. `CODE_STYLE.md`: 코드/테스트 스타일
4. `CODEX_WORKFLOW.md`: 작업 절차/명령어

## 7. 테스트 명령

전체 테스트:

```bash
./gradlew test
```

서비스 단위 테스트:

```bash
./gradlew :chat:test
./gradlew :user:test
./gradlew :oauth2-client:test
./gradlew :oauth2-authorization-server:test
./gradlew :websocket-gateway:test
./gradlew :spring-cloud-api-gateway:test
./gradlew :spring-cloud-config:test
./gradlew :outbox-poller:test
./gradlew :market-detection:test
```

서브모듈 단위 테스트:

```bash
./gradlew :common:common-jpa:test
./gradlew :common:common-id:test
./gradlew :oauth2-client:oauth2-client-application:test
./gradlew :user:user-application:test
./gradlew :chat:chat-application:test
```

특정 테스트:

```bash
./gradlew :oauth2-client:oauth2-client-application:test --tests CustomOidcUserServiceTest
./gradlew :common:common-jpa:test --tests ReadReplicaRoutingIntegrationTest
```

bootJar:

```bash
./gradlew :oauth2-client:oauth2-client-bootstrap:bootJar
./gradlew :user:user-bootstrap:bootJar
./gradlew :chat:chat-bootstrap:bootJar
./gradlew :websocket-gateway:websocket-gateway-bootstrap:bootJar
```

루트 aggregate task가 설정되어 있으면 다음도 가능하다.

```bash
./gradlew :oauth2-client:bootJar
```

단, 실패하면 실제 bootstrap 모듈 task를 확인한다.

```bash
./gradlew :oauth2-client:tasks --all | grep bootJar
```

## 8. 기능별 작업 절차

### 8.1 OAuth2/OIDC 수정

확인 파일:

```bash
find oauth2-client -path '*src/main/java*' -type f | sort
grep -R "CustomOidcUser\|OidcProviderProfile\|AuthorizedClient\|Logout" -n oauth2-client oauth2-authorization-server --include='*.java'
```

체크:

- `CustomOidcUser#getName()` 의미가 principal name인지 확인한다.
- email/userId/providerSub 혼동 여부를 확인한다.
- OAuth2AuthorizedClient 저장 기준과 logout 삭제 기준이 일치하는지 확인한다.
- provider별 claim parsing은 extractor/resolver에 둔다.
- auth server registered client id/secret config/Vault가 양쪽에 존재하는지 확인한다.

검증:

```bash
./gradlew :oauth2-client:oauth2-client-application:test
./gradlew :oauth2-client:oauth2-client-adapter-in:test
./gradlew :oauth2-authorization-server:test
```

### 8.2 Gateway/JWT 수정

확인:

```bash
grep -R "SecurityWebFilterChain\|ReactiveJwtDecoder\|JwtGrantedAuthoritiesConverter" -n spring-cloud-api-gateway --include='*.java'
grep -R "api-path\|routes\|jwt" -n git-config-repo spring-cloud-api-gateway --include='*.yml' --include='*.java'
```

체크:

- issuer/audience/key id가 auth server와 맞는지 확인한다.
- roles claim prefix(`ROLE_`)와 hasRole/hasAuthority 사용을 구분한다.
- permitAll path가 더 구체적인 보호 path보다 뒤에 와서 우회되지 않는지 확인한다.
- 401/403/CORS 응답이 브라우저에서 해석 가능한지 확인한다.

### 8.3 Read replica 수정

확인:

```bash
grep -R "ReadReplica\|DataSourceContextHolder\|ReplicationRoutingDataSource" -n common user --include='*.java'
```

체크:

- write transaction active 상태에서는 read context를 켜지 않는다.
- `@Transactional(readOnly = true)`만으로 read 라우팅하지 않는다.
- `LazyConnectionDataSourceProxy`가 routing datasource 앞에 있는지 확인한다.
- 통합 테스트는 test method transaction을 끄거나 의도적으로 분리한다.

검증:

```bash
./gradlew :common:common-jpa:test --tests ReadReplicaAspectTest
./gradlew :common:common-jpa:test --tests ReadReplicaRoutingIntegrationTest
```

### 8.4 Outbox/DLQ 수정

확인:

```bash
grep -R "Outbox\|Dlq\|transaction_id\|dlq_id\|StreamBridge" -n common chat outbox-poller --include='*.java'
```

체크:

- domain event가 Outbox/DLQ listener로 저장되는지 확인한다.
- entity 상태 변경이 도메인 메서드로 수행되는지 확인한다.
- Kafka header가 producer/consumer에서 일치하는지 확인한다.
- retry exhausted 이후 상태 전이가 명확한지 확인한다.

검증:

```bash
./gradlew :common:common-outbox:test
./gradlew :outbox-poller:test
./gradlew :chat:chat-application:test
```

### 8.5 Redis/cache 수정

확인:

```bash
grep -R "RedisKey\|CacheFailOpen\|StringRedisTemplate\|RedisTemplate\|Lua" -n common chat oauth2-authorization-server websocket-gateway --include='*.java'
```

체크:

- key pattern 인자 수가 맞는지 확인한다.
- cluster hash tag가 유지되는지 확인한다.
- 조회 실패와 command 실패 정책이 구분되는지 확인한다.
- 삭제/복구 이벤트가 idempotent한지 확인한다.

### 8.6 gRPC/protobuf 수정

확인:

```bash
find protobuf/src/main/proto -type f -print
grep -R "Grpc.*Client\|@GrpcService\|deadline\|withDeadline" -n . --include='*.java'
```

체크:

- proto field number 재사용 금지.
- server와 client 모두 재생성/재빌드 필요.
- deadline/cancel/error mapping 영향 확인.
- gateway/websocket/oauth2/user 간 client dependency 확인.

검증:

```bash
./gradlew :protobuf:build
./gradlew :user:test
./gradlew :chat:test
./gradlew :oauth2-client:test
./gradlew :spring-cloud-api-gateway:test
```

### 8.7 WebSocket 성능/동작 수정

확인:

```bash
grep -R "Stomp\|MessageMapping\|SimpMessagingTemplate\|Kafka.*Consumer\|ack" -n websocket-gateway --include='*.java'
ls websocket-gateway/k6
```

체크:

- STOMP destination 변경 여부.
- ack timeout과 gRPC deadline 관계.
- broadcast collect window와 topic destination.
- user queue와 room topic이 프론트 코드와 맞는지 확인한다.

검증 예:

```bash
./gradlew :websocket-gateway:test
k6 run websocket-gateway/k6/light_message_200x60_10s.js
```

## 9. Docker/로컬 검증 절차

bootJar 후 서비스 컨테이너 재빌드:

```bash
./gradlew :oauth2-client:oauth2-client-bootstrap:bootJar
docker compose build oauth2-client
docker compose up -d oauth2-client
```

로그 확인:

```bash
docker logs -f oauth2-client
docker logs -f user-service
docker logs -f oauth2-authorization-server
```

Redis cluster auth key 확인:

```bash
docker exec -it redis-0 redis-cli -p 7100 --scan --pattern '{auth}:*'
docker exec -it redis-1 redis-cli -p 7101 --scan --pattern '{auth}:*'
docker exec -it redis-2 redis-cli -p 7102 --scan --pattern '{auth}:*'
```

slot 확인:

```bash
docker exec -it redis-0 redis-cli -p 7100 cluster keyslot '{auth}:test'
```

Vault 확인은 root token/secret을 응답에 노출하지 않는다.

## 10. PR 단위 쪼개기 기준

큰 작업은 다음처럼 나눈다.

1. 테스트 추가만.
2. 내부 리팩토링만.
3. 외부 계약 변경.
4. 설정 변경.
5. Docker/운영 스크립트 변경.
6. 문서 변경.

예: OAuth2 logout Redis key 정리는 다음처럼 나눈다.

```text
PR1: 현재 Redis key 저장/삭제 기준 테스트 추가
PR2: CustomOidcUser principal name 정리
PR3: logout handler 삭제 기준 보강
PR4: docs 업데이트
```

## 11. 실패 처리

테스트/빌드가 실패하면 다음 순서로 보고한다.

```text
실패 명령
실패 메시지 핵심
가능한 원인
수정 후보
재실행 명령
```

원인 확정 전에는 “해결됐다”고 말하지 않는다.

## 12. 최종 응답 템플릿

```text
완료했어.

변경 파일
- ...

핵심 변경
- ...

검증
- 실행: ...
- 결과: ...

주의/후속
- ...
```
