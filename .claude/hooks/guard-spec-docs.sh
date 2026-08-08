#!/usr/bin/env bash
# PreToolUse (Edit|Write): 정책 카탈로그는 recorder 소유라 이 저장소에서 고칠 수 없다.
#
# **스펙과 설계 문서는 이제 구현자가 고친다.** 작업하면서 요건이 바뀌는 일이 잦고,
# 바뀐 요건을 코드에만 반영하면 스펙이 세 기능 만에 무의미해지기 때문이다.
# 고칠 때는 변경 이력과 사유를 문서에 함께 남긴다.
#
# 반면 **정책(policies.md)은 recorder가 소유한다.** 정책은 여러 스펙에 걸쳐 있어
# 한 구현자가 바꾸면 다른 스펙의 전제가 조용히 무너진다. 정책을 바꿔야 하면
# ieuri-recorder/problem/에 승격·개정 요청 문서를 남긴다.
#
# 막는 것: 볼트의 policies.md (정책 카탈로그)
# 허용:    spec.md, contract.graphql, problem/SPEC-*.md, 그리고 모든 소스
#
# Exit 2 = block; stderr는 Claude에게 사유로 전달된다.
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

# 정책 카탈로그만 막는다. 파일명으로 판정하므로 볼트 밖의 사본에도 같은 규칙이 걸린다.
if [[ "$base" == "policies.md" ]]; then
  {
    echo "BLOCKED (recorder-owned document): $file_path"
    echo
    echo "  정책 카탈로그는 recorder가 소유한다. 여러 스펙이 같은 정책을 전제하므로,"
    echo "  구현자가 직접 고치면 다른 스펙의 전제가 조용히 무너진다."
    echo
    echo "  대신 승격·개정 요청 문서를 남긴다:"
    echo
    echo "    /Users/junha/git/ieuri/ieuri-recorder/problem/<주제>.md"
    echo
    echo "  무엇을·왜 바꿔야 하는지와 구현이 실제로 확정한 내용을 적는다."
    echo "  (요건이 바뀐 것이라면 스펙은 직접 고쳐도 된다 — 이력과 사유를 함께 남길 것.)"
  } >&2
  exit 2
fi

exit 0
