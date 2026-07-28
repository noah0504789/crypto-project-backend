# 시나리오 테스트 — 프론트 실사용 E2E

배포한 백엔드(API Gateway 단일 진입점)에 프론트(`crypto-project-frontend`)를 붙여, 실제 유저처럼 화면 카테고리별로 검증하는 수동 테스트 문서.

- 근거: 프론트 `docs/PAGES.md`(화면 역할·플로우), `docs/API_CONTRACT.md`(REST/STOMP 계약), `docs/AUTH.md`(인증), 백엔드 각 서비스 컨트롤러.
- 모든 REST/WS는 `GATEWAY_URL`(`VITE_GATEWAY_URL`) 한 곳으로 나간다.
- 표기: **[사전조건] → [스텝] → [기대결과]**. 기대결과에 REST/STOMP 계약(status·payload) 명시.
- 기대결과 앞 `☐`는 수동 검증용 체크박스(통과 시 `☑`로 표기). 표 셀 안이라 GFM 대화형 체크박스 대신 기호를 쓴다.

## 실행 준비 (공통 사전조건)

| 항목 | 확인 |
| --- | --- |
| 백엔드 스택 | Eureka·Config(+Vault)·Gateway·user·chat·market·market-detection·notification·outbox-poller·websocket-gateway 기동. MySQL/MongoDB/Redis Cluster/Kafka 정상 |
| 게이트웨이 | `GATEWAY_URL` 접속 가능(예: `https://localhost:8000`). TLS 인증서 브라우저 신뢰(self-signed면 예외 허용) |
| 프론트 | `.env`의 `VITE_GATEWAY_URL`이 배포 게이트웨이를 가리킴. `npm run dev` 또는 `npm run build && npm run preview` |
| 소셜 로그인 | Google/Kakao OAuth2 redirect URI에 배포 게이트웨이 등록됨 |
| 테스트 계정 | 소셜 계정 2개 이상(방장/일반 멤버, 실시간 송수신 교차 확인용) |
| 관측 | 브라우저 DevTools Network(REST status·헤더)·WS 프레임(STOMP), 백엔드 로그 |

> 실시간(채팅·알림)은 **브라우저 2개(다른 계정)** 를 나란히 띄워 교차 검증한다. 알림 수신은 **로컬 세션이 있는 대상에게만** push되므로 수신자 브라우저가 접속·구독 중이어야 한다.

계약 요약(자주 참조):
- 커서 페이지네이션 응답 `{ items, hasNext }`.
- 검증 실패 응답 `response.data.errors = [{ field, message, code? }]`.
- 인증: access token = `sessionStorage`, refresh = httpOnly 쿠키. 요청 `Authorization: Bearer`, `withCredentials:true`.
- STOMP 핸드셰이크 인증 = URL 쿼리 `?access_token=`(없으면 401). connectHeaders 아님.

---

## 카테고리 1 — 홈 (Home) `/`

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-HOME-01 랜딩 진입(미로그인) | 로그아웃 | 1. 프론트 `/` 접속 | ☐ 안내 문구 랜딩 렌더. 로그인 요구 없음. 테스트 알림 버튼 없음(제거됨). Header "로그인" 버튼 노출 |
| TC-HOME-02 랜딩 진입(로그인) | 로그인 | 1. `/` 접속 | ☐ 동일 랜딩. Header에 벨(🔔)·프로필 드롭다운 노출 |

---

## 카테고리 2 — 채팅 (Chat)

### 2-A 인기 채팅방 `/chat`

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-CHAT-01 인기 목록 조회(미로그인 가능) | 로그아웃 | 1. `/chat` 진입 | ☐ `GET /chat/rooms/popular?limit=10&category=CRYPTO_CURRENCY` 2xx. 방 카드(제목/인기도/설명/멤버수/방장/생성일) 렌더. 상단 "내 채팅방"·"채팅방 생성" 링크 **미노출** |
| TC-CHAT-02 "더 보기" 커서 페이지네이션 | 인기 방 10개 초과 | 1. 목록 하단 "더 보기" 클릭 | ☐ `GET /chat/rooms/popular?...&lastId={마지막방id}&lastPopularity={마지막인기도}`. 다음 페이지 append(중복 없음). `hasNext=false`면 버튼 사라짐 |
| TC-CHAT-03 미로그인 입장 차단 | 로그아웃 | 1. 방 카드 "입장하기" 클릭 | ☐ `alert("로그인이 필요한 서비스입니다.")`. navigate 없음 |
| TC-CHAT-04 로그인 입장 | 로그인 | 1. "입장하기" 클릭 | ☐ `navigate(/chat/room?roomId={id})`. 상단 "내 채팅방"·"채팅방 생성" 링크 노출 |

### 2-B 내 채팅방 `/chat/my`

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-CHAT-05 미로그인 안내 | 로그아웃 | 1. `/chat/my` 진입 | ☐ 로그인 안내 카드. 목록 요청 없음 |
| TC-CHAT-06 내 방 목록 조회 | 로그인, 참여 방 ≥1 | 1. `/chat/my` 진입 | ☐ `GET /chat/rooms/me?limit=10` 2xx. 각 방에 안읽음 뱃지(99+ 처리)·최근 메시지/시각·멤버수·방장(👑). 내 방장 방엔 "수정" 노출 |
| TC-CHAT-07 목록 커서 페이지네이션 | — | 1. "더 보기" | ☐ `GET /chat/rooms/me?...&lastUnreadFlag&lastMsgCreatedAt&lastId` 커서 요청, append |
| TC-CHAT-08 실시간 뱃지 갱신 | 로그인 A(내 방 목록 화면). 다른 브라우저 B가 A가 속한 방에 전송 | 1. B가 메시지 전송<br>2. A 화면 관찰 | ☐ A가 `/user/queue/chat/badge` 이벤트 `{id,lastMsgContent,lastMsgCreatedAt}` 수신 → 해당 방 안읽음 **+1**, 최근 메시지 갱신, **목록 맨 앞으로 이동**. 목록에 없는 방이면 `GET /chat/room/{roomId}/me`로 조회해 prepend |
| TC-CHAT-09 나가기 | — | 1. 방 "나가기"<br>2. confirm 확인 | ☐ `DELETE /chat/room/{roomId}/members` 204 → 목록에서 제거 |
| TC-CHAT-10 조회 실패 처리(목 폴백 없음) | 해당 서비스 일시 오류(또는 게이트웨이 차단) 재현 가능 시 | 1. `/chat/my` 진입 중 조회 실패 | ☐ 빈 목록 + `loadError` "다시 시도" 카드. 목 데이터 안 뜸. "다시 시도"로 재조회 |

### 2-C 방 생성 `/chat/create`

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-CHAT-11 생성 폼 검증 | 로그인 | 1. 제목/설명 공백으로 제출 | ☐ 클라이언트 trim 검증 `alert`. 요청 안 나감 |
| TC-CHAT-12 정상 생성 | — | 1. 제목(≤50)·설명(≤300)·카테고리(가상화폐 고정) 입력<br>2. 제출 | ☐ `POST /chat/room {title,description,category}` **201** → 성공 alert → `navigate(/chat/my)`. 생성된 방이 내 목록에 방장(👑)으로 존재 |

### 2-D 방 수정 `/chat/update`

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-CHAT-13 쿼리 초기값 로드 | 로그인, 내 방장 방 | 1. `/chat/my`에서 "수정" 클릭 | ☐ `/chat/update?roomId&title&description&category`로 이동, 폼에 기존값 채워짐. 카테고리는 `isChatRoomCategory` 가드 통과 |
| TC-CHAT-14 roomId 없음 | — | 1. `/chat/update` 직접(쿼리 없이) 진입 | ☐ "수정할 채팅방 정보가 없습니다" 안내. 제출 불가 |
| TC-CHAT-15 정상 수정 | — | 1. 제목 변경<br>2. 제출 | ☐ `PATCH /chat/room/{roomId}` 변경 필드만 `{title?}` **204** → 성공 alert → `navigate(/chat/my)`에 반영 |
| TC-CHAT-16 비방장 수정 권한(서버 검증) | 방장 아닌 계정으로 `/chat/update?roomId=...` 강제 접근 | 1. 제출 | ☐ 서버가 권한 거부(4xx). 화면은 실패 alert. (화면은 방장 접근 전제, 권한은 서버가 최종 판단) |

### 2-E 실시간 채팅방 `/chat/room?roomId=`

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-CHAT-17 미로그인/무효 roomId | — | 1. 로그아웃 상태 진입<br>2. roomId 무효 진입 | ☐ 로그인 안내 카드 / roomId 무효 안내 + `/chat` 링크 |
| TC-CHAT-18 초기 메시지 로드 + 제목 | 로그인, 유효 roomId | 1. 방 진입 | ☐ `GET /chat/room/{roomId}/messages?limit=10` 2xx(newest-first) → 프론트 `reverse()` 후 오래된→최신 렌더 → 하단 스크롤. `GET /chat/room/{roomId}`로 방 제목·`msgCnt` 표시(하드코딩 아님) |
| TC-CHAT-19 STOMP 연결 | — | 1. 방 진입 후 WS 프레임 관찰 | ☐ `GATEWAY_URL/ws?access_token=...` 핸드셰이크 성공. `/topic/chat/{roomId}`(메시지)·`/user/queue/chat/ack`(ACK) 구독. 연결 점 "연결" |
| TC-CHAT-20 낙관적 전송 성공 | — | 1. 메시지 입력<br>2. 전송 | ☐ 즉시 `pending` 말풍선 → `/msg/chat.send`로 `ChatMessageRequest {clientMessageId, roomId, writerId, content}` 발행 → `/topic/chat/{roomId}` flat 브로드캐스트 `{messageId, roomId, writerId, content, timestamp(epoch ms), clientMessageId}` 수신 → `clientMessageId` 매칭으로 `sent` 치환(중복 아님) |
| TC-CHAT-21 교차 수신(상대 프로필) | A·B 같은 방 | 1. B 전송<br>2. A 화면 관찰 | ☐ A가 브로드캐스트 수신, 하단 근처면 자동 스크롤. 작성자(내가 아님) 닉네임/아바타는 `GET /user/{userId}/profile`로 채움(캐시+dedup). 실패 시 `사용자 {id}` 폴백 |
| TC-CHAT-22 발행/ACK 실패 → 재전송 | — | 1. 전송<br>2. 실패 케이스 관찰: (a)발행 실패 (b)`/user/queue/chat/ack` `success:false` (c)3초 내 ACK/브로드캐스트 없음(타임아웃) | ☐ 해당 말풍선 `failed` + 재전송(↻) 버튼. ↻ 클릭 시 재발행. `success:false`면 `errors.errors=[{code,field,message}]` 확인 |
| TC-CHAT-23 이전 메시지 무한 스크롤 | 방에 10개 초과 메시지 | 1. 상단으로 스크롤(임계 40px) | ☐ `GET /chat/room/{roomId}/messages?...&lastId&lastCreatedAtMillis`(가장 오래된 메시지 커서) → 앞에 prepend + **스크롤 위치 보정**(점프 없음) |
| TC-CHAT-24 읽음 보고 | — | 1. 방을 떠남(언마운트) 또는 탭 닫기(`beforeunload`) | ☐ `PUT /chat/room/{roomId}/activity?lastMsgReadSeq&lastMsgCreatedAtMs` 204(keepalive fetch, 수동 Authorization). `lastMsgReadSeq` = `msgCnt` + 수신 브로드캐스트 수. 이후 `/chat/my` 안읽음 수 반영 |

---

## 카테고리 3 — 가격 알림 (Price Alerts) `/price-alerts`

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-PA-01 미로그인 안내 | — | 1. 로그아웃 상태 `/price-alerts` 진입 | ☐ 로그인 안내 카드. (Header에서 미로그인 클릭 시 `saveRedirectAfterLogin('/price-alerts')` 후 로그인 모달 → TC-AUTH-05 참조) |
| TC-PA-02 초기 병렬 로드 | 로그인 | 1. `/price-alerts` 진입 | ☐ `GET /markets` 200 + `GET /price-alerts/me` 200 **병렬**. 마켓·내 설정으로 폼 구성. `code=marketCode` 매핑. `targetChangeRate`(0.03) → 화면 퍼센트("3") 변환 |
| TC-PA-03 로드 실패 | — | 1. 조회 실패 재현 | ☐ `loadError` 카드 + "다시 시도". 목 폴백 없음 |
| TC-PA-04 마켓 추가 | — | 1. "알림 추가"<br>2. 모달 멀티 선택<br>3. 확인 | ☐ 선택 코인이 폼 카드로 추가(중복 방지). 저장 전이므로 서버 요청 없음 |
| TC-PA-05 카드 편집 | — | 1. on/off 토글<br>2. 변화율 3%/5%/7% 선택<br>3. 삭제 표시 | ☐ `hasUnsavedChanges` true. "처음상태"로 되돌리기 시 저장본 복원 |
| TC-PA-06 변경 없이 저장 | — | 1. 변경 없이 "내 알람 설정하기" | ☐ alert(변경 없음). 요청 안 나감 |
| TC-PA-07 정상 저장(diff) | — | 1. 추가/수정/삭제 섞어 저장 | ☐ `convertFormToRequest`로 `{creates[], updates[], deletes[]}` diff 계산 → `PUT /price-alerts/me` **204**. create/update `{code,enabled,targetChangeRate}`, delete `{code}`. 성공 후 폼/저장본 로컬 동기화(재조회 없음) |
| TC-PA-08 서버 검증 범위 | — | 1. (가능하면) 범위 밖 비율 전송 시도 | ☐ 서버 `0.01 ≤ targetChangeRate ≤ 1.00` 검증. 화면 선택지는 3/5/7%라 정상 범위. 위반 시 4xx + errors |

---

## 카테고리 4 — 알림 (Notification) — 전역 기능(페이지 아님)

수신 검증엔 **가격 알림 트리거**가 필요하다: TC-PA-07로 알림을 켜둔 코인이 설정 변화율을 넘겨야 백엔드가 발행한다(market-detection → notification → Kafka → websocket-gateway).

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-NOTI-01 구독 성립 | 로그인, 앱 접속 유지 | 1. 로그인 직후 WS 프레임 관찰 | ☐ `App`이 STOMP `/user/topic/notification/` 구독(`subscribeWebNotifications`). (user-destination은 `/user` prefix 유의) |
| TC-NOTI-02 실시간 수신 표시 | TC-PA-07로 알림 on, 해당 코인 변화율 초과(실장 트리거 또는 백엔드 테스트 발행) | 1. 조건 충족 대기 | ☐ `/user/topic/notification/`로 `WebNotificationEvent {type,title,body,createdAtMs,link,data?}` 수신 → `notifications` **맨 앞** 추가 → Header 벨에 빨간 점(안읽음). `title`/`body`는 서버 완성값 그대로 |
| TC-NOTI-03 드롭다운 읽음/이동 | — | 1. 벨 클릭<br>2. 항목 클릭 | ☐ `handleReadNotification(id)`로 `read:true`, 안읽음 점 사라짐. `link` 있으면 드롭다운 닫고 `navigate(link)`. 바깥 클릭 시 닫힘 |
| TC-NOTI-04 세션/로그아웃 초기화 | — | 1. 로그아웃 또는 세션 만료 | ☐ STOMP `deactivate`, `setNotifications([])` |
| TC-NOTI-05 비영속 한계(설계 확인) | — | 1. 알림 받은 뒤 새로고침 | ☐ 알림 **사라짐**(메모리 전용, 서버 read 저장 없음). 버그 아님 — 개선 여지(TODO 항목으로 기록) |

---

## 카테고리 5 — 계정 (Account)

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-ACC-01 셸 리다이렉트 | 로그인 | 1. `/account` 진입 | ☐ index 라우트가 `/account/profile-edit`로 리다이렉트. 좌측 `SideNavigation` + 우측 Outlet |
| TC-ACC-02 미로그인 안내 | — | 1. 로그아웃 상태 `/account` 진입 | ☐ 로그인 안내 카드 |
| TC-ACC-03 프로필 조회 | — | 1. `/account/profile-edit` 진입 | ☐ 닉네임(수정 가능)·이메일(읽기전용). 값은 `App`의 `user`(앱 시작 시 `GET /user/me`로 복원됨) |
| TC-ACC-04 닉네임 검증 | — | 1. 1자/21자 또는 기존값과 동일 입력 | ☐ 제출 버튼 비활성(2~20자 & 기존값과 달라야 `canSubmit`) |
| TC-ACC-05 정상 수정 | — | 1. 유효 닉네임 입력<br>2. 제출 | ☐ `PATCH /user/me {nickname}` → `onUserUpdated`로 `App.setUser` 전역 반영(재조회 없음) → 성공 alert. Header 프로필 표시 즉시 갱신 |
| TC-ACC-06 수정 실패 | — | 1. 서버 오류/중복 닉네임 등 유발 | ☐ 실패 alert. 전역 user 미변경 |

---

## 카테고리 6 — 인증 (Auth)

| TC | 사전조건 | 스텝 | 기대결과 |
| --- | --- | --- | --- |
| TC-AUTH-01 소셜 로그인(구글/카카오) | 로그아웃 | 1. Header "로그인" → LoginModal<br>2. 구글(또는 카카오) 클릭 | ☐ `<a href>`로 `GATEWAY_URL/oauth2/authorization/{provider}` 이동 → 소셜 인증 → 백엔드가 `/login-success?accessToken=...`로 리다이렉트 |
| TC-AUTH-02 로그인 착지 처리 | — | 1. `/login-success?accessToken=...` 도달 | ☐ "로그인 처리 중..." 잠깐 → `setAccessToken`(sessionStorage) → `consumeRedirectAfterLogin()`로 원경로(없으면 `/`) 이동. 이후 `App`이 `GET /user/me`로 `user` 복원 |
| TC-AUTH-03 accessToken 누락 | — | 1. `/login-success`(쿼리 없이) 진입 | ☐ alert + `navigate(/)` |
| TC-AUTH-04 새로고침 세션 유지 | 로그인 상태 | 1. F5 새로고침 | ☐ 토큰 있으면 로딩 게이트(`isInitializingUser`) 동안 `.app-loading` → `GET /user/me` 복원 → 로그인 UI. "로그인 안내" 깜빡임 없음 |
| TC-AUTH-05 로그인 필요 액션 후 복귀 | 로그아웃 | 1. Header "가격 알림" 클릭<br>2. 로그인 모달<br>3. 로그인 | ☐ `saveRedirectAfterLogin('/price-alerts')` 저장 → 로그인 후 `/price-alerts`로 복귀 |
| TC-AUTH-06 401 자동 재발급(single-flight) | access token 만료(수동 훼손 또는 TTL 경과), refresh 쿠키 유효 | 1. 인증 필요 API 유발(예: `/chat/my` 조회) | ☐ 401 → `POST /auth/refresh`(빈 body, `withCredentials`) **201** + `Authorization: Bearer` 헤더에서 새 토큰 추출 → 원요청 1회 재시도 성공. 동시 401은 하나로 합쳐 재발급 1회(`_retry`로 무한 재시도 방지) |
| TC-AUTH-07 재발급 실패 → 세션 만료 | refresh 쿠키도 무효/만료 | 1. 인증 API 유발 | ☐ `/auth/refresh` 실패 → `removeAccessToken` + `saveRedirectAfterLogin(현재경로)` + `AUTH_SESSION_EXPIRED_EVENT` 발행 → `App`이 `setUser(null)` + 알림 초기화 + 로그인 모달 오픈 |
| TC-AUTH-08 로그아웃 | — | 1. 프로필 드롭다운 "로그아웃" | ☐ `POST /auth/logout`(Bearer) 시도 → finally에서 `removeAccessToken`·`setUser(null)`·`setNotifications([])`·`navigate('/', replace)`. 서버 실패해도 클라 상태 정리됨 |
| TC-AUTH-09 탭 종료 후 재방문 | — | 1. 탭 닫고 다시 접속 | ☐ `sessionStorage` access token 소멸 → 첫 인증 API 401 → refresh 쿠키 살아있으면 재발급으로 복구(TC-AUTH-06 경로) |
| TC-AUTH-10 STOMP 토큰 만료(개선 여지 확인) | — | 1. 연결 시점 토큰 만료 상태로 WS 연결 시도 | ☐ 핸드셰이크 401 → STOMP는 axios식 자동 재발급 없음 → `reconnectDelay:5000` 재연결 반복만. (버그 아님, 개선 여지로 기록) |

---

## 카테고리 7 — 전역 공용 컴포넌트

| TC | 스텝 | 기대결과 |
| --- | --- | --- |
| TC-GLB-01 Header 상태 전환 | 1. 로그아웃↔로그인 전환 | ☐ 미로그인="로그인" 버튼. 로그인=벨(알림)·프로필 드롭다운(계정/로그아웃). 바깥 클릭 시 드롭다운 닫힘 |
| TC-GLB-02 Header 네비 | 1. 로고/채팅/가격알림 클릭 | ☐ 각 라우트 이동. 미로그인 "가격 알림"은 TC-AUTH-05 흐름 |
| TC-GLB-03 LoadingButton | 1. 제출/나가기 등 비동기 액션 중 관찰 | ☐ `isLoading` 동안 스피너+문구, 자동 disabled(중복 제출 방지) |
| TC-GLB-04 SideNavigation | 1. `/account` 하위에서 좌측 메뉴 접기/펼치기 | ☐ 재귀 트리 정상 토글, 현재 라우트 강조 |
| TC-GLB-05 Footer | 1. 이용약관/개인정보/문의 링크 | ☐ 정적 링크 렌더. 대상 페이지 미구현(깨진 이동은 알려진 상태) |

---

## 크로스 카테고리 / 회귀 시나리오

| TC | 흐름 | 기대결과 |
| --- | --- | --- |
| TC-E2E-01 신규 유저 온보딩 풀 플로우 | 1. 로그인<br>2. `/chat` 인기 목록<br>3. 방 생성(`/chat/create`)<br>4. `/chat/room` 진입·메시지 전송<br>5. `/price-alerts` 알림 설정 저장<br>6. 조건 트리거 시 벨 알림 수신<br>7. 프로필 닉네임 수정<br>8. 로그아웃 | ☐ 각 단계 계약대로 동작, 전역 상태(user/notifications) 일관 |
| TC-E2E-02 실시간 2인 채팅 교차 | 1. A·B 같은 방<br>2. 양방향 전송 | ☐ 각자 낙관적/수신 치환 정상, 상대 프로필 표시, `/chat/my` 뱃지 갱신 |
| TC-E2E-03 세션 만료 중 작업 복구 | 1. 채팅방에서 토큰 만료 유발<br>2. 401 재발급(TC-AUTH-06) 후 진행 유지<br>3. refresh도 만료면 세션 만료 모달(TC-AUTH-07)<br>4. 재로그인 후 원경로 복귀 | ☐ 재발급으로 작업 연속성 유지, 세션 만료 시 안전한 재로그인 복귀 |

---

## 관찰 체크리스트(각 REST 스텝 공통)
- ☐ Network status가 계약과 일치(201/204/200 구분).
- ☐ 요청 헤더 `Authorization: Bearer` 존재(비인증 API 제외), `withCredentials`로 쿠키 동반.
- ☐ CORS: 응답에 `Access-Control-Allow-Credentials: true`, refresh 응답에 `Set-Cookie` + `Access-Control-Expose-Headers: Authorization`.
- ☐ STOMP: 핸드셰이크 URL에 `access_token` 쿼리, 구독/발행 destination이 계약과 일치.
- ☐ 실패 응답 body `errors[]` 형식 유지.

## 미결/확인 필요(테스트 중 발견 시 여기 기록)
- 알림 비영속(TC-NOTI-05)·STOMP 토큰 만료 재발급 부재(TC-AUTH-10)는 알려진 개선 여지 — 버그로 리포트하지 말 것.
- 로그인 성공 redirect에서 access token을 `?accessToken=` 쿼리로 전달(백엔드 `security.md` "확인 필요") — 노출 경로 점검 시 참고.
