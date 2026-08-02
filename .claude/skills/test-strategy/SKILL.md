---
name: test-strategy
description: Generate and review tests for the ieuri codebase — pure JUnit5 unit tests for domain and business logic, plus ArchUnit architecture tests. Use when the user asks to add tests for a slice, review test coverage, or decide how to test a class. Enforces "unit-test the domain, never test the framework."
license: MIT
metadata:
  author: junha
  version: "1.0"
# --- Claude Code extensions (ignored by other agents) ---
allowed-tools: Read, Glob, Grep, Write, Edit   # generates test files, so Write/Edit are allowed
argument-hint: "<slice path or class>"
---

# test-strategy

Generates unit tests for domain and business logic in `ieuri`, and reviews existing tests for the wrong kind (the most common waste: booting Spring to test what a plain unit test — or nothing at all — should cover).

Read the repository `CLAUDE.md` first for the layer definitions.

## Iron rule

**Test our logic, not the framework.** The suite contains exactly two kinds of test:

1. **Unit tests** for domain and business logic — pure JUnit 5, no Spring context.
2. **ArchUnit tests** for architectural boundaries.

**Nothing else.** No `@SpringBootTest`, no `@DataJpaTest`, no Testcontainers, no DGS query-execution tests, no tests asserting thread names, executor behavior, shutdown timing, or queue saturation. Those verify Spring rather than us, and they are slow and brittle.

If an infrastructure concern feels like it needs pinning, that is a signal to use a well-trodden Spring idiom (see CLAUDE.md "Infrastructure: follow convention") whose behavior does not need re-proving. Verify infrastructure by **running it**, not by encoding it in the suite.

## Per-layer test shape

- **`domain/` (aggregate, entity, vo, specification, strategy)** → pure **JUnit 5** unit tests, no Spring, no DB, no mocks of infrastructure. Instantiate the aggregate directly and assert invariants, state transitions, and thrown domain exceptions. These are the majority of tests and must run in milliseconds.

- **`application/` command & query handlers** → plain JUnit 5 with the **outbound ports mocked** (MockK). No Spring context. Verify orchestration: correct aggregate method called, correct port interaction, correct result. Do not hit a real DB here.

- **`infrastructure/` (adapters: persistence, query, client, graphql, batch, event)** → **no tests.** Mapping code with no branching is verified by the compiler and by running the app; SQL is verified against a real database by hand. A test that boots a context to prove Spring wires beans tells us nothing about our logic.
    - The one exception is **pure mapping functions with real branching**, which are plain unit tests with no Spring: e.g. `TourApiClient.mapResponse` (upstream DTO → domain, with drop/normalize rules). Call the function directly; do not stand up HTTP or a context.

- **Architecture (`ArchUnit`)** → dependency direction (`infrastructure → application → domain`), domain purity (no JPA/Spring/DGS/QueryDSL under `domain/`), `@Entity` confined to persistence adapters, no DGS types in `application`, cross-domain isolation. These live in `LayeredArchitectureTest`.

### Field-level laziness

Pin it at the **service level**, not through DGS. Selecting only the body must not trigger gallery/intro/info I/O — that is business logic (which I/O a use case performs), so assert it with mocked ports:

```kotlin
service.spotDetail("100", KO)
verify(exactly = 0) { tourInfoPort.findGallery(any()) }
```

Do not boot a DGS context to re-prove that GraphQL only calls selected resolvers — that is the framework's job.

## Generating tests for a slice

Given a slice from `domain-slice-generator`, produce:
1. Domain unit tests for every new invariant/branch (pure JUnit5).
2. Service test with mocked ports — orchestration, fallback order, which I/O runs for which input.
3. Nothing for the adapters. If a slice adds a mapping function with real branching, one plain unit test for it.

Cover edge cases and failure paths, not just the happy path. Prefer table/parameterized tests for VO validation.

## Reviewing existing tests — flag these

- Any `@SpringBootTest` / `@DataJpaTest` / Testcontainers / DGS query-execution test. **Delete it.**
- Tests asserting thread names, executor saturation, shutdown timing, or other framework behavior.
- Domain logic tested through a Spring context (should be pure JUnit5). **Critical waste.**
- Handler tests hitting a real DB instead of mocked ports.
- Resolver tests re-asserting domain invariants already covered downstream.
- Missing failure-path coverage for aggregate invariants.
- Assertions on JPA entities instead of the domain model in write tests.

## Output format

For generation: emit each test file with full path, using JUnit 5 + MockK. **No DB, no Spring context.** For review: `path — [severity] wrong shape — why — the correct shape (or: delete it)`.
