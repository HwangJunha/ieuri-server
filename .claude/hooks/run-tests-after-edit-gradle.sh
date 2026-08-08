#!/usr/bin/env bash
#
# run-tests-after-edit-gradle.sh
# PostToolUse 훅 (matcher: Write|Edit|MultiEdit).
#
# **완료 단계에서만 테스트를 돌린다.** 작업 중에는 요건이 계속 바뀌어 테스트를 먼저 쓰면
# 그 테스트를 여러 번 다시 쓰게 된다. 그래서 테스트 작성·실행은 사용자가 "작업 완료"라고
# 한 뒤로 미루고, 그때 .claude/finalizing 플래그가 생긴다.
#
# 플래그가 없으면 컴파일만 확인한다 — 테스트를 건너뛰더라도 깨진 코드로 진행하지는 않는다.
# 상태는 .claude/last-test-status에 기록하고 Stop 훅이 그것으로 마무리를 막는다.
set -euo pipefail

command -v jq >/dev/null 2>&1 || exit 0

input="$(cat)"
path="$(echo "$input" | jq -r '.tool_input.file_path // empty')"

# Kotlin 파일 편집에만 반응한다 — 설정·마크다운·리소스는 건너뛴다.
case "$path" in
  *.kt) : ;;
  *) exit 0 ;;
esac

mkdir -p .claude

if [ ! -x "./gradlew" ]; then
  echo "[test-hook] No ./gradlew found. Skipping." >&2
  exit 0
fi

# 완료 단계가 아니면 컴파일만 본다. 테스트는 아직 존재하지 않을 수 있다.
if [ ! -f ".claude/finalizing" ]; then
  echo "[test-hook] 작업 중(finalizing 아님) — 컴파일만 확인한다." >&2
  if ./gradlew compileKotlin compileTestKotlin --quiet; then
    echo "COMPILE_OK $(date -u +"%Y-%m-%dT%H:%M:%SZ")" > .claude/last-test-status
  else
    echo "FAIL $(date -u +"%Y-%m-%dT%H:%M:%SZ")" > .claude/last-test-status
  fi
  exit 0
fi

echo "[test-hook] 완료 단계 — 전체 테스트를 돌린다..." >&2
if ./gradlew test --quiet; then
  echo "PASS $(date -u +"%Y-%m-%dT%H:%M:%SZ")" > .claude/last-test-status
else
  echo "FAIL $(date -u +"%Y-%m-%dT%H:%M:%SZ")" > .claude/last-test-status
fi

# PostToolUse의 종료 코드는 막지 않는다. 게이트는 Stop 훅이 건다.
exit 0
