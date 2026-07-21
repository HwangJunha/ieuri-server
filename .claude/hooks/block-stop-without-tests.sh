#!/usr/bin/env bash
#
# block-stop-without-tests.sh
# Stop hook. Refuses to let Claude finalize a turn while tests are failing/unrun.
# Pairs with run-tests-after-edit-gradle.sh, which records PASS/FAIL to
# .claude/last-test-status after each Kotlin edit.
#
# Exit 2 = block the stop; stderr is shown to Claude as the reason.

set -euo pipefail

# Avoid an infinite block loop: if we already blocked once this turn
# (stop_hook_active), allow the stop through. Degrade gracefully without jq.
if command -v jq >/dev/null 2>&1; then
  input="$(cat)"
  active="$(echo "$input" | jq -r '.stop_hook_active // false')"
  [ "$active" = "true" ] && exit 0
fi

status_file=".claude/last-test-status"

if [ ! -f "$status_file" ]; then
  echo "[stop-hook] No test status found. Run ./gradlew test before finalizing." >&2
  exit 2
fi

if ! grep -q "^PASS" "$status_file"; then
  echo "[stop-hook] Tests are not passing. Fix the failing scope before finalizing." >&2
  exit 2
fi

exit 0
