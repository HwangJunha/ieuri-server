---
name: schema-design
description: Design database tables and generate Flyway migration SQL for the ieuri project from requirements, honoring its DDD + hexagonal + CQRS rules (aggregate = consistency boundary, NO cross-domain foreign keys, JPA entity ≠ domain model). Use when the user asks to design a schema, add/change tables, or turn a requirement/spec into a migration. Generates migration files only — never executes DDL against the database.
license: MIT
metadata:
  author: junha
  version: "1.0"
# --- Claude Code extensions (ignored by other agents) ---
allowed-tools: Read, Glob, Grep, Write, Edit   # writes migration .sql files; DB access is read-only via the postgres MCP
model: inherit
argument-hint: "<domain> <requirement or spec path>"
---

# schema-design

Turns a requirement into **table design + Flyway migration SQL** for `ieuri`, under the project's domain rules. Pairs with `domain-slice-generator`: this designs the tables, that builds the JPA entities/mappers on top. They must share the same aggregate boundaries.

Read the repository `CLAUDE.md` first — its DDD/hexagonal/CQRS rules and the Flyway/persistence conventions are authoritative.

## Inputs to collect

1. Domain (e.g. `community`, `commerce`, `auth`).
2. The requirement — from a `problem/*.md` / spec file, a GitHub issue, or stated directly.
3. The aggregate(s) involved and their key fields/invariants.

If a `problem/*.md` or issue is referenced, read it first. Ask once only if the aggregate boundary is genuinely unclear.

## Inspect before designing (read-only)

Use the postgres MCP (read-only) to inspect the CURRENT schema before proposing changes: existing tables, columns, the last applied `flyway_schema_history` version. Never assume an empty DB. The MCP is for reading and, later, verifying — it must not be used to run DDL.

## Design rules (non-negotiable — these mirror CLAUDE.md)

- **Aggregate = consistency & transaction boundary.** One aggregate root + its owned entities form a table group. Model the boundary explicitly; don't sprawl one aggregate across unrelated tables.
- **NO cross-domain foreign keys.** A table in domain `a` must NOT declare a physical FK to a table in domain `b`. Store the other side's id as a plain column and enforce integrity at the application layer. This is what keeps a domain extractable into its own app/DB later. **Within a single domain, FKs are fine and encouraged.**
- **Reference across aggregates by id, not by object.** Even inside a domain, reference another aggregate root by its id column, not a FK into its internals.
- **JPA entity ≠ domain model.** You are designing the persistence schema, which is the `infrastructure/adapter/out/persistence` concern — not the domain model. Design the table for storage clarity; the mapper bridges to the pure domain model.
- **CQRS-aware.** Design the normalized write schema first. For read (QueryDSL projections), add indexes that serve the actual queries; introduce a denormalized read table or view ONLY when a projection demonstrably needs it — not preemptively.
- **Conventions.** snake_case table/column names; explicit PK (prefer a surrogate key unless the domain dictates otherwise); `created_at`/`updated_at` timestamps where the aggregate needs auditing; NOT NULL and constraints that encode real invariants.

## Output — Flyway migration files (never direct DDL)

Generate versioned migration files under `src/main/resources/db/migration/` using **Flyway's default naming**: `V{n}__{snake_case_description}.sql` (e.g. `V1__create_community_post.sql`).

- Determine the next version `n` from the existing migrations / `flyway_schema_history` (never reuse or edit an already-applied version — Flyway checksums them; add a new V file instead).
- One logical change per migration; keep them small and reviewable.
- Include the reverse intent as a comment or a paired approach if the team uses it, but do not edit applied migrations.
- Do NOT set or rely on Hibernate `ddl-auto` to create schema. Schema comes from Flyway only; entities validate against it (`ddl-auto: validate`).

## Workflow this fits

postgres MCP (read current schema) → design tables under the rules above → emit `V{n}__*.sql` → user reviews → user applies via Flyway (app startup runs `migrate`; verify replica caught up) → `domain-slice-generator` builds JPA entities + mappers on the resulting schema.

## Output format

1. One-line restate: domain + what's being designed.
2. The table design: each table with columns, types, keys, constraints, and which aggregate it belongs to — and an explicit note on any cross-domain reference (id column, no FK) and why.
3. The migration file(s) with full path and SQL.
4. A short checklist: indexes added for which queries, and which `domain-slice-generator` slice should follow.

## Guardrails

- Never run DDL through the MCP; emit migration files only.
- Never create a cross-domain physical FK.
- Never edit an already-applied migration; add a new versioned file.
- Never introduce `ddl-auto: update/create`.
