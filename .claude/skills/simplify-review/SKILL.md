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

1. **Forced-fit implementation** — the flag that finds the deepest problems. Machinery emulating a capability the
   upstream/library/data does not actually provide. Ask: *does the source support this natively?*
   If not, the honest simpler model is right, and the client is told.
   *Tell-tales:* a cursor synthesized over an offset-only API, with re-fetch/merge to fake stability;
   repeated calls to "fill in" values the contract demands but the source won't give; retry/backfill
   loops layered onto a source with no such guarantee.

2. **Unmapped code** — the check to run first, because it is mechanical rather than a judgment call.
   Build the reverse mapping table below. Anything with no requirement id behind it is either dead
   weight or a requirement nobody wrote down. Both need the user.

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

## Reverse mapping — write this first

| Added | Requirement id |
|---|---|
| RecommendationCursor | (none) |
| SpotLikeCountResolver | FR-003 |

Ids only, never prose. A sentence of justification is something you can invent; an id is something
that exists or does not. Take them from the issue and `problem/SPEC-XXXX-server.md` — a **decision
id** (`D-04`) counts as much as an FR/AC number, since some of a slice exists because of a design
decision rather than a requirement. `(none)` means it traces to neither.

**One row per thing a requirement could plausibly be traced to** — a new root field, resolver,
aggregate, port, entity, migration, batch job, config knob. Not every class: a private helper
extracted from something already mapped inherits its parent's id. A row per symbol makes the table
too long to read, which is the same as not having one.

**`(none)` means ask, not block.** It marks either dead weight or a requirement nobody wrote down,
and you cannot tell which from the diff — that is the user's call.

Cross-check every row against 이번 범위 아님 in `problem/SPEC-XXXX-server.md`. A match there is a
confirmed overreach, not a judgment call.

## Scale check

Compare the implementation against 규모 가정:

- "results capped at 20" + cursor pagination → mismatch
- "100 concurrent users" + distributed lock → mismatch
- "daily batch" + cache layer → mismatch

This is comparison, not judgment. State the pair and the mismatch.

## Output

The mapping table first, then the scale check, then findings:
`path:line — what's over-built — simpler form — what is lost`. Group "safe to remove" vs "consider".
End with one line on whether the change is proportionate to the problem.
