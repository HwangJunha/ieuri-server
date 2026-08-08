#!/usr/bin/env bash
#
# block-stop-without-tests.sh
# Stop 훅. 마무리 조건을 만족하지 못하면 턴 종료를 막는다.
#
# **작업 중과 완료 단계의 기준이 다르다.**
#   - 작업 중       : 컴파일만 통과하면 된다. 요건이 계속 바뀌는 동안 테스트를 강요하면
#                     그 테스트를 여러 번 다시 쓰게 된다.
#   - 완료 단계     : .claude/finalizing이 있으면 테스트가 통과해야 끝낼 수 있다.
#
# 상태는 run-tests-after-edit-gradle.sh가 .claude/last-test-status에 기록한다
# (COMPILE_OK / PASS / FAIL).
#
# Exit 2 = 종료 차단; stderr가 Claude에게 사유로 전달된다.

set -euo pipefail

# 무한 차단 루프 방지: 이번 턴에 이미 한 번 막았으면 통과시킨다.
if command -v jq >/dev/null 2>&1; then
  input="$(cat)"
  active="$(echo "$input" | jq -r '.stop_hook_active // false')"
  [ "$active" = "true" ] && exit 0
fi

status_file=".claude/last-test-status"

# 완료 단계: 테스트가 통과해야 한다.
if [ -f ".claude/finalizing" ]; then
  if [ ! -f "$status_file" ]; then
    echo "[stop-hook] 완료 단계인데 테스트 기록이 없다. ./gradlew test를 돌려라." >&2
    exit 2
  fi
  if ! grep -q "^PASS" "$status_file"; then
    echo "[stop-hook] 완료 단계인데 테스트가 통과하지 않았다. 실패한 범위를 고쳐라." >&2
    exit 2
  fi
  exit 0
fi

# 작업 중: 컴파일만 확인한다. 기록이 없으면 Kotlin을 건드리지 않은 턴이라 통과시킨다.
[ -f "$status_file" ] || exit 0

if grep -q "^FAIL" "$status_file"; then
  echo "[stop-hook] 컴파일이 깨져 있다. 고친 뒤 마무리해라." >&2
  echo "           (작업 중이므로 테스트는 요구하지 않는다 — 완료 시에만 필요하다.)" >&2
  exit 2
fi

exit 0
