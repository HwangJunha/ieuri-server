---
name: graphql-resolver-review
description: Review ieuri GraphQL resolvers for field-level laziness — does field selection actually change which I/O runs, or is this a REST endpoint behind a GraphQL schema? Checks @DgsData usage, argument placement, per-operation caching, and generated-type misuse. Use when adding or reviewing a query that composes data from more than one source (multiple upstream calls, extra tables, aggregate counts). Reports only.
license: MIT
metadata:
  author: junha
  version: "1.0"
# --- Claude Code extensions (ignored by other agents) ---
allowed-tools: Read, Glob, Grep      # review-only
context: fork                        # isolated subagent; narrow focused pass, keep chat clean
argument-hint: "<resolver path or query name>"
---

# graphql-resolver-review

Checks one thing: **does the client's field selection change what the server actually fetches?**

If a query costs the same I/O whether the client asks for one field or twenty, it is a REST endpoint
wearing a GraphQL schema — the schema is documentation, not a contract the engine can optimize.

Read the repository `CLAUDE.md` ("Don't write REST in GraphQL clothing") for the canonical rule.
Companion to `cqrs-flow-review` (which checks read/write rails); this one checks **resolution granularity**.

## When this applies

Only for queries that **compose data with separable cost**:
- more than one upstream/API call
- extra tables or aggregate counts beyond the root projection
- anything a client might reasonably not want

A query backed by a single projection needs no field resolvers — say so and stop. Splitting a
single-query read into field resolvers is over-engineering (see `simplify-review`).

## Flag these

1. **Eager root** — the root resolver fetches data that only a non-selected field needs.
   *Symptom:* root method calls 3 ports and builds a fully-populated response object.
   *Fix:* move each separable fetch into `@DgsData(parentType = "X", field = "y")`.

2. **Argument hoisted to the root** — the root takes an argument only one field consumes.
   *Symptom:* `thing(id:, category:)` where `category` is used solely to fetch `intro`.
   *Fix:* `intro(category:)` on the field. Callers that skip the field skip the argument.

3. **Whole-response caching under a lazy schema** — one cache key holding the composed response.
   *Symptom:* cache stores the root object with nested lazy fields inside.
   *Why it's wrong:* can't express "body cached, gallery not," so lazy fields either get eagerly
   populated to fill the cache or miss constantly. Key **per operation**.

4. **Cached null treated as a miss** — nullable field re-fetches upstream forever.
   *Symptom:* `cache.get(...) ?: fetch(...)` where the fetch legitimately returns null.
   *Fix:* wrap (`CachedX(value)`) and branch on the wrapper being non-null, not the value.

5. **Generated type as the resolution carrier** — root returns the codegen type.
   *Why it breaks:* DGS codegen makes non-null fields required constructor args, so lazy fields
   can't be left empty; you're forced to fetch them. Root returns the **read DTO**; field resolvers
   read it via `dfe.getSource<ReadDto>()`.

6. **Laziness asserted only by compilation** — no test proves the calls don't happen.
   *Fix:* service-level test with mocked ports verifying call counts (`verify(exactly = 0)`) for unselected
   fields. See `test-strategy`.

7. **DataLoader confusion** — a DataLoader added to "make it lazy," or lazy resolvers added to
   "fix N+1." Different problems: laziness prevents over-fetching *per request*; DataLoaders batch
   *within* a request. A lazy list field returning N items whose children each query can need both.

## Do NOT flag

- Cheap fields already present on the root projection (splitting them adds indirection for nothing).
- Non-null fields that are genuinely part of the body and always needed.
- Absence of DataLoaders where no N+1 has been observed — CLAUDE.md forbids adding them preemptively.

## Output

Findings only; do not rewrite. Per finding: `file:line`, which numbered rule, the concrete cost
(e.g. "`{ id title }` still triggers detailImage2"), and the smallest fix. If the query is
single-source, state that no field resolvers are warranted rather than inventing findings.
