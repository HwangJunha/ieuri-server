---
name: graphql-review
description: Review ieuri GraphQL for the two failures that make it REST in disguise — a new root query/mutation where a field on an existing type belonged, and a root resolver whose field selection doesn't change what it fetches. Also checks N+1 and schema description bloat. Use when adding or reviewing a query, mutation, or schema change. Reports only.
license: MIT
metadata:
  author: junha
  version: "2.0"
allowed-tools: Read, Glob, Grep
context: fork
argument-hint: "<resolver path, query name, or .graphqls>"
---

# graphql-review

We chose GraphQL for flexibility. Two habits throw that away and turn it back into REST.
This skill checks for both, then for N+1 and description bloat.

Read `CLAUDE.md` first.

## 1. A new root where a field belonged

**Before adding any root query or mutation, look for an existing type in the same domain that this
belongs to as a field.** A new root is correct only when nothing existing can carry it.

*Symptom:* `spotDetail` exists, and `spotLikeCount(spotId:)` is added as a sibling root — the client
now makes two round trips for one screen and re-sends an id the server already resolved.
*Fix:* `likeCount` becomes a field on `SpotDetail`, resolved by `@DgsData`.

Ask: **would the client ever call this without the thing it hangs off?** If not, it is a field.
Every new root makes the graph flatter, and a flat graph is a REST API with extra syntax.

## 2. Field selection that changes nothing

If a query costs the same I/O whether the client asks for one field or twenty, the schema is
documentation rather than a contract the engine can act on.

Applies **only** to responses composing parts with separable cost — another upstream call, another
table, an aggregate count. A single-projection query needs no field resolvers; say so and stop.
Splitting a single-source read into field resolvers is over-engineering.

- **Eager root** — root fetches what only a non-selected field needs. → move into `@DgsData`.
- **Argument hoisted to the root** — root takes an argument one field consumes. → put it on the
  field (`intro(category:)`), so callers skipping the field skip the argument.
- **Generated type as the carrier** — codegen makes non-null fields required constructor args, so
  lazy fields can't be left empty. → root returns the read DTO; field resolvers use `getSource`.
- **Whole-response caching** — one key holding the composed response can't express "body cached,
  gallery not". → key per operation. And wrap nullable values so a cached null isn't re-fetched forever.

## 3. N+1

- A list field whose children each query → needs a `DataLoader`.
- Do **not** add DataLoaders where no N+1 has been observed.
- Laziness and DataLoaders solve different problems: laziness avoids over-fetching *per request*,
  DataLoaders batch *within* one. A lazy list of N items whose children query can need both.

## 4. Descriptions are for the client

**Keep:** meaning, unit/format, what null means, pagination default + cap **and what happens past
the cap** (clamp or error), sort guarantees.

**Drop:** cache strategy, upstream operation names or call counts, batch behavior, `SPEC-` ids —
clients cannot act on any of it.

Aim for one or two lines. When shortening, check that facts stated *nowhere else* survive:
null vs empty array, empty-string normalization, who sanitizes HTML. Clients branch on those.

## Output

Findings only. Per finding: `file:line`, which section, the concrete cost
(e.g. "`{ id title }` still triggers detailImage2"), and the smallest fix.
If the query is single-source and correctly rooted, say so rather than inventing findings.
