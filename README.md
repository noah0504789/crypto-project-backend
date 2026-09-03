# crypto-project-backend

가상화폐 시장의 **급격한 가격 변동을 실시간으로 탐지·알림**하고, 코인별 **오픈채팅**을 제공하는 Spring Cloud 기반 MSA 백엔드.

- 언어/프레임워크: **Java 17 · Spring Boot 3.4.0 · Spring Cloud 2024.0.2**
- 아키텍처: **헥사고날(포트/어댑터) 멀티모듈**, 실행 서비스 **13개**
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

외부 시스템 연동과 Upbit 시세 수집 흐름은 [`docs/EXTERNAL_APIS.md`](docs/EXTERNAL_APIS.md)에서 확인할 수 있습니다.

---

## 3. 기능 시연

> 실제 서비스 환경에서 주요 기능의 정상 처리 및 예외 처리 과정을 시연합니다.  
> 전체 테스트 절차와 기대 결과는 [`docs/SCENARIO_TEST.md`](docs/SCENARIO_TEST.md)에서 확인할 수 있습니다.

| 기능 | 시연 내용 |
| --- | --- |
| OAuth2 소셜 로그인 | Google·Kakao OAuth2 인증, JWT 발급, 로그인 완료 후 서비스 이동 |
| 프로필 수정 | 닉네임 변경 성공 및 중복 닉네임 검증·실패 처리 |
| 채팅방 관리 | 채팅방 목록·상세 조회, 생성·수정·삭제 |
| 채팅 메시지 | STOMP 실시간 송수신, 최신 메시지·읽지 않은 메시지 갱신, 커서 기반 과거 메시지 조회 |
| 가격 알림 및 실시간 알림 | 가격 알림 설정, Upbit 시세 탐지, 사용자별 알림 저장 및 STOMP 전달 |

### 시연 영상

#### OAuth2 소셜 로그인

Google 및 Kakao OAuth2 인증을 통한 로그인 과정을 시연합니다.

##### Google 로그인

- Google 계정을 통한 OAuth2 인증
- JWT 발급 및 로그인 처리
- 인증 완료 후 서비스 화면 이동

<details>
<summary>🎬 Google 로그인 시연 영상 보기</summary>

<br>

https://github.com/user-attachments/assets/8d53af31-2331-4a93-9a62-aafbd23cc6d9

</details>

##### Kakao 로그인

- Kakao 계정을 통한 OAuth2 인증
- JWT 발급 및 로그인 처리
- 인증 완료 후 서비스 화면 이동

<details>
<summary>🎬 Kakao 로그인 시연 영상 보기</summary>

<br>

https://github.com/user-attachments/assets/5fad85c7-f7a3-4d3d-8de4-d9ab03a32702

</details>

---

#### 프로필 수정

사용자 프로필의 닉네임 변경과 중복 검증 과정을 시연합니다.

##### 프로필 수정 성공

- 사용 가능한 닉네임으로 프로필 수정
- 변경된 사용자 정보 반영 확인

<details>
<summary>🎬 프로필 수정 성공 시연 영상 보기</summary>

<br>

https://github.com/user-attachments/assets/a5b7c911-9e04-443b-a097-49dfc8b1438e

</details>

##### 프로필 수정 실패 — 닉네임 중복

- 이미 사용 중인 닉네임으로 변경 요청
- 닉네임 중복 오류 메시지 표시
- 프로필 변경 요청 거절 확인

<details>
<summary>🎬 닉네임 중복 검증 시연 영상 보기</summary>

<br>

https://github.com/user-attachments/assets/c65041d4-5a7c-4688-ab9b-bcd6b5617dd2

</details>

---

#### 채팅방 관리

오픈채팅방의 조회·생성·수정·삭제 과정을 시연합니다.

- 채팅방 목록 및 상세 정보 조회
- 새로운 채팅방 생성
- 채팅방 정보 수정
- 채팅방 삭제

<details>
<summary>🎬 채팅방 관리 시연 영상 보기</summary>

<br>

https://github.com/user-attachments/assets/e37228e6-87f7-41bc-8db9-d7c36c7c389e

</details>

---

#### 채팅 메시지

실시간 메시지 송수신, 채팅방별 최신 메시지 및 읽지 않은 메시지 정보 갱신, 커서 기반 과거 메시지 조회 과정을 시연합니다.

* STOMP 기반 실시간 메시지 송수신
* 내 채팅방 목록에서 채팅방별 최신 메시지 실시간 갱신
* 읽지 않은 메시지 수신 시 채팅방별 뱃지 정보 실시간 갱신
* 채팅방 입장 및 메시지 확인에 따른 읽지 않은 메시지 상태 변경
* 위로 스크롤할 때 커서 기반으로 이전 채팅 메시지 추가 조회

<details>
<summary>🎬 채팅 메시지 시연 영상 보기</summary>

<br>

https://github.com/user-attachments/assets/3d19a575-a2f5-4500-87e9-ac5ddec08ad4

</details>

---

#### 가격 알림 및 실시간 알림

코인별 가격 알림 조건을 설정하고, 가격 변동 탐지 후 사용자에게 알림이 전달되는 전체 과정을 시연합니다.

- 코인별 가격 변화율 임계값 설정
- Upbit WebSocket을 통한 실시간 시세 수집
- Kafka Streams 기반 가격 변화율 분석
- 설정한 임계값을 초과한 가격 변동 탐지
- 탐지 이벤트의 사용자별 알림 저장
- STOMP 기반 실시간 알림 수신
- Header 알림 뱃지 및 알림 목록 반영

<details>
<summary>🎬 가격 알림 및 실시간 알림 시연 영상 보기</summary>

<br>

https://github.com/user-attachments/assets/441ca20c-0091-4350-bf14-3548cd3c5ad0

</details>

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
| [`docs/modules/`](docs/modules/) | 모듈별 상세 문서(서비스 13개 + common) |
| [`TODO.md`](TODO.md) | 미해결 확인/결정 항목 단일 관리처 |

빌드·테스트·CI task는 `docs/CI_CD.md`와 루트 `build.gradle` 참고(예: 서비스별 `./gradlew <service>Ci`).
