---
name: test-strategy
description: Generate and review tests for the ieuri codebase with the correct test shape per hexagonal layer — pure JUnit5 for domain, Spring slices for adapters, DGS query/mutation tests for the API. Use when the user asks to add tests for a slice, review test coverage, or decide how to test a class. Enforces "no Spring context for domain logic."
license: MIT
metadata:
  author: junha
  version: "1.0"
# --- Claude Code extensions (ignored by other agents) ---
allowed-tools: Read, Glob, Grep, Write, Edit   # generates test files, so Write/Edit are allowed
argument-hint: "<slice path or class>"
---

# test-strategy

Chooses and generates the right **kind** of test for each layer in `ieuri`, and reviews existing tests for the wrong kind (the most common waste: booting Spring to test pure domain logic).

Read the repository `CLAUDE.md` first for the layer definitions.

## Iron rule

**Match the test to the layer. Never boot the Spring context to test something that has no Spring/JPA dependency.** Fast pure tests for the core; slice tests only where a framework boundary is actually exercised.

## Per-layer test shape

- **`domain/` (aggregate, entity, vo, specification, strategy)** → pure **JUnit 5** unit tests, no Spring, no DB, no mocks of infrastructure. Instantiate the aggregate directly and assert invariants, state transitions, and thrown domain exceptions. These are the majority of tests and must run in milliseconds.

- **`application/` command & query handlers** → plain JUnit 5 with the **outbound ports mocked** (MockK). No Spring context. Verify orchestration: correct aggregate method called, correct port interaction, correct result. Do not hit a real DB here.

- **`infrastructure/adapter/out/persistence` (JPA + mapper)** → `@DataJpaTest` (or `@SpringBootTest` persistence slice) against a real Postgres via **Testcontainers**. Test the mapper round-trip (domain → entity → domain) and, for updates, that dirty-checking emits the expected `UPDATE`.

- **`infrastructure/adapter/out/query` (QueryDSL)** → persistence-slice test with Testcontainers; assert the projection returns the correct read DTO and does not over/under-fetch.

- **`infrastructure/adapter/in/graphql` (DGS resolvers)** → DGS query/mutation tests (`DgsQueryExecutor` / a `@SpringBootTest` with the DGS test starter). Assert schema wiring, input → command mapping, and read DTO → response mapping. Business rules are already covered at the domain layer — don't re-assert them here.
    - **Field-level laziness must be pinned here, and only here.** When a type has `@DgsData` lazy fields, "unselected fields don't fetch" is a *runtime* property — compilation and unit tests cannot prove it. Execute real queries and assert **call counts** on the use-case mock: `verify(exactly = 0) { useCase.xxx(any()) }` for fields the query omits, `exactly = 1` for the ones it selects. Also cover a field argument (`intro(category:)` reaching the port unchanged) and a nullable lazy field resolving to null without errors.
    - Mocks wired as Spring beans are **singletons shared across test methods**, so call counts accumulate and a later test sees an earlier one's calls. `clearMocks(...)` in `@BeforeTest` — otherwise count assertions are silently wrong.

## Generating tests for a slice

Given a slice from `domain-slice-generator`, produce:
1. Domain unit tests for every new invariant/branch (pure JUnit5).
2. Handler test with mocked ports.
3. One persistence-slice test for the mapper (+ dirty-checking on update).
4. One DGS resolver test for the happy path + one validation-failure path.

Cover edge cases and failure paths, not just the happy path. Prefer table/parameterized tests for VO validation.

## Reviewing existing tests — flag these

- Domain logic tested through `@SpringBootTest` (should be pure JUnit5). **Critical waste.**
- Handler tests hitting a real DB instead of mocked ports.
- Resolver tests re-asserting domain invariants already covered downstream.
- Missing failure-path coverage for aggregate invariants.
- Assertions on JPA entities instead of the domain model in write tests.

## Output format

For generation: emit each test file with full path, using JUnit 5 + MockK, Testcontainers where a DB is required. For review: `path — [severity] wrong layer/shape — why — the correct shape`.
