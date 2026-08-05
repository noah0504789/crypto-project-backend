#!/bin/bash
INPUT=$(cat)
cd "$CLAUDE_PROJECT_DIR" || exit 1
BRANCH=$(git branch --show-current)
[ "$BRANCH" = "main" ] && exit 0

if ! gh pr view "$BRANCH" &>/dev/null; then
  if [ -f "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md" ]; then
    PR_TITLE=$(sed -n '1p' "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md")
    # 생성 성공 시에만 원고를 소비한다.
    # (원격 브랜치가 아직 없어 실패하면 원고를 남겨 다음 push에서 재사용)
    if gh pr create --title "$PR_TITLE" \
                    --body-file "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md" \
                    --base main --head "$BRANCH"; then
      rm "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md"
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
