#!/usr/bin/env bash
# .scratch/ markdown issue tracker adapter for Sandcastle.
# Subcommands: list | view <ID> | close <ID>
# ID format: "<feature>/<NN>-<slug>"  e.g. "game-management/01-add-game-crud"
# Open = Status line present but value is not "resolved" (per docs/agents/issue-tracker.md).
set -euo pipefail

SCRATCH_DIR=".scratch"

die() { echo "error: $*" >&2; exit 1; }

# Locate an issue file by ID.
find_issue_file() {
  local id="$1"
  local file="$SCRATCH_DIR/$id.md"
  if [[ -f "$file" ]]; then
    printf '%s' "$file"
    return 0
  fi
  # Fallback: search by basename across features.
  local matches
  matches=$(find "$SCRATCH_DIR" -path "*/issues/$(basename "$id").md" 2>/dev/null | head -1)
  [[ -n "$matches" ]] || die "issue not found: $id"
  printf '%s' "$matches"
}

# Extract Status value from a file (empty string if missing).
# Accepts both "Status:" and markdown-bold "**Status:**" forms.
issue_status() {
  grep -E '^\*?\*?Status:\*?\*?' "$1" 2>/dev/null | head -1 | sed -E 's/^\*?\*?Status:\*?\*?[[:space:]]*//' | tr -d '[:space:]'
}

# Extract title: first markdown heading, else filename.
issue_title() {
  local title
  title=$(grep -E '^# ' "$1" 2>/dev/null | head -1 | sed -E 's/^# //')
  if [[ -z "$title" ]]; then
    title=$(basename "$1" .md)
  fi
  printf '%s' "$title"
}

cmd_list() {
  [[ -d "$SCRATCH_DIR" ]] || die "$SCRATCH_DIR not found (run from repo root)"
  local files status id title body
  # Emit a JSON array of open issues.
  {
    printf '['
    local first=1
    while IFS= read -r file; do
      status=$(issue_status "$file")
      # Skip resolved; everything else counts as open.
      [[ "$status" == "resolved" ]] && continue
      # id = "<feature>/<NN>-<slug>" relative to .scratch/, minus .md
      id=${file#"$SCRATCH_DIR"/}
      id=${id%.md}
      # Only treat files under issues/ as tickets.
      [[ "$id" == */issues/* ]] || continue
      title=$(issue_title "$file")
      body=$(cat "$file")
      if [[ $first -eq 0 ]]; then printf ','; fi
      first=0
      jq -n --arg id "$id" --arg title "$title" --arg body "$body" --arg status "$status" \
        '{id:$id, title:$title, body:$body, status:$status}'
    done < <(find "$SCRATCH_DIR" -path '*/issues/*.md' -type f 2>/dev/null | sort)
    printf ']\n'
  }
}

cmd_view() {
  local id="${1:-}"
  [[ -n "$id" ]] || die "usage: view <ID>"
  cat "$(find_issue_file "$id")"
}

cmd_close() {
  local id="${1:-}"
  [[ -n "$id" ]] || die "usage: close <ID>"
  local file
  file=$(find_issue_file "$id")
  if grep -qE '^\*?\*?Status:\*?\*?' "$file"; then
    # Replace existing Status line in place, preserving markdown bold if present.
    sed -i.bak -E 's/^\*?\*?Status:\*?\*?.*/**Status:** resolved/' "$file" && rm -f "$file.bak"
  else
    # No Status line — insert after title, or prepend.
    if grep -qE '^# ' "$file"; then
      sed -i.bak -E '0,/^# /s//&\n\nStatus: resolved/' "$file" && rm -f "$file.bak"
    else
      printf 'Status: resolved\n\n%s' "$(cat "$file")" > "$file"
    fi
  fi
  echo "closed: $id"
}

case "${1:-}" in
  list)  shift; cmd_list "$@" ;;
  view)  shift; cmd_view "$@" ;;
  close) shift; cmd_close "$@" ;;
  *) die "usage: $0 {list|view <ID>|close <ID>}" ;;
esac
