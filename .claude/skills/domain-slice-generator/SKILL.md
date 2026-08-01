---
name: domain-slice-generator
description: Generate a complete vertical slice for one use case in the ieuri codebase, following its DDD + hexagonal + CQRS conventions. Use when the user asks to add a new command or query (create/update/delete/get/list) to a domain, scaffold a use case, or wire a new feature end to end. NOT for cross-cutting infra, build config, or one-off edits.
license: MIT
metadata:
  author: junha
  version: "1.0"
# --- Claude Code extensions (ignored by other agents) ---
allowed-tools: Read, Glob, Grep, Write, Edit   # this skill WRITES files (it scaffolds a slice)
disable-model-invocation: true                 # writes many files → trigger deliberately via /domain-slice-generator, not auto
argument-hint: "<domain> <command|query> <UseCaseName>"
---

# domain-slice-generator

Scaffolds one **vertical slice** (a single use case) across all hexagonal layers for `ieuri`, so a feature is wired end to end in one pass instead of hand-creating 6–8 files.

Read the repository `CLAUDE.md` first. It is the source of truth; this skill assumes those rules and never contradicts them. If a rule here disagrees with `CLAUDE.md`, follow `CLAUDE.md`.

## Non-negotiables (inherited from CLAUDE.md)

- Package root `org.around.ieuri`, organized **by domain first, then hexagonal layer**.
- Dependencies point inward only: `infrastructure → application → domain`.
- `domain/` is pure Kotlin: no `jakarta.persistence`, no `com.netflix.graphql`/DGS, no Spring imports.
- **JPA entity ≠ domain model, always separated (every domain, no exception).**
- Write input is a `Command`; "DTO" is reserved for the read side.
- Command path goes through the domain; query path bypasses it via QueryDSL projection.

## Inputs to collect before generating

1. Domain name (e.g. `community`, `commerce`, `auth`).
2. Slice type: **command** (write) or **query** (read).
3. Use-case name (e.g. `CreatePost`, `PlaceBid`, `GetPostDetail`, `ListPosts`).
4. Aggregate involved and the fields that matter for this slice.

If any is missing, ask once, then generate.

## Command slice — files to generate

For use case `Xxx` in domain `d`:

1. `d/application/command/XxxCommand.kt` — immutable input object (validated shape).
2. `d/application/port/in/command/XxxUseCase.kt` — interface `fun handle(command: XxxCommand): <Result>`.
3. `d/application/command/XxxHandler.kt` — application handler implementing the port; orchestrates load → domain mutation → save. Depends only on `domain` + `port/out`.
4. `d/domain/aggregate/` — add/extend the aggregate root method that enforces the invariant. Pure Kotlin.
5. `d/application/port/out/persistence/XxxRepositoryPort.kt` — `load`/`save` contract in domain terms (no JPA types).
6. `d/infrastructure/adapter/out/persistence/` — JPA `@Entity`, Spring Data repository, and a **mapper** (domain ↔ entity). Implements the port.
7. `d/infrastructure/adapter/in/graphql/` — DGS `@DgsMutation` resolver that maps GraphQL input → `XxxCommand`, calls the use case, maps result → response type.

### Update vs create rule (dirty-checking)

- **Update:** in the persistence adapter, inside the transaction, load the **managed** entity, apply the domain's changes onto it, and let Hibernate dirty-checking emit the `UPDATE`. Do NOT build a fresh detached entity and `save()` it.
- **Create:** map the domain aggregate to a new entity and persist it.

## Query slice — files to generate

For query `Xxx`:

1. `d/application/query/XxxResult.kt` (or `...Dto.kt`) — read-model DTO (flat, tailored to this query).
2. `d/application/port/in/query/XxxQueryUseCase.kt` — interface returning the read DTO.
3. `d/application/query/XxxQueryHandler.kt` — implements the port; calls the outbound query port. **Does not touch the aggregate.**
4. `d/application/port/out/query/XxxQueryPort.kt` — returns the read DTO directly.
5. `d/infrastructure/adapter/out/query/` — **QueryDSL** projection into the read DTO. Per-query projection; no DataLoader unless a nested field demonstrably causes N+1.
6. `d/infrastructure/adapter/in/graphql/` — DGS `@DgsQuery` resolver. Maps args → query, calls use case, maps read DTO → GraphQL response type. **Application must not import DGS/response types — mapping happens here.**

**If the response composes parts with separable cost** (another upstream call, another table, an aggregate count), do not assemble them in the root. Give each part its own use-case method (step 2) and a `@DgsData(parentType, field)` resolver so it runs **only when the client selects that field**; put an argument on the field that needs it rather than the root. The root then returns the read DTO (not the codegen type — its non-null fields are required constructor args, so lazy fields can't be left empty) and field resolvers read it via `dfe.getSource<ReadDto>()`. A single-source query needs none of this — keep it one method and one resolver. See CLAUDE.md "Don't write REST in GraphQL clothing".

## Output format

1. Confirm domain + slice type + use case in one line.
2. Print the file tree to be created.
3. Emit each file with its full path as a heading.
4. End with a short checklist: schema `.graphqls` entry to add, and which test files `test-strategy` should generate for this slice.

## Guardrails

- Never place JPA/DGS imports in `domain/` or `application/`.
- Never cross into another domain's internals; go through its `port/in` or a domain event.
- Keep resolvers and handlers thin; business rules live in the aggregate.
