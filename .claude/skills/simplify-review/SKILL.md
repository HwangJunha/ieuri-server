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

- Dead code: unused functions, params, classes, unreachable branches.
- Premature generalization: interfaces with one implementation and no extraction plan (distinct from ports, which are intentional); config for cases that don't exist yet.
- Needless indirection: pass-through wrappers, a service that only forwards to another, a mapper that does nothing.
- Over-abstraction: generics/inheritance where a plain function or data class would do.
- Redundant validation duplicated across layers (belongs once — see CLAUDE.md validation rule).
- Clever code where obvious code reads better; deep nesting that a guard clause flattens.
- Reinvented stdlib/Kotlin/Spring functionality.
- Fat read DTOs reused where a small per-query projection is clearer.

## How to judge

For each candidate, ask: does removing it lose a real capability or invariant? If not, it's accidental complexity — recommend removal. If it protects an invariant or is a deliberate architectural seam, leave it.

## Output format

`path:line — what's over-built — simpler form — what (if anything) is lost`. Group by "safe to remove" vs "consider". End with a one-line read on whether the change is proportionate to the problem it solves.
