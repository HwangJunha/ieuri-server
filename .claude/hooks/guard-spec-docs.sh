#!/usr/bin/env bash
# PreToolUse (Edit|Write): planner-owned documents are read-only in an implementation repo.
#
# Specs and their per-repo design documents are produced by the planner harness. Editing
# them here makes the spec's change history a fiction: the next /amend merges against a
# document that already moved, and nobody can tell a deliberate reversal from a drift.
#
# Blocked:  problem/SPEC-*.md, and anything inside the Obsidian vault.
# Allowed:  every other file under problem/, and all source.
#
# Exit 2 = block; stderr is surfaced to Claude as the reason.
set -euo pipefail

payload=""
if [[ ! -t 0 ]]; then
  IFS= read -r -d '' -t 5 payload <&0 || true
fi

file_path="$(printf '%s' "$payload" \
  | python3 -c 'import json,sys; print((json.load(sys.stdin).get("tool_input",{}) or {}).get("file_path",""))' \
  2>/dev/null || echo '')"

[[ -z "$file_path" ]] && exit 0

base="$(basename "$file_path")"

block() {
  {
    echo "BLOCKED (planner-owned document): $file_path"
    echo
    echo "$1"
    echo
    echo "  If the spec is wrong, do not edit it and do not work around it in code."
    echo "  Comment on the issue instead:"
    echo
    echo "    [AMEND] SPEC-XXXX: <what you hit and why the spec cannot stand>"
    echo
    echo "  The planner harness folds it back with /amend and records the change."
  } >&2
  exit 2
}

# 1) per-repo design documents produced by the planner
case "$base" in
  SPEC-*.md)
    case "$file_path" in
      */problem/*|problem/*)
        block "  problem/SPEC-*.md is written by the planner from the spec."
        ;;
    esac
    ;;
esac

# 2) the vault itself — specs, contracts, policy catalogue, app registry
if [[ -n "${IEURI_VAULT:-}" ]]; then
  vault="$(cd "$IEURI_VAULT" 2>/dev/null && pwd || echo "")"
  if [[ -n "$vault" ]]; then
    abs="$(cd "$(dirname "$file_path")" 2>/dev/null && pwd || echo "")/$base"
    case "$abs" in
      "$vault"/*)
        block "  The Obsidian vault belongs to the planner and recorder harnesses."
        ;;
    esac
  fi
fi

exit 0
