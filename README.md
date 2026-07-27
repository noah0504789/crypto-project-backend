# crypto-project-backend

가상화폐 시장의 **급격한 가격 변동을 실시간으로 탐지·알림**하고, 코인별 **오픈채팅**을 제공하는 Spring Cloud 기반 MSA 백엔드.

- 언어/프레임워크: **Java 17 · Spring Boot 3.4.0 · Spring Cloud 2024.0.2**
- 아키텍처: **헥사고날(포트/어댑터) 멀티모듈**, 실행 서비스 **12개**
- 인프라: Eureka(디스커버리) · Spring Cloud Config(+Vault) · gRPC · Kafka(Cloud Stream/Streams) · MySQL · MongoDB · Redis Cluster
- 프론트엔드: [`../crypto-project-frontend`](../crypto-project-frontend) (React 19 · Vite)

---

## 1. 프로젝트 기획

### 기획 의도
- 가상화폐 시장은 **변동성이 크고 이벤트에 민감**하다.
- 급격한 가격 변동이 발생하면 **원인을 빠르게 파악**해야 한다.
- **자산 변동을 실시간으로 추적**하고, **관련 뉴스와 주요 이벤트를 제공**하여 사용자가 빠르게 대응할 수 있도록 지원한다.

### 구현된 핵심 기능
| 도메인 | 내용 |
| --- | --- |
| 가격 알림 | 코인별 변화율 임계값(3%/5%/7%) 설정 → 초과 시 실시간 알림 |
| 실시간 알림 | 탐지 이벤트를 사용자별로 fan-out 저장 + STOMP 웹 푸시 |
| 오픈채팅 | 코인 주제 오픈채팅방 CRUD·실시간 메시지(STOMP, 낙관적 전송) |
| 계정/인증 | OAuth2 소셜 로그인(Google/Kakao), 프로필 관리, JWT |

> 시스템 전체 구조·모듈 관계는 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), 주요 요청/이벤트 흐름은 [`docs/SERVICE_FLOWS.md`](docs/SERVICE_FLOWS.md), 문서 인덱스는 [`docs/README.md`](docs/README.md) 참고.

---

## 2. 외부 API

### 업비트(Upbit) API
- **웹소켓 연결**을 통해 등록된 market에 대해 **실시간 시세(ticker)를 수집**한다.
- 수집 흐름: `market-detection` 서비스가 Upbit WebSocket으로 시세를 받아 Kafka(`upbit-ticker-event`)로 흘리고, **Kafka Streams**로 단기 이동평균 대비 변화율을 계산해 임계값을 넘으면 가격 알림 탐지 이벤트를 발행한다 → `notification` 서비스가 소비해 사용자 알림으로 만든다.
- 근거: `market-detection/.../upbit/*`, 상세 흐름 [`docs/SERVICE_FLOWS.md`](docs/SERVICE_FLOWS.md) §11~14, 모듈 [`docs/modules/MARKET_DETECTION.md`](docs/modules/MARKET_DETECTION.md).

---

## 3. 화면 시연

> ⚠️ **영상 준비 중** — 실제 연동 테스트를 진행하며 페이지(카테고리)별로 녹화해 아래에 추가할 예정.
> 시연 시나리오(사전조건·스텝·기대결과)는 [`docs/SCENARIO_TEST.md`](docs/SCENARIO_TEST.md) 참고.

| 카테고리 | 주요 화면/경로 | 시연 영상 |
| --- | --- | --- |
| 홈 | `/` | 🚧 준비 중 |
| 채팅 | `/chat`, `/chat/my`, `/chat/create`, `/chat/update`, `/chat/room` | 🚧 준비 중 |
| 가격 알림 | `/price-alerts` | 🚧 준비 중 |
| 알림 | Header 벨 드롭다운(실시간 수신 + 인박스) | 🚧 준비 중 |
| 계정 | `/account`, `/account/profile-edit` | 🚧 준비 중 |
| 인증 | 소셜 로그인(Google/Kakao) | 🚧 준비 중 |

---

## 4. 문서

| 문서 | 내용 |
| --- | --- |
| [`docs/README.md`](docs/README.md) | 문서 인덱스(전체 지도) |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 전체 구조·모듈 관계·서비스 카탈로그·계약 |
| [`docs/SERVICE_FLOWS.md`](docs/SERVICE_FLOWS.md) | 주요 요청/이벤트 흐름 |
| [`docs/CODE_STYLE.md`](docs/CODE_STYLE.md) | 코드 작성/리팩토링 기준 |
| [`docs/CI_CD.md`](docs/CI_CD.md) | CI(affected 빌드)·CD(배포) 파이프라인 |
| [`docs/SCENARIO_TEST.md`](docs/SCENARIO_TEST.md) | 화면 카테고리별 시연/연동 테스트 시나리오 |
| [`docs/modules/`](docs/modules/) | 모듈별 상세 문서(서비스 12개 + common) |
| [`TODO.md`](TODO.md) | 미해결 확인/결정 항목 단일 관리처 |

빌드·테스트·CI task는 `docs/CI_CD.md`와 루트 `build.gradle` 참고(예: 서비스별 `./gradlew <service>Ci`).
