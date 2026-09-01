# 원본 로그 (2026-09-01)

#281 뱃지 flush 타이머와 #282 뱃지 `brokerChannel` 우회가 적용된 단일 게이트웨이를
VU 100 · 방 멤버 302명 조건으로 측정한 원본이다. 시각은 KST다.

보존 회차는 `run-final-vu100-msg60-20260901-155456` 하나다. Docker Desktop 재시작 후
웜업을 거쳐 실행했고 JFR은 켜지 않았다. 재시작 전 회차와 JFR을 켠 회차는 호스트 상태와
계측 조건이 달라 근거에서 제외했다.

no-JFR은 계측 누락이 아니라 의도적인 조건이다. JFR을 켠 VU 100 회차에서는 recorder의
메모리·파일 기록까지 제한된 개발 호스트 자원을 함께 사용해 swap thrashing이 커졌고, 서비스
경합과 계측 경합을 분리하기 어려웠다. JFR 회차는 병목의 스택·락·GC 원인을 탐색하는 데 쓰고,
이 최종 회차는 그 영향을 제외한 상태에서 서버 카운터와 정확성을 검증하는 데 썼다.

이 회차는 운영 SLO·최대 처리량·지연 분포를 확정하지 않는다. 개발계에서 부하로 소프트웨어
병목을 드러내고 코드 수정 후 원인 지표가 제거됐는지 확인하는 것이 목적이다.

## 파일

| 파일 | 내용 |
|---|---|
| `*.txt` | k6 표준 출력과 최종 정확성·지연 요약 |
| `*.meta` | 시작·종료 시각, 컨테이너, 이미지 digest, 회차 중 swapin |
| `*.metrics` | 실행 직전·직후 애플리케이션 메트릭 스냅샷 |
| `*.metrics-summary.txt` | 카운터 증가량, 큐 최댓값, flush 타이머 요약 |
| `*.metrics-{before,after}-{gateway,chat}.prom` | 두 서비스의 Prometheus exposition 원문 |
| `*.prometheus.json` | 실행 구간의 5초 간격 Prometheus range query 응답 |

보존 시 k6 ANSI 색상 코드와 줄 끝 공백만 제거했다. 메트릭 이름·label·값과 로그 내용은
변경하지 않았다.

`*.metrics-summary.txt`는 증가량이 0인 계열을 생략한다. 따라서 거절·오버플로는
`*.metrics-{before,after}-gateway.prom`의 동일 label 조합을 빼서 0임을 확인한다.

## 해석 주의

- 이 회차의 swapin은 8,213MB다. 클라이언트 지연값으로 #282의 효과를 판정하지 않는다.
- 배포된 #282 바이너리는 로컬 세션이 없는 202명을 `chat_badge_direct_failed_total`에
  포함한다. 그래서 증가량 22,220은 실제 실패가 아니라 `110 flush × 202 offline`이다.
- 같은 PR에 포함된 후속 수정은 이를 `chat_badge_direct_skipped_total`로 분리하고,
  로컬 세션의 구독 누락·outbound 오류만 `failed`로 센다.
- #282의 판정 근거는 서버가 직접 센 flush·direct sent·broker 완료 태스크·거절·큐 지표와
  k6가 대조한 전송/수신 총량이다.
