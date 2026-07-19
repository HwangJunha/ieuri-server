#!/usr/bin/env bash
#
# block-architecture-violations.sh
# PreToolUse hook (matcher: Write|Edit|MultiEdit).
# Enforces the ieuri architectural contract from CLAUDE.md at write time —
# the deterministic counterpart to the architecture-boundary-review skill.
#
# Exit 2 = block the write. stderr is shown to Claude as the reason.

set -euo pipefail

command -v jq >/dev/null 2>&1 || exit 0   # degrade gracefully if jq is missing

input="$(cat)"

path="$(echo "$input" | jq -r '.tool_input.file_path // empty')"
[ -z "$path" ] && exit 0

# Only police Kotlin source files.
case "$path" in
  *.kt) : ;;
  *) exit 0 ;;
esac

# The content being written: Write.content, Edit.new_string, or MultiEdit.edits[].new_string.
content="$(echo "$input" | jq -r '
  .tool_input.content //
  .tool_input.new_string //
  ([.tool_input.edits[]?.new_string] | join("\n")) //
  empty
')"
[ -z "$content" ] && exit 0

block() { echo "[arch-hook] BLOCKED: $1" >&2; echo "[arch-hook] File: $path" >&2; exit 2; }

# Identify the layer from the path (…/<domain>/<layer>/…).
in_domain_layer=false
in_application_layer=false
in_persistence=false
case "$path" in
  */domain/*)          in_domain_layer=true ;;
  */application/*)     in_application_layer=true ;;
esac
case "$path" in
  */infrastructure/adapter/out/persistence/*) in_persistence=true ;;
esac

# Rule 1 — domain purity: no framework imports in domain/.
if $in_domain_layer; then
  if echo "$content" | grep -Eq 'import[[:space:]]+(jakarta\.persistence|javax\.persistence|com\.netflix\.graphql|com\.netflix\.dgs|graphql\.|org\.springframework|com\.querydsl)'; then
    block "domain/ must stay pure Kotlin — no JPA / DGS / Spring / QueryDSL imports. Move persistence & delivery concerns to infrastructure adapters."
  fi
  if echo "$content" | grep -Eq '@(Entity|Table|Column|MappedSuperclass|Embeddable|DgsComponent|DgsQuery|DgsMutation|DgsData)\b'; then
    block "domain/ must not carry JPA/DGS annotations. Keep the domain model framework-free."
  fi
fi

# Rule 2 — dependency direction: inner layers may not import outer ones.
if $in_domain_layer; then
  if echo "$content" | grep -Eq 'import[[:space:]]+org\.around\.ieuri\.[a-zA-Z0-9_]+\.(application|infrastructure)\.'; then
    block "domain/ may not import from application/ or infrastructure/. Dependencies point inward only."
  fi
fi
if $in_application_layer; then
  if echo "$content" | grep -Eq 'import[[:space:]]+org\.around\.ieuri\.[a-zA-Z0-9_]+\.infrastructure\.'; then
    block "application/ may not import from infrastructure/. Depend on a port/out interface instead."
  fi
  if echo "$content" | grep -Eq 'import[[:space:]]+(com\.netflix\.graphql|com\.netflix\.dgs|graphql\.)'; then
    block "application/ may not import DGS/GraphQL types. Read-DTO -> response mapping belongs in the graphql resolver (adapter/in)."
  fi
fi

# Rule 3 — JPA @Entity only under infrastructure/adapter/out/persistence.
if echo "$content" | grep -Eq '@Entity\b'; then
  if ! $in_persistence; then
    block "@Entity classes are allowed only under infrastructure/adapter/out/persistence/. JPA entities are a persistence detail, never the domain model."
  fi
fi

# Rule 4 — cross-domain isolation: a domain must not import another domain's internals.
# Derive this file's domain segment (the one just before /domain|application|infrastructure/).
self_domain="$(echo "$path" | sed -nE 's#.*/org/around/ieuri/([a-zA-Z0-9_]+)/(domain|application|infrastructure)/.*#\1#p')"
if [ -n "$self_domain" ] && [ "$self_domain" != "common" ]; then
  # Any import of another domain's domain/ or application/ internals.
  offenders="$(echo "$content" \
    | grep -Eo 'import[[:space:]]+org\.around\.ieuri\.[a-zA-Z0-9_]+\.(domain|application)\.[^[:space:]]*' \
    | grep -Ev "org\.around\.ieuri\.${self_domain}\." || true)"
  if [ -n "$offenders" ]; then
    block "cross-domain import of another domain's internals detected. Go through that domain's port/in or a published domain event. Offending: $(echo "$offenders" | head -1)"
  fi
fi

exit 0
