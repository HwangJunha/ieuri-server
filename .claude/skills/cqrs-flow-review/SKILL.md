---
name: cqrs-flow-review
description: Review a command or query flow in ieuri for correct CQRS separation — command through the domain, query via QueryDSL bypassing the aggregate, response mapping owned by the resolver. Use when reviewing a slice's read/write path or when a handler feels like it's on the wrong side. Narrow companion to architecture-boundary-review. Reports only.
license: MIT
metadata:
  author: junha
  version: "1.0"
# --- Claude Code extensions (ignored by other agents) ---
allowed-tools: Read, Glob, Grep      # review-only
context: fork                        # isolated subagent; narrow focused pass, keep chat clean
argument-hint: "<slice path>"
---

# cqrs-flow-review

Focused check on the **read/write split** for a single flow in `ieuri`. Narrower than `architecture-boundary-review` — use it when the question is specifically "is this command/query on the right rails?"

Read the repository `CLAUDE.md` for the canonical flow definitions.

## Correct flows (the spec)

**Command (write):**
GraphQL input → `Command` object → command handler → **domain aggregate (invariants here)** → `port/out/persistence` → JPA entity via mapper.
- Update: apply changes to the **managed** entity (Hibernate dirty-checking), never `save()` a fresh detached one.
- Create: map aggregate → new entity → persist.

**Query (read):**
GraphQL args → query handler → `port/out/query` → **QueryDSL projection → read-model DTO (aggregate bypassed)** → resolver maps read DTO → GraphQL response.
- **One use-case method per independently-resolvable field.** If a response composes parts with separate cost (extra upstream call, extra table, aggregate count), the inbound port exposes a method per part and `@DgsData` field resolvers call each only when the client selects it — not one fat method that assembles everything eagerly. Resolution granularity itself is `graphql-resolver-review`'s job; here just flag a query service that clearly forces over-fetching.

## Flag these

- Command handler that skips the aggregate and mutates data directly.
- Query handler that **loads an aggregate** to read (defeats CQRS — should project).
- Query returning a domain aggregate/entity instead of a read DTO.
- Command returning a read DTO (mixing sides).
- Response-type mapping done in `application` instead of the resolver.
- Read path importing `port/out/persistence` (should use `port/out/query`), or write path importing `port/out/query`.
- Update path that rebuilds a detached entity and `save()`s it (lost dirty-checking).
- A single fat read DTO reused across unrelated queries (prefer per-query projection).

## Output format

State the flow type detected (command/query), then list deviations as `path — issue — correct rail`. If the flow is clean, say so in one line and note whether a per-query projection or a shared DTO was used.
