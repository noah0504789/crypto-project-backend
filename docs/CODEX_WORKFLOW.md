# crypto-project Codex Workflow

## 1. Codex 사용 목적

Codex는 `crypto-project` 전체 코드를 읽고, 반복되는 컨벤션을 정리하며, 작은 단위의 리팩토링과 테스트 작성을 보조하는 코딩 에이전트로 사용한다.

목표:

- 프로젝트 코드 철학 문서화
- 서비스별 컨벤션 일관화
- 테스트 보강
- `GlobalExceptionHandler` 정리
- 정적 상수, enum, properties 관리 개선
- Lombok 생성자 및 builder access level 정리
- Outbox/DLQ와 read replica 구조 보존
- 멀티모듈 전환 준비

## 2. 기본 원칙

Codex는 바로 수정하지 않고 먼저 근거를 수집한다.

기본 순서:

1. 전체 구조 확인
2. 관련 서비스 범위 식별
3. 같은 패턴 전체 검색
4. 문제점과 영향 범위 정리
5. 최소 수정 계획 제시
6. 코드 수정
7. 서비스 단위 테스트 실행
8. 변경 요약과 남은 TODO 정리

단, 사용자가 명확히 "수정해줘", "문서화해줘", "테스트 추가해줘"라고 요청하면 분석 후 바로 구현까지 진행한다.

## 3. 작업 시작 체크리스트

작업 전 확인:

```bash
pwd
find . -maxdepth 2 -type f -name 'settings.gradle' -o -name 'build.gradle'
find . -maxdepth 2 -type d | sort
```

서비스별 Git 상태 확인:

```bash
git -C chat status --short
git -C user status --short
git -C outbox-poller status --short
```

주의:

- 이 작업 디렉토리는 여러 독립 Git 저장소를 포함한다.
- 기존 변경이 있으면 사용자가 만든 변경으로 보고 되돌리지 않는다.
- 특정 서비스 수정 전 해당 서비스의 `git status --short`를 확인한다.

## 4. 분석 요청 처리 방식

분석 요청을 받으면 코드를 수정하지 않는다.

출력 형식:

- 발견한 패턴
- 문제점
- 추천 컨벤션
- 적용 우선순위
- 바로 수정하면 위험한 항목
- 테스트/검증 방법

분석 기준:

- 추측하지 않고 실제 코드에서 발견한 파일/패턴만 근거로 삼는다.
- 관련 서비스 하나만 보지 않고 전체 서비스에서 같은 문자열/패턴을 검색한다.
- 큰 변경은 작은 PR 단위로 쪼갠다.

## 5. 수정 요청 처리 방식

수정 요청을 받으면 다음 순서로 진행한다.

1. 관련 파일과 유사 패턴을 읽는다.
2. 변경 범위를 좁힌다.
3. 기존 public API와 외부 계약 변경 여부를 판단한다.
4. 필요한 파일만 수정한다.
5. 테스트를 추가하거나 기존 테스트를 보강한다.
6. 서비스 단위 테스트를 실행한다.
7. 결과를 한국어로 요약한다.

최종 응답에는 다음을 포함한다.

- 변경 요약
- 실행한 테스트
- 실패/미실행 테스트가 있다면 이유
- 남은 TODO 또는 후속 후보

## 6. 코드 리뷰 요청 처리 방식

리뷰 요청을 받으면 수정하지 않고 finding 중심으로 답한다.

우선순위:

1. 버그
2. 장애/데이터 정합성 위험
3. 외부 계약 깨짐
4. 테스트 누락
5. 컨벤션 이탈

형식:

- 심각도순 finding
- 파일/라인 근거
- 영향
- 최소 수정 제안
- 질문/가정

문제가 없으면 "특별한 문제를 찾지 못했다"고 명확히 말하고 남은 리스크를 적는다.

## 7. 서비스별 테스트 명령

전체 테스트는 비용이 클 수 있으므로 가능한 서비스 단위로 실행한다.

```bash
cd chat
./gradlew test
```

```bash
cd user
./gradlew test
```

```bash
cd outbox-poller
./gradlew test
```

```bash
cd websocket-gateway
./gradlew test
```

```bash
cd oauth2-client
./gradlew test
```

```bash
cd oauth2-authorization-server
./gradlew test
```

```bash
cd spring-cloud-api-gateway
./gradlew test
```

```bash
cd spring-cloud-config
./gradlew test
```

```bash
cd market-detection
./gradlew test
```

특정 테스트만 실행:

```bash
cd chat
./gradlew test --tests ChatRoomCommandServiceTest
```

## 8. 문서화 작업 기준

문서화 요청을 받으면 다음을 포함한다.

- 실제 서비스 구조
- 현재 구현된 패턴
- 권장 컨벤션
- 예외 또는 아직 정리되지 않은 부분
- 바꾸면 위험한 계약
- 앞으로 리팩토링할 우선순위

문서는 한국어로 작성한다.

문서가 코드보다 앞서가면 안 된다. 아직 구현되지 않은 이상적인 구조는 "목표" 또는 "후보"로 명확히 표시한다.

## 9. 상수화 작업 기준

상수화 요청 처리 순서:

1. 같은 문자열이 쓰이는 위치 검색
2. 외부 계약인지 내부 구현인지 구분
3. enum/constants/properties 중 위치 결정
4. 단일 서비스 내부 정리부터 진행
5. 테스트 수정
6. 멀티서비스 공통화는 별도 단계로 제안

검색 후보:

```bash
grep -R 'transaction_id\|dlq_id\|__TypeId__' -n --include='*.java' .
grep -R 'X-User-Id' -n --include='*.java' .
grep -R 'chatMongoTransactionManager\|transactionManager' -n --include='*.java' .
```

바로 공통 모듈로 이동하지 않는다. 먼저 서비스 내부 constants로 안정화한다.

## 10. 예외 처리 작업 기준

GlobalExceptionHandler 정리 순서:

1. Controller 목록 확인
2. custom exception 목록 확인
3. 현재 예외 응답 형태 확인
4. validation 응답 형식 확인
5. status mapping 제안
6. handler와 테스트 추가

검색:

```bash
find . -path '*/src/main/java/*Controller.java' -o -path '*/src/main/java/*GrpcService.java'
find . -path '*/src/main/java/*Exception.java'
grep -R '@RestControllerAdvice\|@GrpcAdvice' -n --include='*.java' .
```

주의:

- OAuth2/Security filter chain의 실패 응답은 Spring Security 흐름과 충돌하지 않게 검토한다.
- gRPC 예외는 REST handler가 아니라 `@GrpcAdvice`에서 처리한다.

## 11. Outbox/DLQ 작업 기준

수정 전 확인:

```bash
grep -R 'Outbox\|Dlq\|transaction_id\|dlq_id' -n --include='*.java' chat outbox-poller
```

규칙:

- `@Transactional("transactionManager")` 제거 금지
- status 직접 대입 대신 도메인 메서드 사용
- retry count 직접 변경 대신 `increaseRetryCnt` 사용
- Publisher/Repository/StreamBridge는 단위 테스트에서 mock
- Kafka header 변경은 외부 계약 변경으로 취급

권장 작업 단위:

1. header constants 정리
2. outbox entity builder access 정리
3. DLQ 상태 전이 테스트 보강
4. retry 대상 exception 구체화

## 12. Read Replica 작업 기준

수정 전 확인:

```bash
grep -R 'ReadReplica\|DataSourceContextHolder\|ReplicationRoutingDataSource' -n --include='*.java' user
```

규칙:

- `@ReadReplica`가 라우팅의 명시적 신호이다.
- `@Transactional(readOnly = true)`만으로 READ 라우팅되게 바꾸지 않는다.
- `DataSourceContextHolder`의 depth 기반 scope는 유지한다.
- 통합테스트에서는 Spring Cloud Config와 Eureka 의존을 끊는다.

필수 검증:

```bash
cd user
./gradlew test --tests ReadReplicaRoutingIntegrationTest
```

## 13. 멀티모듈 전환 작업 기준

멀티모듈 전환 요청을 받으면 바로 파일을 이동하지 않는다.

먼저 제시할 것:

- 대상 서비스
- 현재 패키지 구조
- 모듈 후보
- 의존 방향
- 이동 파일 목록
- build.gradle 변경 범위
- import 변경 범위
- 실행할 테스트
- rollback 방법

권장 순서:

1. `protobuf` 유지/정리
2. 단일 서비스 내부 패키지 정리
3. 공통 test support 후보 분리
4. event contract constants 후보 분리
5. common exception/validation 후보 분리

금지:

- 한 번에 모든 서비스 이동
- 순환 의존이 생기는 모듈 구조
- domain이 infra에 의존하는 구조 강화

## 14. 커밋/브랜치 작업 기준

서비스별 독립 Git 저장소이므로 커밋 요청 시 대상 저장소를 명확히 한다.

확인:

```bash
git -C chat status --short
git -C chat branch --show-current
```

규칙:

- 사용자가 만든 변경은 되돌리지 않는다.
- 관련 없는 dirty file은 커밋에 포함하지 않는다.
- 여러 서비스 변경은 서비스별 커밋을 우선한다.
- 커밋 메시지는 변경 의도와 범위를 드러낸다.

## 15. 자주 쓰는 분석 프롬프트

### 15.1 전체 구조 분석

```text
crypto-project 전체 구조를 분석해줘.
바로 수정하지 말고, 서비스별 역할과 반복되는 패턴을 정리해줘.
```

### 15.2 컨벤션 분석

```text
이 서비스의 코드 컨벤션을 분석해줘.
정적 상수, enum, Lombok, Entity, 테스트, 예외 처리 기준으로 봐줘.
바로 수정하지 말고 후보와 우선순위를 제시해줘.
```

### 15.3 안전한 리팩토링

```text
이 후보를 최소 범위로 리팩토링해줘.
외부 계약은 바꾸지 말고 테스트도 같이 보강해줘.
```

### 15.4 테스트 실패 분석

```text
이 테스트 실패 원인을 분석해줘.
root cause와 최소 수정안을 먼저 말하고, 필요한 경우에만 수정해줘.
```

## 16. 현재 알려진 정리 후보

우선순위 높은 후보:

1. Kafka header constants: `transaction_id`, `dlq_id`, `__TypeId__`
2. gateway/downstream identity header: `X-User-Id`
3. transaction manager name constants
4. `chat`의 retry target exception 구체화
5. REST `GlobalExceptionHandler`가 없는 서비스 정리
6. JUnit4 assertion import 제거
7. `websocket-gateway` RedisKey 인자 검증 강화
8. OAuth2 redirect/path/cookie 관련 properties 정리

바로 수정하면 위험한 후보:

- Kafka topic/header 이름 변경
- Redis key pattern 변경
- gRPC proto 변경
- API response의 `items=null` 정책 변경
- read replica routing semantics 변경
- Outbox/DLQ transaction manager 변경
