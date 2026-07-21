#!/usr/bin/env bash
#
# block-sensitive-paths.sh
# PreToolUse hook (matcher: Write|Edit|MultiEdit).
# Blocks writes to credential/secret files and parent-directory traversal.
#
# NOTE: the original lab version also blocked broad personal dirs (/Users/, ~/,
# Desktop, Downloads, Library). This project *lives* under /Users/..., so those
# patterns are intentionally dropped here — otherwise every legit edit is blocked.
#
# Exit 2 = block the write; stderr is shown to Claude as the reason.

set -euo pipefail

command -v jq >/dev/null 2>&1 || exit 0   # degrade gracefully if jq is missing

input="$(cat)"
path="$(echo "$input" | jq -r '
  .tool_input.file_path //
  .tool_input.path //
  .tool_input.notebook_path //
  empty
')"
[ -z "$path" ] && exit 0

# Secrets / private keys / credential stores — never write these.
if echo "$path" | grep -Eq '(^|/)\.env($|[./])|(^|/)\.ssh(/|$)|id_rsa|id_ed25519|\.pem$|\.p12$|\.pfx$|\.keystore$|(^|/)secrets?/'; then
  echo "[secrets-hook] Blocked write to a sensitive/credential path: $path" >&2
  exit 2
fi

# Parent-directory traversal.
if echo "$path" | grep -Eq '\.\./'; then
  echo "[secrets-hook] Blocked parent-directory traversal: $path" >&2
  exit 2
fi

exit 0
