#!/bin/bash
INPUT=$(cat)
cd "$CLAUDE_PROJECT_DIR" || exit 1
BRANCH=$(git branch --show-current)
[ "$BRANCH" = "main" ] && exit 0

# 브랜치별 원고. stacked 브랜치를 쌓는 동안 서로 덮어쓰지 않게 한다.
# 파일명에 쓸 수 없는 '/'는 '-'로 치환한다 (refactor/foo -> pr-draft-refactor-foo.md)
DRAFT="$CLAUDE_PROJECT_DIR/.claude/pr-draft-${BRANCH//\//-}.md"

if ! gh pr view "$BRANCH" &>/dev/null; then
  if [ -f "$DRAFT" ]; then
    PR_TITLE=$(sed -n '1p' "$DRAFT")
    # 생성 성공 시에만 원고를 소비한다.
    # (원격 브랜치가 아직 없어 실패하면 원고를 남겨 다음 push에서 재사용)
    if gh pr create --title "$PR_TITLE" \
                    --body-file "$DRAFT" \
                    --base main --head "$BRANCH"; then
      rm "$DRAFT"
    fi
  else
    gh pr create --fill --base main --head "$BRANCH"
  fi
fi

PR_TITLE=$(gh pr view "$BRANCH" --json title -q .title)
PR_NUMBER=$(gh pr view "$BRANCH" --json number -q .number)

# squash 커밋 본문은 비우되, 브랜치 커밋들의 Co-authored-by 트레일러만 살린다.
# (--body "" 로 비우면 공동 저작 표시가 머지 시 사라진다)
CO_AUTHORS=$(gh pr view "$BRANCH" --json commits -q '.commits[].messageBody' \
  | grep -iE '^Co-authored-by:' | sort -u)

gh pr merge "$BRANCH" --auto --squash --delete-branch \
  --subject "$PR_TITLE (#$PR_NUMBER)" \
  --body "$CO_AUTHORS"
