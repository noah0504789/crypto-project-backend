#!/bin/bash
INPUT=$(cat)
cd "$CLAUDE_PROJECT_DIR" || exit 1
BRANCH=$(git branch --show-current)
[ "$BRANCH" = "main" ] && exit 0

if ! gh pr view "$BRANCH" &>/dev/null; then
  if [ -f "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md" ]; then
    PR_TITLE=$(sed -n '1p' "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md")
    gh pr create --title "$PR_TITLE" \
                 --body-file "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md" \
                 --base main --head "$BRANCH"
    rm "$CLAUDE_PROJECT_DIR/.claude/pr-draft.md"
  else
    gh pr create --fill --base main --head "$BRANCH"
  fi
fi

PR_TITLE=$(gh pr view "$BRANCH" --json title -q .title)
PR_NUMBER=$(gh pr view "$BRANCH" --json number -q .number)
gh pr merge "$BRANCH" --auto --squash --delete-branch \
  --subject "$PR_TITLE (#$PR_NUMBER)" \
  --body ""
