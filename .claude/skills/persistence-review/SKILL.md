---
name: persistence-review
description: Review ieuri persistence code — Exposed discipline (DSL only, repository vs query port, minimal write loads, bounded child collections, column-scoped concurrent updates, no bulk SQL duplicating domain rules, table declarations mirroring the migration) and Flyway migration rules. Use when a change touches tables, repositories, query adapters, or migrations. Reports only.
license: MIT
metadata:
  author: junha
  version: "2.0"
allowed-tools: Read, Glob, Grep
context: fork
argument-hint: "<path or diff>"
---

# persistence-review

Read `CLAUDE.md` first. This covers what the sensors cannot see: not *where* persistence code lives
or what it is named (already enforced), but whether it is **written the way this codebase writes
Exposed**.

## Do not re-check these — the sensors already prove them

Exposed and JDBC out of `application`/`domain` · tables declared in `adapter/persistence/table/…Tables.kt`
· `required/` interfaces named `Repository` or `Port` · no repository in a `QueryService` ·
`QueryPort` used only by read-only services · a repository used only by its own aggregate's
services · inbound adapters depending on no `required` port · `copy` on a domain model called only inside `domain/`.

## 1. Repository and query port

- **A repository is the write side's door to its root.** It loads the root, saves what changed, and
  carries the reads a write decision needs (candidates to assign, whether a code exists). A method
  no write service calls does not belong on it.
- **A query port never returns an aggregate root** — a read model (`XxxView`) or plain values. The
  same table read by both sides is expected; a method shared by both is the finding.
- **A write use case returns what changed (ids, codes), not a read model.** A write service that
  assembles a view — or takes the request language to do it — is doing the resolver's job.
- **A read that needs a domain rule** has the port return stored values and a pure domain function
  decide. The rule rewritten in SQL lives twice.

## 2. Write path

- **A write loads only the row its decision needs.** A `findById` that joins children, or loads
  photos/tags "in case", is over-fetch. Children are read by field resolvers keyed by the parent id.
- **An update still calls a save method** — Exposed has no dirty-checking. A domain method whose
  result is dropped changes nothing.
- **After the root decides, the service writes only what changed**, through intention-named
  repository methods called with `?.let`. No transfer type built just to reach the repository.
- **A root holds a child collection only when it is small, bounded, and read whole by a rule.** An
  unbounded set (messages, answers) on a root — or loaded to add one element — is a finding; so is
  any reference across aggregates other than an id.
- **Concurrent writes are column-scoped and conditional**, not read-modify-write: atomic `± 1` for
  counters, `WHERE col IS NULL OR col < :v` for values that only move forward, `UPDATE … RETURNING`
  to learn what this call changed.
- **Never self-invoke `@Transactional`** from another method of the same class — the proxy is
  bypassed and the annotation does nothing. A flow that wraps external calls is its own service
  calling the Modify service's use case.
- A bulk `update`/`deleteWhere` that re-states a rule a domain method owns — the rule now lives
  twice. Per-item through the domain unless volume is proven (see CLAUDE.md, bulk changes).

## 3. Exposed

- **DSL only.** Any Exposed DAO (`Entity`, `IntIdTable` entity classes) is Active Record — the
  persistence model becoming the domain model.
- **Main-DB queries are DSL, including PostGIS and pgvector** — the shared expressions in
  `common/persistence`. `JdbcTemplate` on the main DB, or `exec()`, is a finding: change the
  statement's shape first. The search node (`searchJdbcTemplate`) is the only raw SQL.
- **Generated SQL must not grow with the input.** An `inList` fed from an unbounded collection needs
  a bound (PostgreSQL allows 32,767 parameters per statement) or a different shape.
- `Table` objects mirror the migration exactly — names of FKs, CHECKs, indexes, `databaseGenerated()`
  for DB defaults, `timestampWithTimeZone` for `timestamptz`. **Nothing checks drift at startup**; a
  mismatch surfaces on the first query that touches it, so read the declaration against the
  migration by hand.
- A string enum column is as wide as its longest value — no sensor checks this any more.
- Keyset paging is `a < ? OR (a = ? AND b < ?)`; the comparison order matches the `ORDER BY`.

**Readability beats cleverness here.** A duplicated straightforward query in two adapters is better
than one abstracted builder that neither reader can follow.

## 4. Migrations

- Schema is Flyway's. `V{n}__{snake_case}.sql`. **A migration from a finished slice is immutable**
  (Flyway checksums the whole file) — the fix is a new `V{n+1}`, with `n` taken from history, never
  reused. One written in *this* unmerged slice is still editable; flag a new `V{n+1}` that only
  corrects it. See `CLAUDE.md`, Persistence & migrations, for the local repair that edit needs.
- One logical change per migration. NOT NULL and constraints should encode real invariants.
- **A value the client always sends is NOT NULL in the DB too** — see the required-value rule in
  `CLAUDE.md`. A nullable column is a path that returns wrong data later.
- No physical FK across contexts (the sensors cannot see SQL — check migrations by hand).

## Output

`path:line — which rule — what breaks in practice — the fix`.
If persistence was untouched, say so in one line rather than inventing findings.
