---
name: architecture-boundary-review
description: Review ieuri code for violations of its DDD + hexagonal + CQRS boundaries — dependency direction, domain purity, JPA/DGS leakage into inner layers, and cross-domain internal imports. Use when the user asks to review architecture, check boundaries, or before merging a slice. Reports violations only; does not rewrite.
license: MIT
metadata:
  author: junha
  version: "1.0"
allowed-tools: Read, Glob, Grep      # review-only: no Edit/Write/Bash so it cannot modify code
context: fork                        # runs in an isolated subagent — keeps verbose findings out of the main chat
argument-hint: "<path or diff>"
---

# architecture-boundary-review

Static, rule-based review of `ieuri` against its architectural contract. Reports violations grouped by severity; proposes the minimal fix. Does not refactor unless explicitly asked.

Read the repository `CLAUDE.md` first — it is the contract. This skill mechanizes it.

## What to check

### Critical (block merge)

1. **Domain purity.** No file under any `*/domain/` package imports `jakarta.persistence.*`, `com.netflix.graphql.*`, DGS annotations, Spring (`org.springframework.*`), or QueryDSL types. `domain/` is pure Kotlin.
2. **Dependency direction.** `domain` imports nothing from `application` or `infrastructure`. `application` imports nothing from `infrastructure`. Only inward references allowed (`infrastructure → application → domain`).
3. **JPA entity ≠ domain model.** No `@Entity`/`@MappedSuperclass`/`@Embeddable` class outside `*/infrastructure/adapter/out/persistence/`. Domain aggregates/entities must not carry JPA annotations.
4. **Cross-domain isolation.** Domain `a` must not import `b/domain/**` or `b/application/**` internals. Cross-domain access only via the other domain's `port/in` or a published domain event.

### Warning

5. **CQRS split.** Command handlers depend on `port/out/persistence`; query handlers depend on `port/out/query`. A query handler that loads an aggregate is a smell. A command that returns a read DTO is a smell.
6. **Resolver responsibility.** `application` must not import DGS/response types. Read-DTO → GraphQL-response mapping belongs in `infrastructure/adapter/in/graphql`.
7. **Adapter placement.** Inbound adapters under `adapter/in/{graphql,messaging}`; outbound under `adapter/out/{persistence,query,client}`. Nothing inbound/outbound outside these.
8. **`common/` weight.** Flag domain logic leaking into `common/` — it should hold only base VOs, error types, and shared interfaces (e.g. authz).

### Suggestion

9. Anemic aggregate (state mutated from a handler instead of via an aggregate method).
10. Mapper doing business logic (mappers translate shape only).

## How to run

- Prefer analyzing imports and package paths, not just file names.
- If given a diff, review only changed files but resolve their imports against the whole tree.
- Gather any nested `CLAUDE.md` for project-specific overrides before judging.

## Output format

Group by file, then severity. For each finding: `path:line — [severity] rule # — what's wrong — minimal fix`. End with a one-line verdict: PASS / PASS-with-warnings / BLOCK.

