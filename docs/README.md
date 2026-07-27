# 문서 인덱스 (crypto-project-backend)

이 프로젝트의 문서 지도다. 문서와 코드가 어긋나면 **코드가 기준**이며, 미해결 확인/결정 항목은 [`../TODO.md`](../TODO.md)에서 단일 관리한다.

문서는 3층으로 나뉜다.
- **`docs/`** — 사람이 읽는 전체 구조·흐름·기준(이 폴더).
- **`docs/modules/`** — 모듈별 상세 기준 문서.
- **`<module>/CLAUDE.md`** — 그 모듈 디렉토리에서 작업할 때 자동 로드되는 짧은 작업 규칙. 상세는 대응 모듈 문서를 참조.
- **`.claude/rules/`** — 작업 유형별로 읽는 짧은 규칙.

## 1. 전체 문서 (docs/)

| 문서 | 내용 |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 전체 시스템 구조·모듈 관계·서비스 카탈로그·계약 목록 |
| [SERVICE_FLOWS.md](SERVICE_FLOWS.md) | 주요 요청/이벤트 흐름(로그인·채팅·알림·Outbox 등) |
| [CODE_STYLE.md](CODE_STYLE.md) | 코드 작성/리팩토링 기준(네이밍·DTO·도메인·예외·상수화 등) — 단일 정본 |
| [CI_CD.md](CI_CD.md) | CI(affected 빌드)·CD(배포)·Config 재배포 파이프라인 |
| [SCENARIO_TEST.md](SCENARIO_TEST.md) | 화면 카테고리별 시연/연동 테스트 시나리오 |
| [../README.md](../README.md) | 프로젝트 소개(기획·외부 API·화면 시연·문서 지도) |
| [../TODO.md](../TODO.md) | 미해결 확인/결정 항목 단일 관리처 |
| [../CLAUDE.md](../CLAUDE.md) | 루트 공통 작업 규칙·문서 지도 |

## 2. 모듈 상세 문서 (docs/modules/)

각 행은 **상세 문서**(구조·흐름·계약·확인 필요)와 **작업 지침**(모듈 디렉토리 CLAUDE.md) 쌍이다.

### 실행 서비스 (12)

| 서비스 | 상세 문서 | 작업 지침 | 한 줄 요약 |
|---|---|---|---|
| user | [USER.md](modules/USER.md) | [user/CLAUDE.md](../user/CLAUDE.md) | 계정 소유 서비스. 회원가입/프로필/권한, REST·gRPC `user.v1` |
| oauth2-authorization-server | [OAUTH2_AUTHORIZATION_SERVER.md](modules/OAUTH2_AUTHORIZATION_SERVER.md) | [.../CLAUDE.md](../oauth2-authorization-server/CLAUDE.md) | 내부 OAuth2 AS. token-exchange/refresh, Vault RS256, gRPC `auth.v1` |
| oauth2-client | [OAUTH2_CLIENT.md](modules/OAUTH2_CLIENT.md) | [.../CLAUDE.md](../oauth2-client/CLAUDE.md) | 외부 OIDC 로그인(Google/Kakao) → 내부 토큰 브리지, 로그아웃/재발급 |
| spring-cloud-api-gateway | [API_GATEWAY.md](modules/API_GATEWAY.md) | [.../CLAUDE.md](../spring-cloud-api-gateway/CLAUDE.md) | Reactive 게이트웨이 + JWT Resource Server. 라우팅·CORS·`X-User-Id` 전파 |
| chat | [CHAT.md](modules/CHAT.md) | [chat/CLAUDE.md](../chat/CLAUDE.md) | 채팅방/메시지. 캐시-우선 + Outbox, 영속은 Kafka consumer 비동기, gRPC `chatmessage.v1` |
| websocket-gateway | [WEBSOCKET_GATEWAY.md](modules/WEBSOCKET_GATEWAY.md) | [.../CLAUDE.md](../websocket-gateway/CLAUDE.md) | STOMP 게이트웨이. broadcast 소비 → 로컬 세션 push, 세션 위치 로컬+Redis |
| market | [MARKET.md](modules/MARKET.md) | [market/CLAUDE.md](../market/CLAUDE.md) | 마켓 카탈로그·가격알림 설정. MySQL, gRPC `market.v1`, Caffeine 캐시 무효화 |
| market-detection | [MARKET_DETECTION.md](modules/MARKET_DETECTION.md) | [.../CLAUDE.md](../market-detection/CLAUDE.md) | Upbit WS 수집 + Kafka Streams 변동률 탐지 → `PriceAlertDetectedEvent` |
| notification | [NOTIFICATION.md](modules/NOTIFICATION.md) | [notification/CLAUDE.md](../notification/CLAUDE.md) | 탐지 이벤트 소비 → 수신자 fan-out 저장(Mongo) + web push 발행 |
| outbox-poller | [OUTBOX_POLLER.md](modules/OUTBOX_POLLER.md) | [outbox-poller/CLAUDE.md](../outbox-poller/CLAUDE.md) | Outbox/DLQ 릴레이. dispatchType별 폴링 → Kafka 발행 |
| spring-cloud-config | [SPRING_CLOUD_CONFIG.md](modules/SPRING_CLOUD_CONFIG.md) | [.../CLAUDE.md](../spring-cloud-config/CLAUDE.md) | Config Server(git + Vault), JWKS, Vault Transit 서명 대행 |
| spring-cloud-eureka-server | [EUREKA_SERVER.md](modules/EUREKA_SERVER.md) | [.../CLAUDE.md](../spring-cloud-eureka-server/CLAUDE.md) | 서비스 디스커버리(HTTP `lb://` + gRPC metadata) |

### 공통 모듈

| 대상 | 상세 문서 | 작업 지침 | 한 줄 요약 |
|---|---|---|---|
| common-* | [COMMON.md](modules/COMMON.md) | [common/CLAUDE.md](../common/CLAUDE.md) | 공통 모듈 파사드. Outbox/DLQ·JPA(Read Replica)·Redis·이벤트·ArchUnit 등 |

## 3. 작업 규칙 (.claude/rules/)

작업 유형별로 읽는 짧은 규칙. 상세 배경은 위 `docs/` 문서를 참조한다.

| 규칙 | 언제 |
|---|---|
| [git-safety.md](../.claude/rules/git-safety.md) | 항상 로드(파일 변경·Git·민감정보·Plan Mode 우선) |
| [architecture.md](../.claude/rules/architecture.md) | 계층·의존·트랜잭션·Outbox/DLQ·Redis·예외 |
| [external-contracts.md](../.claude/rules/external-contracts.md) | REST/gRPC/Kafka/Redis/STOMP/JWT/DB 계약 변경 |
| [security.md](../.claude/rules/security.md) | 인증·인가·OAuth2/JWT·Secret |
| [testing.md](../.claude/rules/testing.md) | 테스트 작성·실행·검증 |
| [commit-pr.md](../.claude/rules/commit-pr.md) | 커밋·PR 메시지 작성 |

## 4. 커버리지

실행 서비스 12개 전부 + 공통 모듈(`common-*`)에 대해 **상세 문서 + 작업 지침(CLAUDE.md)** 쌍이 존재한다. 새 서비스를 추가하면 `docs/modules/<NAME>.md` + `<module>/CLAUDE.md`를 만들고 이 인덱스와 [`../CLAUDE.md`](../CLAUDE.md) 문서 지도에 반영한다.
