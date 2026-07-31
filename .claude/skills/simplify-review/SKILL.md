---
name: simplify-review
description: Review ieuri code against the "Simple is Best" principle — flag dead code, needless abstraction, premature generalization, and indirection that doesn't earn its keep, while respecting that the hexagonal structure itself is deliberately complex. Use when the user asks to simplify, clean up, or sanity-check a change for over-engineering. Reports only.
license: MIT
metadata:
  author: junha
  version: "1.0"
# --- Claude Code extensions (ignored by other agents) ---
allowed-tools: Read, Glob, Grep      # review-only
context: fork                        # isolated subagent
argument-hint: "<path or diff>"
---

# simplify-review

Reviews `ieuri` code for accidental complexity — the kind that hurts readability — while **respecting the intentional structural complexity** of hexagonal architecture and the project's deliberate rules.

Read the repository `CLAUDE.md` first. The point is to simplify the *code*, not to fight the *architecture*.

## The line this skill must not cross

"Simple is Best" applies to code (clean, readable, minimal), **not** to structure. The following are deliberate and are NOT to be flagged as complexity:
- Domain ≠ JPA entity separation and its mappers (a chosen trade for purity + consistency).
- port/adapter indirection, CQRS split, per-domain layering.
- Cross-domain isolation through ports/events.

Do not recommend "just use the JPA entity as the domain model" or "merge the layers to save files." Those are settled decisions.

## Flag these (accidental complexity)

- **Force-fitting a pattern the upstream/library/data can't support** — the highest-value flag. Emulating a capability that isn't natively there, then carrying machinery to keep the illusion alive. Tell-tales: an opaque cursor synthesized over an offset-only (`pageNo`) API, with per-request re-fetch/merge/re-sort to fake stability; retry/backfill loops or "supplement to N" logic layered onto a source that doesn't guarantee it; a Relay/connection shape mapped onto live data whose order isn't stable. Ask: does the *upstream* actually provide this? If not, the simpler honest model (e.g. offset `page`/`size`, "return one page, client asks for `page+1`") is almost always right. Worked example: SPEC-0002 `spots` deleted a faked cursor for plain offset paging (−122 net lines). See CLAUDE.md "Simple is Best".
- **Contract-driven overreach** — building elaborate machinery only to satisfy the *shape* of a planner's `contract.graphql` rather than the acceptance criteria. The repo owns the final schema; when the proposed contract implies unsupported machinery, the fix is to change the contract, not to implement the fiction. Flag the machinery; note the contract should change.
- Dead code: unused functions, params, classes, unreachable branches. **Includes config knobs that became orphaned when the code using them was removed** (e.g. paging/merge tuning left behind after switching pagination strategy) — grep the property name; if only the definition/binding remains, remove it from the properties class, the policy, and `application.yaml`.
- Premature generalization: interfaces with one implementation and no extraction plan (distinct from ports, which are intentional); config for cases that don't exist yet.
- Needless indirection: pass-through wrappers, a service that only forwards to another, a mapper that does nothing.
- Over-abstraction: generics/inheritance where a plain function or data class would do.
- Redundant validation duplicated across layers (belongs once — see CLAUDE.md validation rule).
- Clever code where obvious code reads better; deep nesting that a guard clause flattens.
- **Reinvented stdlib/Kotlin/Spring functionality** — hand-rolled parsing/serialization/HTTP a library already does. E.g. reading a JSON tree field-by-field and hand-normalizing when a DTO + the framework's message converter deserializes directly; a custom "is this XML?" sniff or result-wrapper the client already surfaces. Let the library parse; only own the *domain* mapping. Do not write tests for the library's deserialization — test only your own mapping logic.
- Fat read DTOs reused where a small per-query projection is clearer.
- **Defensive branches for unobserved cases** — null checks / fallbacks / normalization for inputs a typed contract already guarantees or that have never been seen in real responses. Keep only filters/guards tied to a real, observed requirement; drop speculative "just in case" defense.

## How to judge

For each candidate, ask: does removing it lose a real capability or invariant? If not, it's accidental complexity — recommend removal. If it protects an invariant or is a deliberate architectural seam, leave it.

## Output format

`path:line — what's over-built — simpler form — what (if anything) is lost`. Group by "safe to remove" vs "consider". End with a one-line read on whether the change is proportionate to the problem it solves.
