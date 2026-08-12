---
name: simplify-review
description: Review ieuri code for forced-fit implementations and accidental complexity — machinery built to fake a capability the upstream doesn't have, abstractions that don't earn their keep, comments that outlived the code. Use before a PR, or when a change feels bigger than the problem. Reports only.
license: MIT
metadata:
  author: junha
  version: "2.0"
allowed-tools: Read, Glob, Grep
context: fork
argument-hint: "<path or diff>"
---

# simplify-review

Read `CLAUDE.md` first. This skill catches what ArchUnit cannot: code that compiles, passes,
and still has the wrong shape.

## Never flag these — they are deliberate

Domain ↔ JPA entity separation and its mappers. Port/adapter indirection. The CQRS split.
Cross-domain isolation via ports/events. Never recommend collapsing them.

Duplication is also acceptable here. Readability beats line count — a bit of repeated code that
reads straight is better than an abstraction that couples two callers together.

## Flag these

1. **Forced-fit implementation** — the highest-value flag. Machinery emulating a capability the
   upstream/library/data does not actually provide. Ask: *does the source support this natively?*
   If not, the honest simpler model is right, and the client is told.
   *Tell-tales:* a cursor synthesized over an offset-only API, with re-fetch/merge to fake stability;
   repeated calls to "fill in" values the contract demands but the source won't give; retry/backfill
   loops layered onto a source with no such guarantee.

2. **Contract-driven overreach** — building machinery to satisfy the *shape* of a planner's
   `contract.graphql` rather than the acceptance criteria. The repo owns the final schema: change
   the contract, don't implement the fiction.

3. **Dead code** — unused functions, params, branches, and orphaned config knobs. Grep callers
   before rewriting: a "migrate this" task sometimes turns out to be "delete it".

4. **Comments that outlived the code** — a KDoc naming a renamed class, or stating a convention
   since reversed. Worse than no comment; the next reader trusts them.

5. **Over-abstraction** — one-implementation interfaces with no extraction plan (ports excepted),
   pass-through wrappers, generics where a data class would do, config for cases that don't exist.

6. **Reinvented framework behavior** — hand-rolled parsing/serialization/HTTP a library already does.

7. **Defensive branches for unobserved cases** — null checks and fallbacks for inputs a typed
   contract guarantees or that have never been seen. Speculative defense is complexity.

## Judging

Does removing it lose a real capability or invariant? If not, recommend removal.

## Output

`path:line — what's over-built — simpler form — what is lost`. Group "safe to remove" vs "consider".
End with one line on whether the change is proportionate to the problem.
