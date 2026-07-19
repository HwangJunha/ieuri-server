#!/usr/bin/env bash
#
# run-tests-after-edit-gradle.sh
# PostToolUse hook (matcher: Write|Edit|MultiEdit).
# Kotlin/Gradle replacement for the pytest version — ieuri uses ./gradlew test,
# not python -m pytest. Records PASS/FAIL to .claude/last-test-status so the
# Stop hook (block-stop-without-tests.sh) can gate the final response.
#
# NOTE: a full `./gradlew test` on every edit is heavy (JVM + compile).
# This hook therefore (a) only fires for edits to Kotlin source/test files and
# (b) runs asynchronously is recommended via settings.json ("async": true).
# For a lighter loop, swap `test` for `compileTestKotlin` (compile-only gate).

set -euo pipefail

command -v jq >/dev/null 2>&1 || exit 0

input="$(cat)"
path="$(echo "$input" | jq -r '.tool_input.file_path // empty')"

# Only run for Kotlin file edits — skip config, markdown, resources, etc.
case "$path" in
  *.kt) : ;;
  *) exit 0 ;;
esac

mkdir -p .claude

# No Gradle wrapper -> nothing to do (don't fabricate a status).
if [ ! -x "./gradlew" ]; then
  echo "[test-hook] No ./gradlew found. Skipping." >&2
  exit 0
fi

echo "[test-hook] Running ./gradlew test after Kotlin edit..." >&2
if ./gradlew test --quiet; then
  echo "PASS $(date -u +"%Y-%m-%dT%H:%M:%SZ")" > .claude/last-test-status
else
  echo "FAIL $(date -u +"%Y-%m-%dT%H:%M:%SZ")" > .claude/last-test-status
fi

# PostToolUse exit code does not block; the Stop hook enforces the gate.
exit 0
