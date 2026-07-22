#!/bin/bash
INPUT=$(cat)
BRANCH=$(git branch --show-current)
[ "$BRANCH" = "main" ] && exit 0

if ! gh pr view "$BRANCH" &>/dev/null; then
  if [ -f ".claude/pr-draft.md" ]; then
    # 1번째 줄 = 제목, 3번째 줄부터 = 본문
    PR_TITLE=$(sed -n '1p' .claude/pr-draft.md)
    gh pr create --title "$PR_TITLE" \
                 --body-file .claude/pr-draft.md \
                 --base main --head "$BRANCH"
    rm .claude/pr-draft.md
  else
    gh pr create --fill --base main --head "$BRANCH"
  fi
fi

# 커밋 메시지는 PR 본문 전체가 아니라 "제목만" 쓰도록 명시적으로 지정
PR_TITLE=$(gh pr view "$BRANCH" --json title -q .title)

gh pr merge "$BRANCH" --auto --squash --delete-branch \
  --subject "$PR_TITLE" \
  --body ""