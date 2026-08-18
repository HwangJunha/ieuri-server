# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working with the user

- **Write to the user in Korean.** All questions, summaries, and explanations are in Korean. Code, identifiers, commit messages, and code comments follow their existing conventions (comments in this repo are Korean; commit subjects are Korean with conventional-commit prefixes).

- **End every turn with a summary and anything needing confirmation.** After finishing work, close with two things:
    1. **요약** (Summary) — what changed and why, in a few lines. Not a file list; the reasoning that matters.
    2. **확인할 것** (To confirm) — decisions made under assumption, things the user must verify or run, risks accepted, and work deliberately left out. **If there is genuinely nothing to confirm, say so and move on** — do not manufacture items.

  Report faithfully: if something was skipped, blocked, or only partially verified, say it plainly here rather than burying it.

- **Ask before inventing.** When a decision is the user's to make — infrastructure choice with no precedent, a spec `[TBD]`, a tradeoff with no obviously right answer — stop and ask rather than guessing. Do everything that does not depend on the answer first, then ask once with the options and your recommendation.

## Overview

`ieuri` is a Spring Boot 4 application written in Kotlin, exposing a GraphQL API via Netflix DGS backed by PostgreSQL through Spring Data JPA (with QueryDSL for reads), and Spring Security on the request path. Two bounded contexts are implemented today — `auth` (members, tokens, RBAC) and `tourism` (spots, details, reactions, festival sync) — plus a Spring Batch job that syncs TourAPI data.

`ieuri` runs as a single server, and its design is centered on Domain-Driven Design (DDD) and hexagonal architecture. Every domain currently lives inside this one application, but because the application may need to be split as it grows in scale, the design deliberately leads with domain boundaries — so that individual domains can later be extracted into their own separate applications naturally, with minimal friction.

## Commands

Uses the Gradle wrapper (`./gradlew`); Gradle 9.5.1, Java toolchain 25.

- Build: `./gradlew build`
- Run the app: `./gradlew bootRun` (serves on port **8888**, not 8080)
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "org.around.ieuri.IeuriApplicationTests"`
- Run a single test method: `./gradlew test --tests "org.around.ieuri.IeuriApplicationTests.contextLoads"`
- Package a runnable jar: `./gradlew bootJar`

## Architecture & conventions

- **Simple is Best — do not force-fit a pattern the data or upstream doesn't support.** The hexagonal/CQRS structure itself is deliberate and stays; this rule is about everything built *inside* it. Prefer the simplest implementation that satisfies the acceptance criteria, and let real capabilities — not the shape of a contract or a convention — drive the design. Signs you are over-building: emulating a capability an upstream/library doesn't natively provide (e.g. synthesizing an opaque cursor over an API that only offers `pageNo` offset paging, then re-fetching/merging pages every request to fake stability); adding config knobs, retry/backfill loops, or defensive branches for cases you have not actually observed; hand-rolling parsing/serialization a library already does. When a proposed contract (e.g. a planner's `contract.graphql`) implies such machinery, change the contract here instead — the repo owns the final schema (see [Contract](#contract)) — and record it on the issue. Deleting code that doesn't earn its keep is a feature, not a regression. (Precedent: SPEC-0002 dropped a faked Relay cursor because TourAPI is offset-only.) Run `simplify-review` before the PR to catch these.

  **When the complexity comes from a constraint, suspect the constraint first.** These three are signs you are keeping some constraint fixed and faking the result around it — if any appears, stop and ask *why that constraint is there* before writing more code. Changing it usually deletes the awkward code rather than tidying it.
    - Generated SQL or config **grows with the input count** (an N-branch `CASE WHEN`, an `IN` clause of thousands).
    - Something the framework cannot express is **mapped as a different type** to sneak it through.
    - You need a **fudge factor with no basis** — "read 3× and filter afterwards", "retry a few times".

- **Infrastructure: follow convention, never hand-roll.** When a slice needs an infrastructure capability (async execution, queueing, scheduling, caching, retries, transactions, events), resolve it in this order and **stop at the first hit**:
    1. **This project's existing precedent.** Grep for it first. If another domain already solves it, copy that shape — even if you would design it differently. Consistency beats local optimality.
    2. **The Spring Framework / Boot idiom**, or a purpose-built library already on the classpath. Spring almost always has one (`ApplicationEventPublisher`, `@Async`, `@Scheduled`, `@Transactional`, `TaskExecutor`, Spring Batch, Spring Cache).
    3. **Neither exists → stop and ask the user.** Do not invent infrastructure and do not proceed on a guess.

  Building your own is the last resort, not the first instinct. (Precedent: SPEC-0004 hand-rolled a `ThreadPoolExecutor` queue when `ApplicationEventPublisher` + `@Async` already existed; the rewrite deleted two classes and a test file.) Note that Spring events are **synchronous by default** — the idiom is only half-applied without `@Async`.

- **Test the domain, not the framework.** Write only:
    - **Unit tests for domain and business logic** — pure JUnit5, no Spring context. Aggregates, value objects, specifications, and use-case services (with mocked ports).
    - **Architecture tests with ArchUnit** — dependency direction, domain purity, `@Entity` confinement, cross-domain isolation.

  **Do not write infrastructure tests.** No tests that boot a Spring context to prove wiring, no tests that hit a real database or Valkey, no tests that assert thread names, executor behavior, shutdown timing, or queue saturation. Those verify the framework rather than our logic, they are slow, and they drag in external dependencies. If an infrastructure concern seems to need pinning, that is a signal to use a well-trodden Spring idiom (see the rule above) whose behavior does not need re-proving. Verify infrastructure by running it, not by encoding it in the suite.

  **The one exception is conversion, and there it is mandatory — every conversion, both directions.** GraphQL input/response ↔ domain model or `Command`; domain model ↔ JPA entity; external API response ↔ domain model. A conversion is a pure function with no external dependency, so it is cheap to test, and it hides a bug the compiler cannot see: a **swapped mapping between two same-typed fields** (`mapy → longitude`). Assert **field by field with a distinct value per field** — a single whole-object `assertEquals` cannot say which field is wrong, and shared fixture values let a swap pass. Include an all-nulls case.

  A conversion written inline inside a resolver method cannot be called on its own, so it cannot be tested without standing up the resolver. When a slice adds one with enough fields to swap, **extract it as an extension function on the source type** and test that. Conversions predating this rule are untested; that is the accepted current state, and the rule binds what a slice adds or changes.

- **Tests are written once, right before the PR — not during the work, and not run during it.**
  Requirements keep moving while a slice is built. Not following the planner's document exactly is
  normal, which is why policy judgment and code decisions belong to this server harness. Tests
  written early get rewritten every time a requirement moves, and that cost exceeds the feedback
  gained. While working, **keep it compiling** (`./gradlew compileKotlin compileTestKotlin`) and
  nothing more.

  **Immediately before opening the PR**, write the tests for the domain and application logic this
  slice added, and make `./gradlew test` pass. The `test-strategy` skill has the required shape.
  If a policy changed, leave a request document in `ieuri-recorder/problem/`.

- **Design by domain, for future extraction.** Keep domain logic at the core and independent of delivery/persistence adapters, following hexagonal ports-and-adapters. All domains share this one application today, so structure each domain so it can be lifted out into its own application later without rewriting the core.

- **The domain model is never a JPA entity — always kept separate. This is a project-wide rule with no exceptions.** Every `domain/` type is pure Kotlin with no persistence concerns. JPA `@Entity` classes live only under `infrastructure/adapter/out/persistence`, with mappers converting between the domain model and the persistence model. This applies to *every* domain, including simple CRUD-heavy ones — not just rich domains. The rationale is domain purity and, above all, **code consistency**: one predictable shape across every domain makes the codebase far easier to read and reason about, and removes the per-domain "is this one separated or merged?" judgment. The accepted cost is mapping boilerplate; do not try to eliminate it by reusing JPA entities as domain models.

- **Conversion lives in a `mapper` package, as extension functions on the source type (POL-033).** Do not scatter `private fun toDomain(...)` members across adapters and clients — a conversion buried in the class that uses it grows with that class and cannot be unit-tested on its own. Each bounded context gets `infrastructure/adapter/out/persistence/mapper/` for entity↔domain, and external-response conversion goes in a *separate* package (`.../out/tourapi/mapper/`) so one package doesn't swell again. Rules:
    - Declare as an extension on the **source** type and return the **domain model**: `fun SpotEntity.toDomain(): Spot`, `fun TourApiDetailCommonResponse.Item.toSpotDetail(...): SpotDetail`. Never return a JPA entity or a codegen type across the boundary.
    - **Keep side-effecting concerns out.** Encryption, category-code resolution, and anything needing config are passed *in* as parameters (`fun MemberEntity.toDomain(profile, decryptedEmail: String)`) — the mapper stays a pure function.
    - Mappers translate shape only. No business logic, no I/O.
    - Every conversion gets a pure unit test asserting **field by field**, with a distinct value per field so a swapped mapping fails. A single whole-object `assertEquals` does not satisfy this — it cannot tell you *which* field is wrong. Include a case where all nullable fields are null. This covers the GraphQL boundary too: a resolver's input → `Command` conversion is extracted as an extension function (`fun CreatePostInput.toCommand(...)`) when it has enough fields to swap, so that it is callable — and therefore testable — without the resolver.

- **Comments summarize what a thing does; rationale lives in the spec (POL-033).** Design rationale, policy justification, rejected alternatives, and SPEC history do not belong in code — link the spec instead. **KDoc blocks stay under 15 lines.** Two things must survive the trimming, though: (1) *operational warnings* a maintainer needs at the call site — "an empty surviving set deletes everything", "`ST_MakePoint` takes (lon, lat)", step ordering that a FK depends on; and (2) *behavioral facts that exist only in the comment* — clamp-vs-error at a limit, null vs empty-array meaning, empty-string normalization, sort guarantees. Those are contract, not rationale. Compress them to one line; do not delete them. The same rule governs GraphQL schema descriptions — see the schema bullet below.

- **GraphQL, not REST.** The API layer is Netflix DGS (`graphql-dgs-spring-graphql-starter`), running on top of Spring for GraphQL. Define the schema in `src/main/resources/schema/*.graphqls` and wire resolvers with DGS annotations (`@DgsComponent`, `@DgsQuery`, `@DgsMutation`, `@DgsData`). Prefer DGS over hand-rolled MVC controllers unless a plain HTTP endpoint is genuinely needed. In hexagonal terms, DGS resolvers are inbound adapters — keep them thin and delegate to the application layer.

- **Schema descriptions are written for the client, not the server (POL-033).** A description carries what a client needs to *use* the field: its meaning, unit and format, what null means, pagination defaults and caps including **what happens past the cap** (clamp or error — POL-018), and sort guarantees (POL-019). It does not carry server-side implementation: cache strategy, upstream operation names or call counts, batch behavior, or SPEC ids. When shortening a description, diff it for *behavioral facts stated nowhere else* — null vs empty array, empty-string normalization, who sanitizes HTML — and keep every one. Losing those silently breaks clients that branch on them.

- **A missing root is never the answer to "can the API do X?" — this is absolute.** When asked whether some capability exists, or when a client reports a field is undefined, **do not answer by grepping for a root named after it.** `myQuestions` not existing says nothing about whether the server can return a member's questions. The question is always *what can carry this* — in this order:

    1. **An argument on an existing field.** `spotQuestions(catalogId:)` returns a filtered list; a member filter is one more optional argument (`mine: Boolean`), not a new root. Widening a filter reuses the sort, cursor, and moderation rules already there; a parallel root duplicates all three and they drift.
    2. **A field on an existing type.** Anything owned by a member hangs off `Me`; anything owned by a spot hangs off `SpotDetail`. If the caller already holds the parent, the data belongs on it.
    3. **A new root** — only when nothing above can carry it, i.e. the client holds no parent object and the thing is not a filtered view of an existing list.

  Answering "there is no such root, so the feature is missing" is a REST reflex: it assumes one capability = one endpoint. **State what the schema can be made to express, not what it currently names.** If the honest answer is that it needs building, say which of the three shapes it takes — never just "not supported."

- **A new root query/mutation is the last resort — look for the field first.** Before adding a root, find the existing type in the same domain this belongs to as a field. Ask: *would a client ever call this without the thing it hangs off?* If not, it is a field, not a root. Adding `spotLikeCount(spotId:)` beside an existing `spotDetail` costs the client a second round trip and re-sends an id the server already resolved — `likeCount` belongs on `SpotDetail`. Every new root flattens the graph, and a flat graph is a REST API with extra syntax. We chose GraphQL for the flexibility; a new root is correct only when nothing existing can carry it.

- **Never take a caller identity as an argument.** `myQuestions(memberId:)` is not a member filter, it is an authorization hole — anyone passing another id reads that person's data. Ownership comes from the auth context (`currentMemberProvider`), and the schema expresses it as a boolean filter (`mine: true`) or a field on `Me`. The same applies to any "whose" argument: the server decides who the caller is, the client never asserts it.

- **Don't write REST in GraphQL clothing — resolve per field, not per endpoint.** A root resolver must fetch only what its *own* fields need. Anything with a separate cost — another upstream call, another table, an aggregate count — belongs in a `@DgsData` field resolver so it runs **only when the client selects that field**. If field selection doesn't change which I/O happens, you've built a REST endpoint behind a GraphQL schema and thrown away the one thing GraphQL buys you. Corollaries:
    - **Arguments belong to the field that needs them, not the root.** If only `intro` requires `category`, it's `intro(category:)` — don't force every caller to pass it to the root.
    - **Cache per operation, not per response.** A single blob keyed by the root can't express "body cached, gallery not," so it silently defeats lazy fields. Key each upstream operation separately.
    - **Distinguish a cache miss from a cached null.** A field that legitimately resolves to null (`intro`) will hammer upstream on every request if absence is treated as a miss. Wrap the value.
    - **Generated types are output shape, not the resolution carrier.** DGS codegen makes non-null fields required constructor args, so a generated type can't be built with lazy fields left empty. Return the read DTO from the root and let field resolvers read it via `getSource`.
    - Lazy resolution is a *runtime* property — compilation proves nothing. Pin it at the **service level**: assert with mocked ports that selecting only the body triggers no gallery/intro/info calls. That is business logic (which I/O a use case performs) and belongs in a unit test; do not boot a DGS/Spring context to prove it. Worked example: SPEC-0003 `spotDetail` — `{ id title overview }` costs 1 upstream call; selecting `gallery`/`intro`/`infoItems`/`likeCount` adds one each.

- **JPA relations follow the textbook — no `@OneToMany`; a child-side `@ManyToOne` only, when one is needed at all.** Unless a service requirement genuinely demands otherwise, follow the well-proven default. `@OneToMany` fires a query the moment a mapper touches the collection — even when reading a single row — and its size cannot be bounded. A unidirectional child→parent `@ManyToOne` is better on both performance and longevity. Reads are the job of the repositories and QueryDSL projections.

  But **across aggregates, not even `@ManyToOne`** — references between aggregates are id columns (see "Reference across aggregates by id" above). A relation is only ever a candidate *within* one aggregate. That is why the codebase currently has no relations at all, only id references, and that is correct.

- **JPA entities need the Kotlin all-open treatment, which is already configured.** The `kotlin("plugin.jpa")` and `allOpen` block in `build.gradle.kts` automatically open classes annotated with `@Entity`, `@MappedSuperclass`, and `@Embeddable` (JPA/Hibernate require non-final classes). Do not manually mark entities `open`. These constraints are exactly why entities stay out of `domain/`.

- **Compiler strictness.** `-Xjsr305=strict` makes Spring's nullability annotations enforce Kotlin null-safety at the boundary, and `-Xannotation-default-target=param-property` controls annotation targeting on constructor properties — keep JPA/validation annotations on `val`/`var` constructor params working as expected.

- **Persistence.** PostgreSQL is the only JDBC driver on the classpath (`runtimeOnly`), configured via `spring.datasource.*` in `application.yaml` (the local password comes from `IEURI_DB_PASSWORD`).

- **Queries are QueryDSL or derived queries — raw SQL is the exception, and exceptions get their own adapter.** The whole point of JPA + QueryDSL is not being pinned to one database; a `JdbcTemplate` string throws that away silently. Order of preference: Spring Data **derived query** → **QueryDSL** → raw SQL. Do not use `@Query` at all — an annotation string splits the query definition across two places and is neither type-checked nor refactorable.
    - Only two things justify raw SQL today: **PostGIS spatial functions** (`ST_DWithin`, `ST_Distance` — QueryDSL cannot express them) and **`INSERT ... ON CONFLICT` upsert** (JPA has no portable upsert; `merge` semantics would introduce a select-then-insert race).
    - When you need one, **isolate it in a dedicated adapter** whose name says so (`SpotSpatialQueryAdapter`, `SpotUpsertJdbcAdapter`). The invariant to preserve: `JdbcTemplate` appears *only* in those adapters. Give that adapter its **own outbound port** so the application depends on the port, not on another adapter — `infrastructure → infrastructure` injection hides the split in the wrong layer.
    - **A column whose type JPA cannot express (PostGIS `geography`, …) is not mapped on the entity at all.** Mapping it — even as `String` to sneak it past the compiler — fails `ddl-auto: validate` at startup. An unmapped column is also invisible to HQL, so **any query touching it belongs in the raw-SQL adapter above.** Deriving the value from mapped columns instead (`ST_MakePoint(map_x, map_y)`) compiles but skips the index; measure before choosing it.
    - **JPQL has no row-value constructor.** Keyset paging written as `(a, b) < (?, ?)` in SQL becomes `a < ? OR (a = ? AND b < ?)` in QueryDSL. Equivalent result and ordering; verify the index is still used if the table is large.
    - Bulk `update()`/`delete()` bypass the persistence context. That is fine when nothing re-reads those entities in the same transaction — **check the callers before converting**, and note that DB-level `ON DELETE CASCADE` still fires (it is not a JPA cascade).
    - Never self-invoke a `@Transactional` method from another method of the same class — the proxy is bypassed and the annotation silently does nothing. Extract the shared body into a private helper and have both call it.

- **Security is on the classpath.** `spring-boot-starter-security` is included, so once beans exist, endpoints are locked down by default. Add a security configuration to open up GraphQL / introspection as needed.

- **`ZH` is always Traditional Chinese (번체/正體) — never Simplified.** This applies everywhere `ZH` appears: DB name columns, seed data, GraphQL responses, and any content we author or generate.

  The reason is that we do not serve mainland China at all. The app cannot ship there — Play Store and App Store are unavailable, and the domestic Android stores each impose their own approval process we are not taking on. So the actual `ZH` audience is Taiwan and Hong Kong, where Traditional is the correct script. Writing Simplified would be wrong for every user who will ever read it.

  Practical consequences when filling `name_zh`-style columns: use `鍾路區`, not `钟路区`; `廣域市`, not `广域市`; `濟州市`, not `济州市`; `上黨區`, not `上党区`. Do not mix scripts within a dataset — a single Simplified row among Traditional ones is a defect, not a variant. Note that Japanese (`JA`) is a *separate* script convention (shinjitai: `東莱区`), so `JA` and `ZH` legitimately differ and neither should be copied into the other.

## Personal style

These are the repository owner's preferences, not correctness rules. Code that breaks them is not
wrong — it is just not how this codebase is written. They are recorded here so the reasoning does
not have to be repeated, and so tooling and other sessions follow the same shape.

- **No `inline fun <reified T>`. Pass the target type as an explicit `Class<T>` parameter,** even
  though callers then repeat `::class.java`.

  The principle is *avoid the construct rather than use it carefully* — what you do not use cannot
  go wrong. `reified` buys exactly one thing: not repeating `::class.java` at the call site. Its
  required companion `inline` brings real footguns along — the body is copied into every call site
  (binary bloat when the function grows), and a `public inline` function cannot touch private
  members. Repetition is the cheaper price.

  ```kotlin
  // Prefer
  private fun <T> read(payload: String, type: Class<T>): T? = mapper.readValue(payload, type)
  read(payload, TypingEventPayload::class.java)

  // Avoid
  private inline fun <reified T> read(payload: String): T? = mapper.readValue(payload, T::class.java)
  read<TypingEventPayload>(payload)
  ```

  This says nothing about `inline` on lambda-taking functions (the stdlib `let`/`map` shape) — that
  case has not come up. If `reified` looks unavoidable, ask rather than reaching for it.

## Review — skills and the reviewer

This document is the authority on the rules. The skills cover **only what ArchUnit cannot catch** —
code that compiles, passes the suite, and is still designed wrong. The eight boundary violations are
enforced by `LayeredArchitectureTest`, so no skill re-checks them.

- `architecture-review` — CQRS rails, mapper placement and purity, framework leaking below
  infrastructure, domain independence, `common/` bloat, required values weakened to nullable.
- `jpa-review` — relations (no `@OneToMany`), dirty-checking, raw-SQL containment, query choice,
  migrations.
- `graphql-review` — a new root where a field belonged, per-field resolution, N+1, description bloat.
- `security-review` — authorization on every mutation and member-scoped query, personal data in
  logs and errors, credential handling.
- `concurrency-review` — transaction boundaries and proxy self-invocation, event semantics,
  `@Async` and per-domain executors, hand-rolled infrastructure.
- `simplify-review` — forced-fit implementations and needless abstraction.
- `test-strategy` — the shape tests must take when written before the PR.

**When a slice is finished, hand it to the `reviewer` subagent.** It reads the `dev...HEAD` diff and
applies the six review skills. An implementer treats the decisions they just made as correct, so
they miss the same things in their own code repeatedly. The reviewer never saw those decisions being
made — only the diff — so the bias does not carry over. This is why human code review works.

The reviewer reads only the domains the diff touches, plus `common/`. Narrowing what must be read is
the practical payoff of the DDD split; if a change genuinely required reading another domain, that
is itself a finding.

## Layout

Package root: `org.around.ieuri`. Organized **by domain first, then hexagonal layer**, with a **CQRS split** in the application layer. Dependencies point inward only: `infrastructure → application → domain`. `domain` imports nothing external (no Spring/JPA/DGS); `application` depends only on `domain`; `infrastructure` implements `application` ports.

```
org/around/ieuri/
  <domain>/                          # community, commerce, tourism, auth, auction
    domain/                          # pure Kotlin — no framework/JPA/DGS imports
      aggregate/                     # aggregate roots (entry point to invariants)
      entity/                        # entities owned by an aggregate
      vo/                            # value objects
      event/                         # domain events
      specification/                 # Specification-pattern rules
      strategy/                      # Strategy-pattern policies
    application/
      port/
        in/
          command/                   # write use-case interfaces (XxxCommandUseCase)
          query/                     # read use-case interfaces (XxxQueryUseCase)
        out/
          persistence/               # write ports: load/save aggregate
          query/                     # read ports: return read-model DTOs
          client/                    # external system ports
      command/                       # Command input objects (write inputs only)
      query/                         # read-model DTOs + query inputs (read models only)
      service/                       # use-case implementations (XxxService, impl of port/in command & query)
    infrastructure/
      adapter/
        in/
          graphql/                   # DGS resolvers + schema-mapped response types
          messaging/                 # Kafka consumers
        out/
          persistence/               # port impl + JPA @Entity + repositories
            entity/                  # @Entity only — confined here
            mapper/                  # domain<->entity extension functions (POL-033)
          query/                     # QueryDSL projections → read-model DTOs
          client/                    # HTTP/gRPC clients
            mapper/                  # external response -> domain, kept apart from entity mappers
  common/                            # thin shared kernel: base VO, error types, authzService.can()
  IeuriApplication.kt
```

There is no per-domain `utils` package. Domain-specific helpers become domain services, value objects, or specifications; genuinely generic helpers go in `common/`.

### CQRS & data flow (enforce this)

- **Command (write):** GraphQL input → `Command` object (application) → `domain` aggregate (invariants enforced here) → JPA `@Entity` via the persistence adapter's mapper. The domain model is the source of truth; JPA entities are a persistence detail and never leak into `application`/`domain`.
    - **On update:** inside the transaction, load the *managed* JPA entity, apply the domain's changes onto that managed entity, and let Hibernate dirty-checking emit the `UPDATE`. Do **not** build a fresh detached entity and `save()` it — that defeats dirty-checking.
    - **On create:** map the domain aggregate to a new entity and persist it.
- **Query (read):** GraphQL args → query service (`application/service/`) → outbound `query` port → **QueryDSL projects directly into a read-model DTO**, bypassing the domain aggregate entirely. The **resolver** maps that read DTO into the GraphQL response type — `application` must not import DGS/response types.
- **One use-case method per independently-resolvable field.** A composite response is not one query. If `gallery` and `likeCount` cost separate I/O, the inbound port exposes `spotGallery(...)`/`spotLikeCount(...)` alongside the body method, and a `@DgsData` resolver calls each only when selected. Don't build a fat method that eagerly assembles everything — that forces the resolver to fetch fields nobody asked for.
- Write ports and read ports stay physically separate; never route reads through aggregates or the CQRS benefit is lost. The read `port/out/query` may return read DTOs directly — do not force domain-port symmetry on the read side.
- Start with a **per-query QueryDSL projection**. Introduce a DGS `DataLoader` only where nested associations actually cause N+1 — do not add DataLoaders preemptively. (Lazy field resolvers avoid over-fetching *per request*; DataLoaders solve N+1 *within* a request. Different problems — don't substitute one for the other.)

### Naming

- Write input is a `Command`, not a "DTO". Reserve "DTO" for the read side (QueryDSL projection results and read models). This keeps command input, domain model, JPA entity, read DTO, and GraphQL response from collapsing into one overloaded word.
- **Use-case implementations are `XxxService`, not `XxxHandler`, and live in `application/service/`.** Group related use cases into one cohesive service with **meaningful method names** (e.g. `MemberRegistrationService.registerBySns/verifyEmail`, `AuthTokenService.loginBySns/refresh/logout`) instead of a generic `handle()` per single-method class. The matching inbound port groups the same use cases (`MemberRegistrationUseCase`, `AuthTokenUseCase`, …).

### Validation

- Format/shape validation at the inbound adapter or at `Command` construction. Business invariants only inside `domain`. No duplication across layers.

- **A value the client always sends is never accepted as nullable.** If the app cannot run without
  location consent (k-trip-spot cannot), coordinates are **required**. Opening them to null forces
  the server to write a branch for the missing case, and that branch does not merely return less
  data — it becomes **a path that returns wrong data**. An error beats silently substituting a
  default. Keep it required in all three places: the `.graphqls` field, the `Command`, and the DB
  constraint. One of them left open is the path it will arrive through.

### Cross-domain rules (keeps extraction cheap)

- A domain never imports another domain's `domain`/`application` internals. Cross-domain interaction goes through the other domain's `port/in` or via published domain events.
- Anything genuinely shared goes in `common/`, kept deliberately thin — domain logic must not leak into it. A fat `common` is the real reason monoliths refuse to split later.
## Persistence & migrations

Schema is owned by **Flyway migrations**, never by Hibernate. The domain-model ≠ JPA-entity separation means entities are not the source of schema truth — migrations are.

- **`ddl-auto: validate` only.** Set `spring.jpa.hibernate.ddl-auto=validate` so Hibernate checks entities against the real schema but never creates or alters it. Never use `update`, `create`, or `create-drop` (they let Hibernate mutate schema behind Flyway's back and can drop data).
- **All schema changes are Flyway migrations** under `src/main/resources/db/migration/`, using Flyway's default naming `V{n}__{snake_case_description}.sql` (e.g. `V1__create_community_post.sql`). Flyway runs `migrate` automatically on app startup.
- **Migrations from a finished slice are immutable — including comments. The ones you wrote in *this* slice are still yours to edit.** Flyway hashes the whole file, so changing a single comment line breaks the checksum and blocks startup. What that costs depends on who else has the file:

    - **Written in an earlier slice (merged, or another session's work): never edit.** Other checkouts and environments have already applied it, and a local repair does nothing for them — it would break their startup instead. Add a new `V{n+1}` file. Determine the next `n` from `flyway_schema_history`, never reuse a number.
    - **Written in this slice, not merged yet: edit the file.** It exists only on this branch, so nobody else has applied it. Splitting a correction into `V{n+1}` here leaves permanent history of a decision that was reverted before anyone saw it — the reviewer then reads two migrations to learn what one says. Keep the branch's migrations in the shape they should ship in.

  **Editing one you already applied locally needs a repair — ask the user to run it.** Your own boot has it in `flyway_schema_history` with the old checksum, so the next startup fails validation. There is **no Flyway Gradle plugin here** (migrations run at boot, from Spring Boot), so there is no `flywayRepair` task — the repair is SQL the user runs against the local DB:

  ```sql
  UPDATE flyway_schema_history SET checksum = <"Resolved locally" from the failure log>
  WHERE version = '<n>';
  ```

  **The checksum is only half of it.** Repair rewrites history, never schema — whatever the old version already created is still in the database. If your edit removed or changed a statement, say so and give the user the DDL that undoes it, or the file and the real schema stay silently out of step. Do not reach for `V{n+1}` merely to dodge this request, and never repair a migration from a finished slice — there the mismatch is the correct alarm.

  **Assume every migration in the working tree is already applied.** During a slice you will boot the app several times, and each boot applies whatever is new — so a file you wrote an hour ago is almost certainly in `flyway_schema_history` now. Check it before touching any migration, and note that **both databases** track their own history (main and search node).
- **One logical change per migration.** Keep them small and reviewable.
- **No cross-domain foreign keys.** A table in one domain must not declare a physical FK into another domain's tables — store the other side's id as a plain column and enforce integrity in the application layer. This is what keeps a domain extractable into its own app/DB later. FKs **within** a single domain are fine and encouraged.
- **Reference across aggregates by id, not object**, even inside a domain.
- **Copying another domain's *value* does not violate the FK rule.** The id reference stays; duplicating just the field a query needs (a spot's coordinates onto a post) removes the round-trip to that domain and makes extraction easier, not harder. The contract is that the copy **does not follow later edits** — so only copy values where that is the desired behaviour (the location a post was written at, not a price).
- **Conventions:** snake_case tables/columns; explicit PK; `created_at`/`updated_at` where the aggregate needs auditing; NOT NULL and constraints that encode real invariants.
- **DB access from tooling is read-only.** The postgres MCP is for inspecting the current schema and verifying after apply — never for running DDL. Schema changes go through migration files, reviewed before apply.

## Git strategy

**Claude commits when the work is done.** Group changes into commits that read well in history —
one logical change per commit, not one giant dump. Korean subjects with conventional-commit
prefixes, matching the existing log.

Push and PR follow the same rule: do them when the slice is finished, without asking each time.
Never push to `master`, never force-push.

**Still report what changed in the summary.** A commit is not a substitute for telling the user
what you did and why.

**Work in a git worktree by default.** Several slices run concurrently, so each unit of work gets its own worktree instead of sharing the checkout — otherwise parallel sessions collide on the working tree and the Gradle build directory. `worktree.bgIsolation` is set to `worktree` in `.claude/settings.local.json`; when a task starts in an existing session, call `EnterWorktree` rather than branching in place. One worktree per branch, named after it.

> `worktree.baseRef` is `origin/dev`, so a fresh worktree branches from `dev` — matching the rule that features cut from `dev`. It does **not** branch from your current HEAD, so uncommitted work in the shared checkout does not carry over.

Branches: `master`, `dev`, `feature/*`, `hotfix/*`.

- **master** — production. Always deployable; only ever receives merges from `dev` (or `hotfix/*`). Never commit directly.
- **dev** — integration branch, kept in an always-mergeable-to-master state. Every feature starts from `dev` and merges back into `dev`.
- **feature/** — all new work. Branch from `dev`, merge back into `dev`. This is where review happens (feature → dev).
- **hotfix/** — urgent production fixes. Branch from `master`, merge into `master` (and back-merge into `dev` to keep it in sync).

Merge & review rules:

- `feature/* → dev`: **reviewed** (this is the review gate).
- `dev → master`: **not reviewed** — dev is already validated and kept release-ready, so promoting it to production is a straight merge.
- Never start a feature from `master`. Never start a hotfix from `dev`.
- After a `hotfix/* → master`, back-merge into `dev` so dev doesn't fall behind production.

## Spec-driven workflow

Requirements do not originate in this repository. They come from the planner harness
(`ieuri-planner`) and live in the Obsidian vault under `Specs/SPEC-XXXX/`.

Two documents, two jobs:

- **`Specs/SPEC-XXXX/spec.md`** (vault) — requirements, acceptance criteria, applied policies.
  The authority on *what must be true*.
- **`problem/SPEC-XXXX-server.md`** (this repo) — the server-side design: domain, aggregate
  boundary, command/query split, persistence implications. The entry point for implementation,
  versioned alongside the code.

**Read both before implementing.** The design document is where you start; the acceptance
criteria in the linked spec are what the tests must satisfy. A slice that satisfies the design
but not the acceptance criteria is not done.

`problem/SPEC-*.md` files are produced by the planner, but **when a requirement changes, the
implementer edits them directly** (see "When the spec is wrong" below). Record the history and the
reason alongside the edit. Other files under `problem/` are yours to write freely.

### Issues and branches

Each spec opens one issue per target repo, created by the planner. The issue carries the
background, a link to the spec, the acceptance conditions for this repo, and the branch name.

Branches are `feature/SPEC-XXXX-<short-kebab-name>`, cut from `dev` as before. The spec id is a
permanent identifier shared by the spec, the issue, the log, and the published post, so carrying
it in the branch makes the whole chain greppable. A step or plan number still does not belong
there — it stops meaning anything the moment the plan changes.

### When the spec is wrong — fix the spec, not just the code

Requirements change while the work happens. Reflecting a changed requirement only in the code and
leaving the spec alone makes the spec meaningless within three features. So **the implementer edits
the spec and the design document directly.**

When the spec does not cover a case, demands something technically impossible, or the contract needs
to change:

1. Edit `Specs/SPEC-XXXX/spec.md` (the vault) and `problem/SPEC-XXXX-server.md`.
2. **Record the date, what changed, and why in the change history.** An edit without history is
   indistinguishable from drift.
3. Leave a line on the issue too — the planner sees this decision when writing the next spec.

Always list what you edited under **확인할 것** (To confirm) at the end of the turn. Quietly changing
a spec is the same failure as quietly implementing something different from it.

### Policies are recorder-owned — never edited here

`Specs/policies.md` is **a document several specs depend on at once.** If one implementer edits it,
the premises of other specs collapse silently, so do not edit it directly.

To promote a new policy or revise an existing one, leave a request document **at the point the user
says the work is done**:

```
/Users/junha/git/ieuri/ieuri-recorder/problem/SPEC-XXXX-POLICY-<topic>.md
```

State what must change and why, plus **what the implementation actually settled on**. Where the
spec's candidate wording and the implementation result differ, spell out the difference — the
recorder promotes from this document, not from the spec.

### Contract

`Specs/SPEC-XXXX/contract.graphql` is a **proposal** from the planner, not a decision. This
repository owns the final schema.

Where the proposal is wrong, change it here and say so in the issue — that is a normal outcome,
not an amendment. Where you accept it, the `.graphqls` under `src/main/resources/schema/` must
end up matching it — check it against the contract before the PR.

### Status

The recorder harness moves the spec to `implemented` when it records completion. Do not edit spec
status yourself.
