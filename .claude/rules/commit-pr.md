# 커밋 · PR 메시지 규칙

이 파일은 커밋/PR 메시지를 작성할 때 읽는다. squash 머지가 **PR 제목을 커밋 subject로 쓰고 `(#PR)`을 붙이며 커밋 본문은 비운다**(`.claude/hooks/auto-pr.sh`) → 관리 대상은 **제목**과 **PR 본문**이다.

## 제목 (커밋 제목 = PR 제목)

형식: `[<type>] <scope>: <subject>`

### type (브래킷 안)

| type | 용도 |
|---|---|
| `feat` | 새 기능·능력 추가 |
| `fix` | 버그·오류 수정(머지 직후 긴급 후속 수정 포함) |
| `refactor` | 동작 변화 없는 구조/네이밍 정리 |
| `docs` | 문서만 변경 |
| `test` | 테스트만 변경 |
| `chore` | 빌드/의존성/설정 등 잡무 |
| `ci` | GitHub Actions workflow |
| `cd` | 배포 스크립트/파이프라인 |

### scope

- 모듈/영역 **디렉터리명**을 쓴다. **여러 모듈·전역 변경이면 생략**한다.
- 축약 별칭(가독성): `config` = spring-cloud-config, `eureka` = spring-cloud-eureka-server, `gateway` = spring-cloud-api-gateway.
- 그 외는 디렉터리명 그대로: `chat`, `market`, `user`, `notification`, `oauth2-client`, `oauth2-authorization-server`, `websocket-gateway`, `market-detection`, `outbox`, `common`, `git-config-repo`, `docker`, `build` …

### subject

- 한국어, **명사형 종결**("정리/추가/수정"), 마침표 없음, 50자 내외.
- 예:
  - `[docs] config: 모듈 문서 추가`
  - `[refactor] chat: 트랜잭션 경계 정리`
  - `[fix] market: database 추가`
  - `[refactor] 의존성 정리` (전역 → scope 생략)

## 커밋 본문

- 상세는 PR 본문에 쓰고 커밋 본문은 비워둔다(squash 머지가 어차피 비운다).
- 단, **AI(Claude)가 작성한 커밋**은 본문 끝에 공동 저작 트레일러를 넣는다:
  ```
  Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
  ```

## PR 본문 (고정 템플릿)

```markdown
## 배경
왜 바꾸는지 1~2줄

## 변경
- 핵심 변경 bullet

## 검증
- 실행/결과, 또는 '문서만 변경(코드 변경 없음)'
```

- 아주 작은 변경은 각 섹션 한 줄로 축약 가능.
- PR 본문에는 AI 저작 푸터를 넣지 않는다. AI 저작 표시는 **커밋 `Co-authored-by:` 트레일러**로만 남긴다(위 "커밋 본문" 참고). 제목에도 AI 표시를 넣지 않는다(사람 커밋과 동일한 형식 유지).
