---
name: test-strategy
description: Write the tests for a finished slice, right before the PR. Pure JUnit5 for domain and application logic, ArchUnit for boundaries, field-by-field tests for every conversion (GraphQL, JPA, external response) — and nothing else in infrastructure. Use when work is done and the PR is next, or when reviewing existing tests for the wrong shape.
license: MIT
metadata:
  author: junha
  version: "2.0"
allowed-tools: Read, Glob, Grep, Write, Edit, Bash
argument-hint: "<slice path or class>"
---

# test-strategy

**Tests are written once, at the end — right before the PR.** Requirements move while a slice is
being built, and tests written early get rewritten every time they move. That cost is real and it
is larger than the feedback gained. During the work: keep it compiling and nothing more.

Read `CLAUDE.md` first for the layer definitions.

## Iron rule

**Test our logic, not the framework.** The suite holds exactly two kinds of test:

1. **Unit tests** for domain and application logic — pure JUnit 5, no Spring context.
2. **ArchUnit tests** for architectural boundaries.

**Nothing else.** No `@SpringBootTest`, `@DataJpaTest`, Testcontainers, or DGS query-execution tests.
No assertions on thread names, executor behavior, or shutdown timing. Those re-prove Spring, and
they are slow and brittle. If an infrastructure concern seems to need pinning, that is a signal to
use a well-trodden Spring idiom instead. **Verify infrastructure by running it, not by encoding it.**

## What to write, per layer

- **`domain/`** → pure JUnit 5. Instantiate the aggregate directly; assert invariants, state
  transitions, and thrown domain exceptions. The majority of the suite; must run in milliseconds.

- **`application/` services** → JUnit 5 with **outbound ports mocked** (MockK). Verify orchestration:
  the right aggregate method, the right port interaction, the right result. No DB.
  This is also where **field-level laziness** is pinned — which I/O a use case performs is business
  logic: `verify(exactly = 0) { tourInfoPort.findGallery(any()) }`. Never boot DGS to prove it.

- **`infrastructure/` adapters** → **no tests.** They are slow to run and heavy on external
  dependencies — a database, an HTTP upstream, a Spring context — and what they would prove is
  Spring's behaviour, not ours. SQL is verified against a real database by hand.

  **The one exception is conversion.** Adapters are where types cross a boundary, and a conversion
  is a pure function with no external dependency, so it costs nothing to test and catches a bug the
  compiler cannot see: a **swapped mapping between two same-typed fields** (`mapy → longitude`,
  `nameKo → nameEn`). Everything else in the adapter is skipped; the conversion is always tested.

## Every conversion is tested

All three boundaries count, in both directions:

| Boundary | Where it lives |
|---|---|
| GraphQL input/response ↔ domain model or `Command` | `adapter/in/graphql` |
| domain model ↔ JPA entity | `adapter/out/persistence/mapper/` |
| external API response ↔ domain model | `adapter/out/client/**/mapper/` |

**Required shape** — the same for all three:

- **Field by field, with a distinct value per field.** One whole-object `assertEquals` does not
  satisfy this: when it fails it cannot say *which* field is wrong, and two fields sharing a
  fixture value let a swap between them pass silently.
- Assert **inside value objects** (`Coordinates.latitude`/`.longitude`, `Email.value`), not just
  that the VO is non-null.
- Include a case where **every nullable field is null** — it must not throw.
- No Spring, no DB, no HTTP. Call the function directly.
- Check for existing coverage through a caller first; extend rather than duplicate.

**A conversion that cannot be called on its own cannot be tested.** GraphQL conversion is often
written inline in the resolver method (`CreatePostCommand(input.title, …)`), which is only reachable
by standing up the resolver — exactly the test we do not write. When a slice adds one, extract it as
an extension function on the source type (`fun CreatePostInput.toCommand(memberId: Long)`) and test
that. If an inline conversion is trivial and total — one field passed straight through — leave it;
extraction is for conversions with enough fields to swap.

**Existing gaps are accepted.** Several conversions predate this rule and are untested; that is the
current state, not a defect to fix in an unrelated slice. The rule binds what a slice **adds or
changes**.

## Architecture (ArchUnit)

Dependency direction, domain purity, `@Entity` confinement, no DGS types in `application`,
cross-domain isolation, `AFTER_COMMIT`/`REQUIRES_NEW`. These live in `LayeredArchitectureTest` and
are what actually *proves* the architecture holds — keep them green.

## At PR time

1. Write the tests for the domain and application logic this slice added.
2. Write a test for **every conversion** the slice added or changed, in all three boundaries above.
3. **Prove each new test can fail** — invert the condition or drop the guard it covers and confirm
   that test goes red. A test written against already-correct code often asserts nothing. Restore.
4. `./gradlew test` must pass before the PR goes up.

## Reviewing existing tests — flag these

- Any `@SpringBootTest` / `@DataJpaTest` / Testcontainers / DGS execution test. **Delete it.**
- Domain logic tested through a Spring context. Critical waste — should be pure JUnit5.
- Service tests hitting a real DB instead of mocked ports.
- **Conversion tests using one whole-object `assertEquals`**, or a fixture where several fields
  share a value (a swap between them passes silently).
- A conversion added by this slice with **no test at all** — including GraphQL input → `Command`.
- Assertions on JPA entities instead of the domain model in write tests.
- Missing failure-path coverage for aggregate invariants.
- Non-deterministic fixtures — if a test can pass or fail on the same input, fix the fixture rather
  than deleting the coverage.

## Output

For generation: each test file with full path, JUnit 5 + MockK, no DB, no Spring context.
For review: `path — [severity] wrong shape — why — the correct shape (or: delete it)`.
