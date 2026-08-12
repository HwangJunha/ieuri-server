---
name: jpa-review
description: Review ieuri persistence code for JPA best practice and query-layer discipline — @OneToMany, fetch strategy, dirty-checking on update, raw SQL containment, derived-query vs QueryDSL choice, and Flyway migration rules. Use when a change touches entities, repositories, query adapters, or migrations. Reports only.
license: MIT
metadata:
  author: junha
  version: "1.0"
allowed-tools: Read, Glob, Grep
context: fork
argument-hint: "<path or diff>"
---

# jpa-review

Read `CLAUDE.md` first. This covers what ArchUnit cannot see: not *where* persistence code lives
(already enforced), but whether it is **written the way JPA wants to be written**.

## 1. Relations

- **`@OneToMany` is not used.** A collection fires a query the moment a mapper touches it, even when
  reading one row, and its size cannot be bounded. Only a unidirectional child-side `@ManyToOne`.
  Reads go through repositories and QueryDSL projections instead.
- **Across aggregates, not even `@ManyToOne`** — reference by id column. Relations are only ever
  considered *within* one aggregate. Today the codebase has **no relations at all**, all id
  references; that is correct, not an omission.
- No physical FK across domains (ArchUnit can't see SQL — check migrations by hand).
- Flag bidirectional relations, `CascadeType.ALL` on a parent, and `orphanRemoval` — all imply an
  owning collection we don't keep.
- Any `FetchType.EAGER`. Default to lazy; a query that needs the other side projects it.

## 2. Write path

- **Update must mutate the managed entity** inside the transaction and let dirty-checking emit the
  `UPDATE`. Flag a fresh detached entity passed to `save()` — dirty-checking is lost and it silently
  becomes an upsert.
- Create maps the aggregate to a new entity and persists it.
- **Never self-invoke `@Transactional`** from another method of the same class — the proxy is
  bypassed and the annotation does nothing. Extract a private helper.
- Bulk `update()`/`delete()` bypass the persistence context. Fine when nothing re-reads those
  entities in the same transaction — **check callers**. DB-level `ON DELETE CASCADE` still fires.

## 3. Query choice

Order of preference: **derived query → QueryDSL → raw SQL**. Never `@Query` — it splits the query
across two places and is neither type-checked nor refactorable.

Raw SQL is justified by exactly two things: **PostGIS spatial functions** (QueryDSL cannot express
`ST_DWithin`/`ST_Distance`) and **`INSERT ... ON CONFLICT`** (JPA has no portable upsert).

- `JdbcTemplate` appears **only** in an adapter whose name says so (`SpotSpatialQueryAdapter`,
  `SpotUpsertJdbcAdapter`), and that adapter has **its own outbound port**. An
  `infrastructure → infrastructure` injection hides the split in the wrong layer.
- A column JPA cannot express (PostGIS `geography`) is **not mapped on the entity at all** — mapping
  it as `String` to sneak past the compiler fails `ddl-auto: validate` at startup. It is also
  invisible to HQL, so any query touching it belongs in the raw-SQL adapter.
- JPQL has no row-value constructor: keyset paging is `a < ? OR (a = ? AND b < ?)`, not `(a,b) < (?,?)`.

**Readability beats cleverness here.** A duplicated straightforward query in two adapters is better
than one abstracted builder that neither reader can follow.

## 4. Migrations

- Schema is Flyway's, never Hibernate's — `ddl-auto: validate` only.
- `V{n}__{snake_case}.sql`; **applied migrations are immutable** (Flyway checksums them). Determine
  the next `n` from history; never reuse or edit.
- One logical change per migration. NOT NULL and constraints should encode real invariants.
- **A value the client always sends is NOT NULL in the DB too** — see the required-value rule in
  `CLAUDE.md`. A nullable column is a path that returns wrong data later.

## Output

`path:line — which rule — what breaks in practice — the fix`.
If persistence was untouched, say so in one line rather than inventing findings.
