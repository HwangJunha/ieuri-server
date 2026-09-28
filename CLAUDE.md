# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working with the user

- **Write to the user in Korean.** All questions, summaries, and explanations are in Korean. Code, identifiers, commit messages, and code comments follow their existing conventions (comments in this repo are Korean; commit subjects are Korean with conventional-commit prefixes).

- **End every turn with a summary and anything needing confirmation.** After finishing work, close with two things:
    1. **요약** (Summary) — what changed and why, in a few lines. Not a file list; the reasoning that matters.
    2. **확인할 것** (To confirm) — decisions made under assumption, things the user must verify or run, risks accepted, and work deliberately left out. **If there is genuinely nothing to confirm, say so and move on** — do not manufacture items.

  Report faithfully: if something was skipped, blocked, or only partially verified, say it plainly here rather than burying it.

- **Ask before inventing.** When a decision is the user's to make — infrastructure choice with no precedent, a spec `[TBD]`, a tradeoff with no obviously right answer — stop and ask rather than guessing. Do everything that does not depend on the answer first, then ask once with the options and your recommendation.

- **Never write code without the user's approval. This is absolute.** Investigate, read, run read-only queries, and report — but do not create, edit, or delete a file until the user has said to proceed. This holds even when the fix looks obvious, even when you just found a real defect, and even when you are mid-task and the change seems like a natural continuation of work already approved. **Approval is per change, not a standing permit**; finishing an approved edit does not authorize the next one.

  When you find something wrong, the response is a report, not a patch:

    1. State what is wrong and what it breaks, with the evidence you actually gathered.
    2. Give the options, including doing nothing, and say which you would choose and why.
    3. Stop. Wait.

  **The failure this prevents is not a wasted edit — it is losing the user's decision.** Fixing something before it is discussed means the user never gets to weigh in on their own system, and reviewing a diff after the fact is a far worse position to reason from than deciding beforehand. It also buries what was found: a defect described in prose invites a decision, while the same defect silently patched invites nothing.

  Two things this is *not*. Running the build, the tests, or read-only queries to check a hypothesis is investigation and needs no approval — it changes nothing. And once the user approves a piece of work, carrying it out completely is expected; do not stop mid-edit to re-ask about steps that plainly belong to what was agreed.

  **What you put in front of the user is the design**, not a request to start typing — see [Agree the design before writing it](#agree-the-design-before-writing-it). Asking "shall I proceed?" with nothing to disagree with is not approval; it is a formality that produces the same unreviewed code.

## Overview

`ieuri` is a Spring Boot 4 application written in Kotlin, exposing a GraphQL API via Netflix DGS backed by PostgreSQL through Spring Data JPA (with QueryDSL for reads), and Spring Security on the request path. Four bounded contexts are implemented — `auth` (members, tokens, RBAC), `tourism` (spots, details, reactions, festival sync), `community`, and `alarm` — plus a Spring Batch job that syncs TourAPI data.

`ieuri` runs as a single server, and its design is centered on Domain-Driven Design (DDD) and hexagonal architecture. Every domain currently lives inside this one application, but because the application may need to be split as it grows in scale, the design deliberately leads with domain boundaries — so that individual domains can later be extracted into their own separate applications naturally, with minimal friction.

## Commands

Uses the Gradle wrapper (`./gradlew`); Gradle 9.5.1, Java toolchain 25.

- Build: `./gradlew build`
- Run the app: `./gradlew bootRun` (local profile serves on port **8888**; deployed environments use 8080)
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "org.around.ieuri.architecture.LayeredArchitectureTest"`
- Run a single test method: `./gradlew test --tests "<fully.qualified.ClassName>.<methodName>"`
- Package a runnable jar: `./gradlew bootJar`

## Architecture & conventions

- **Simple is Best — do not force-fit a pattern the data or upstream doesn't support.** The hexagonal/CQRS structure itself is deliberate and stays; this rule is about everything built *inside* it. Prefer the simplest implementation that satisfies the acceptance criteria, and let real capabilities — not the shape of a contract or a convention — drive the design. Signs you are over-building: emulating a capability an upstream/library doesn't natively provide (e.g. synthesizing an opaque cursor over an API that only offers `pageNo` offset paging, then re-fetching/merging pages every request to fake stability); adding config knobs, retry/backfill loops, or defensive branches for cases you have not actually observed; hand-rolling parsing/serialization a library already does. When the schema you are about to write implies such machinery, change the shape instead — you are writing it, so change it before it ships — and record it on the issue. Deleting code that doesn't earn its keep is a feature, not a regression. (Precedent: SPEC-0002 dropped a faked Relay cursor because TourAPI is offset-only.) Run `simplify-review` before the PR to catch these.

  **When the complexity comes from a constraint, suspect the constraint first.** These three are signs you are keeping some constraint fixed and faking the result around it — if any appears, stop and ask *why that constraint is there* before writing more code. Changing it usually deletes the awkward code rather than tidying it.
    - Generated SQL or config **grows with the input count** (an N-branch `CASE WHEN`, an `IN` clause of thousands).
    - Something the framework cannot express is **mapped as a different type** to sneak it through.
    - You need a **fudge factor with no basis** — "read 3× and filter afterwards", "retry a few times".
    - You need to **call the same source more than once to satisfy one field.** That means the requirement is asking for something the source does not have.

  **When a forced-fit signal appears mid-implementation, do not fix the code.** Identify which decision or requirement forced it — by id — record the counter-evidence and an alternative in the decision table (`결정`) of `problem/SPEC-XXXX-server.md`, and stop. "This implementation feels forced" is useless; "D-03 (cursor pagination) is impossible because TourAPI is offset-only" is actionable.

- **Infrastructure: follow convention, never hand-roll.** When a slice needs an infrastructure capability (async execution, queueing, scheduling, caching, retries, transactions, events), resolve it in this order and **stop at the first hit**:

  Before step 1: SPEC documents written before SPEC-0035 carry no decision record, and the code currently being refactored is not precedent. If a grep turns up only refactoring targets, treat it as "no precedent" and make a fresh decision — then record it. Following code that is on its way out is worse than having no example at all.

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

- **Behavioural tests are written once, right before the PR — not during the work, and not run during it.**
  Requirements keep moving while a slice is built. Not following the requirement document exactly is
  normal, which is why policy judgment and code decisions belong to this server harness. Tests
  written early get rewritten every time a requirement moves, and that cost exceeds the feedback
  gained.

  **Structure sensors are the exception — run them as you finish each piece, not once at the end.**

  ```
  ./gradlew compileKotlin compileTestKotlin test --tests "org.around.ieuri.architecture.*"
  ```

  Run it when a piece of the slice stands on its own — a resolver wired up, a mapper written, a
  migration added — not after every edit. The point is that a violation surfaces while you still
  remember why you wrote it that way; a check saved for just before the PR arrives when the fix is
  a rewrite. It takes well under a minute, so the cost of running it a few times per slice is
  nothing against finding out late.

  This does not conflict with the rule above: what that rule prevents is **tests that move with the
  requirements**, and structure rules do not move.

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

  But **across aggregates, not even `@ManyToOne`** — references between aggregates are id columns (see "Reference across aggregates by id" under Persistence & migrations). A relation is only ever a candidate *within* one aggregate. That is why the codebase currently has no relations at all, only id references, and that is correct.

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

- **An outbound adapter never injects another adapter — including through a port whose only implementation is one.** The wrapper reads as "the application doesn't need to know about the split", but that is exactly the cost: which writes take raw SQL, and in what order two stores are touched, stop being visible where those decisions are made. **The application service injects both ports** and sequences them itself. A one-line `override` that forwards to another adapter is the tell.

  Two things this is *not*. Injecting the other domain's inbound `port/in` from an `out/client` adapter is the sanctioned cross-domain path (`SpotLookupAdapter` → `SpotQueryUseCase`). And a port whose implementation is a thin shim over a `common/` utility is fine — `EmailEncryptorPort` resolves to three lines wrapping `common/security/AesGcmCipher`, and the encryption happens *inside* entity↔domain mapping, so hoisting it would push storage format up into the service.

  Two exceptions stand today, both because hoisting makes it worse:
    - **`TourApiClient`/`TourApiSyncClient` → `CategoryExternalMappingPort`.** The mapping resolves a domain category into TourAPI's `contentTypeId` *while building the request*. The service cannot sequence it without learning TourAPI's wire format.
    - **`out/event` listeners injecting an outbound port** (`SpotDetailWriteBehindListener`, `VerificationEmailListener`, `NewMessageBroadcastListener`, `PostViewCountListener`). These are one-line event→port bridges with no service between them. The honest fix is to move them to `adapter/in/event/` — where the other domains' listeners already live — not to invent a forwarding use case. Left as a structural decision, not a wiring one.

- **Security is on the request path.** `spring-boot-starter-security` locks endpoints down by default; `auth/infrastructure/config/SecurityConfig.kt` opens GraphQL, and introspection is toggled per profile (`local` only). Per-field authorization is still ours to write.

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

This document is the authority on the rules. The skills cover **only what the structure sensors
cannot catch** — code that compiles, passes the suite, and is still designed wrong. Three test
classes under `src/test/kotlin/org/around/ieuri/architecture/` hold the mechanical checks, so no
skill re-checks them:

- `LayeredArchitectureTest` — dependency direction, domain purity, `@Entity` confinement, DGS
  isolation, cross-domain slices, bean-name collisions, `AFTER_COMMIT`/`REQUIRES_NEW`.
- `ConsistencyConventionTest` — persistence tech out of `application`, `common` out of `domain`,
  `@Transactional` off domain and resolvers, repository/`Dto`/test naming.
- `ImportStyleTest` — no wildcard imports (source-level, so Konsist rather than ArchUnit).

`ConsistencyConventionTest` and `ImportStyleTest` fix **what is already consistent**, chosen because
a full survey found zero violations — not because the convention has been settled. They are
revisited in the refactoring; `problem/HARNESS-server-sensors.md` lists what is up for review.

**A sensor's failure message says what to do, not just what is wrong** — name the rule it enforces,
so the reader can tell a real violation from a rule that is wrong for this slice:

```
[konsist] A wildcard import hides where a name comes from.
  → Use individual imports.
```

**A sensor that is wrong for the slice is a finding, not an obstacle.** When one fires on something
you believe is correct, say so and record why in the decision table rather than reshaping the code
to get past it — a rule nobody is allowed to question stops being a rule and becomes a ritual. That
belongs in the design agreement, not in every failure string; ArchUnit's `because` renders as the
rule's description rather than as failure output, so putting it there only makes the rule read
oddly.

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

**When a slice is finished, hand it to the `reviewer` subagent.** It reads the decision record, then
the `dev...HEAD` diff, then applies the six review skills — and its output leads with the reverse
mapping table (everything the diff adds, traced to an FR/AC or decision id) before any finding.

An implementer treats the decisions they just made as correct, so they miss the same things in their
own code repeatedly. The reviewer read those decisions but did not participate in making them — it
sees what was agreed and what shipped as two separate things, where the implementer remembers only
one. That is why it catches divergence the implementer cannot, and why the mapping table is its job
rather than the implementer's: whoever built something can justify every line of it.

The reviewer reads only the domains the diff touches, plus `common/`. Narrowing what must be read is
the practical payoff of the DDD split; if a change genuinely required reading another domain, that
is itself a finding.

**After applying the fixes, read them back together — once.** Findings arrive as a list, so they get
fixed as a list, and that is the failure: one fix can remove the *reason* another fix exists, and
nothing notices, because each was verified on its own and the suite is green either way. What
survives is machinery whose premise is gone.

Worked example (SPEC-0029): one finding said the event carried only changed rows, so an empty copy
could never be filled; another said a `SELECT`-then-`UPDATE` pair could disagree under READ
COMMITTED. The second was fixed with `UPDATE … RETURNING` — correct in isolation. But the first fix
made the event carry the full authoritative set, so *which* rows changed stopped mattering at all.
`RETURNING` existed to answer a question nobody asked anymore, and it cost raw SQL where QueryDSL had
been. Two green fixes, one pile of dead complexity.

This pass is **not another review**, and it is bounded so it cannot loop:

- **Do not re-run the `reviewer` subagent.** It is your own read, no second opinion.
- **Scope is only what you just changed** — you are not hunting for new defects.
- **One question:** did any fix invalidate the premise of another? Follow-up: is anything now
  unreferenced, over-general, or reaching for raw SQL / a config knob / a wrapper it no longer needs?
- **The output is deletion, not design.** Machinery whose reason is gone comes out. If you find
  yourself adding something, that is a new finding — note it and stop; do not start another cycle.
- **Once.** Then run `simplify-review`, make the suite pass, and open the PR.

A finding you deliberately did not fix is fine — say so in the PR with the reason. Silently leaving
it is not.

## Layout

Package root: `org.around.ieuri`. Organized **by domain first, then hexagonal layer**, with a **CQRS split** in the application layer. Dependencies point inward only: `infrastructure → application → domain`. `domain` imports nothing external (no Spring/JPA/DGS); `application` depends only on `domain`; `infrastructure` implements `application` ports.

```
org/around/ieuri/
  <domain>/                          # auth, tourism, community, alarm
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
        out/                         # one subpackage per outbound concern; add one when a new
          persistence/               #   concern appears rather than widening an existing name.
          query/                     #   Today: persistence, query, client, event, cache, token,
          client/                    #   push, security.
      command/                       # Command input objects (write inputs only)
      query/                         # read-model DTOs + query inputs (read models only)
      service/                       # use-case implementations (XxxService, impl of port/in command & query)
      policy/                        # configurable policy values a use case reads
      validation/                    # domain validation failure → error-code translation
      event/                         # application-level event types
      exception/                     # use-case exceptions
    infrastructure/
      adapter/
        in/
          graphql/                   # DGS resolvers + schema-mapped response types
            mapper/                  # GraphQL input -> Command, read DTO -> response (POL-033)
          event/                     # listeners for other domains' published events
          messaging/                 # Kafka consumers
        out/
          persistence/               # port impl + JPA @Entity + repositories
            entity/                  # @Entity only — confined here
            repository/              # Spring Data interfaces (XxxRepository)
            mapper/                  # domain<->entity extension functions (POL-033)
          query/                     # QueryDSL projections → read-model DTOs
          client/                    # HTTP/gRPC clients
            mapper/                  # external response -> domain, kept apart from entity mappers
          event/                     # this domain's event publishing
          storage/                   # object storage (R2/S3)
      batch/                         # Spring Batch jobs, tasklets
      config/                        # per-domain @Configuration
      security/                      # per-domain security wiring
  common/                            # thin shared kernel: base VO, error types, authzService.can()
  IeuriApplication.kt
```

The tree is the shape a domain grows into, not a checklist — a domain has only the packages it
needs. When a new outbound concern appears, add a subpackage for it rather than widening an
existing name; `port/out/` currently has eight for that reason.

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

> `worktree.baseRef` is `fresh` — it takes only `fresh` or `head`, never a git ref. `fresh` branches a new worktree from the latest remote default branch (`dev` here), matching the rule that features cut from `dev`. It does **not** branch from your current HEAD, so uncommitted work in the shared checkout does not carry over. (`head` would branch from the current HEAD instead.)

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

### A slice

**A slice is a commit, not a branch.** Number the slices of one SPEC `S1`, `S2`, … in the slice
table of `problem/SPEC-XXXX-server.md` and read that list as a **commit plan**: one branch, one PR,
several commits that each read well in history. "S2 해줘" still means that row.

**Cut a new branch when the work is a different feature, not when it is the next slice.** A SPEC
gets one `feature/SPEC-XXXX-<short-kebab-name>` branch from `dev`; if something of a genuinely
different nature turns up mid-way, that is when a second branch is right.

Lowering the unit of work is correct — the unit just isn't the branch. Splitting one SPEC across
branches costs a review round each time and usually cuts in the wrong place: a batch job split into
"the job" and "its retry/limit handling" leaves a job nobody can run until the second PR lands,
while the remaining slices turn out to be one `WHERE` clause and three file deletions. Merging them
also dissolves ordering constraints that only existed because of the split (SPEC-0037: removing a
TTL first would have left a window with no refresh path — irrelevant once the refresh cycle ships
in the same PR).

What that fixes in practice:

- **Branch**: one per SPEC, cut from `dev`. Not one per slice.
- **Commits**: one logical change each, in the slice order, so `dev...HEAD` reads as the story of
  the SPEC rather than one dump.
- **Scope**: read the whole requirement. Looking ahead may shape the data model but not the code —
  if it leaves something behind now (a column for an expiry you will not process yet), that is a
  decision and gets recorded as one.
- **The list is not final.** After S1 lands, the shape of S2 and S3 may change; only the next one
  has to be clear.
- **Judge the split by what it costs.** If the slices are a `WHERE` clause and a deletion, they are
  commits. If one of them is a feature someone would describe without mentioning the others, it is
  a branch.

**A SPEC — no exceptions, no size threshold — starts by agreeing its design (below) and ends at the
reviewer.** That gate is per SPEC, not per slice; the point is to leave a place for a person to step
in before the work is built, and once between design and PR is enough for work this size.

Requirements do not originate in this repository. They come from the planner harness
(`ieuri-planner`) and live in the Obsidian vault under `Specs/SPEC-XXXX/`.

**`Specs/SPEC-XXXX/spec.md`** (vault) is the authority on *what must be true* — requirements and
acceptance criteria. It is the one document in this workflow that this repo does not author.

**`Specs/policies.md`** (vault) holds the 59 numbered platform policies. `spec.md` does not say
which of them apply, so **checking it is this repo's job** — see
[Policies are recorder-owned](#policies-are-recorder-owned--never-edited-here). A policy violation
compiles and passes the suite; nothing else catches it.

**`problem/SPEC-XXXX-server.md`** is the server-side design — domain, aggregate boundary,
command/query split, persistence implications — and it is **written here, not handed down**. The
planner supplies requirements; how they are realised is decided and recorded in this repo. Record
the history and the reason alongside each decision. Other files under `problem/` are yours to write
freely.

**A slice that satisfies the design but not the acceptance criteria is not done.** The design is
yours and can be wrong; the acceptance criteria are what the tests must satisfy.

**Every SPEC gets this document, written from `problem/_TEMPLATE-server.md` before any code.** It is
not optional and there is no size threshold — the slice table lives in it, so without it "S2" names
nothing, and the applied-policies section is the only place policies get checked. A trivial SPEC
produces a short document, not no document.

What *is* a judgment call is whether to delegate the drafting. Hand it to the `requirement-analyst`
subagent when the SPEC carries a hard-to-reverse decision — a domain boundary, a contract, a data
model, a new infrastructure dependency, a concurrency strategy, or the reversal of an earlier
decision. Otherwise write it yourself. Either way the `decision-authoring` skill is the rule for
what a decision is and when a gap must stop you, and either way the document exists before the
first slice starts.

### Issues and branches

Each spec opens one issue per target repo, created by the planner. The issue carries a link to the
spec, the **FR/AC numbers** this repo owns, and a link to the decision report.

**The issue has the numbers, not the sentences — opening `spec.md` is not optional.** And read the
decision report before filling any scale assumption: it records which items are still undecided,
what they block, and where the user's answer conflicted with a legal or industry source. None of
that survives into `spec.md`, which keeps only settled values.

When a spec targets two repos there are two issues, and each repo decides its own realisation from
the same `spec.md`. Do not read the other repo's design document.

Branches are `feature/SPEC-XXXX-<short-kebab-name>`, cut from `dev`, **one per SPEC** (see
[A slice](#a-slice)) — the reviewer reads the `dev...HEAD` diff, so keep that diff to one SPEC's
worth of work and let the commits inside carry the slice structure. The spec id is a
permanent identifier shared by the spec, the issue, the log, and the published post, so carrying
it in the branch makes the whole chain greppable. A step or plan number still does not belong
there — it stops meaning anything the moment the plan changes.

See [A slice](#a-slice) for what one is and how it is scoped.

### Agree the design before writing it

**Work starts with a short design agreement, not with code.** The decisions recorded when the SPEC
document was written came before any of it existed, so they cannot answer the questions that only
appear once you know what you are about to build. Say what you intend to build, then wait.

One agreement covers the SPEC when it ships as one branch. Come back for another only when the
shape changes mid-way — a constraint you did not know about, a forced-fit signal, a policy you had
missed — or when a genuinely different feature turns up and earns its own branch.

**Before writing that agreement, find the second option.** The first shape that comes to mind is
usually the one the last slice used, or the one the schema's wording suggests — neither is an
argument. Until you have something to compare against, you are not choosing, you are reporting.

Three questions, and they are worth answering in writing even when the answer is short:

1. **What else could carry this?** A field on an existing type instead of a new root; an argument
   instead of a field; a column instead of a table; nothing at all, with the client deriving it.
2. **What makes this slice different from the obvious precedent?** Copying a shape is right far more
   often than not — but say what you checked, so "we already do it this way" is a finding rather
   than a reflex.
3. **What would make this wrong in three slices?** A shape that cannot hold the next acceptance
   criterion is worth knowing about now, while changing it is free.

Then write the agreement, and put the rejected option in it — **that is the part the user reads.**
"A nullable column, not a separate table" says what you decided; "a nullable column rather than a
separate table, because only festivals have this field" says what you decided *against*, which is
the only part anyone can disagree with.

If the three questions produce nothing — sometimes there genuinely is one shape — say that in a
line. An agreement with no alternative considered and no note saying why is the failure this
prevents.

Keep it to what someone could disagree with — usually under ten lines:

- **Shape** — root vs field, command vs query, which aggregate owns it, what the schema looks like.
- **Where the work goes** — new types and which package, or which existing ones change.
- **Persistence** — new table or column, a migration, an index.
- **The judgment calls you are making, with the alternative you rejected.** This is the part worth
  reading. "A nullable column rather than a separate table, because only festivals have this" is a
  decision; "add a column" is not.
- **Which policies bind it** — cite the ids from `Specs/policies.md`, and add any the design
  document missed to its applied-policies section. The document was written before this slice's
  shape was known, so it cannot have caught all of them.
- **What you are not doing in this slice.**

Then **stop and wait for a reply.** This is the same rule as "never write code without approval" —
the design agreement is what makes that approval possible, because approving a plan is a different
act from reviewing a finished diff.

Once it is agreed, carry it out completely. Do not re-ask about steps that plainly belong to what
was agreed. If the work leaves the agreed shape mid-slice — a constraint you did not know about, a
forced-fit signal, a policy you had missed — that is a new question, so raise it then rather than
deciding alone.

**Record what was agreed in `problem/SPEC-XXXX-server.md`**, and the agreement splits across two
sections rather than one:

- **Each judgment call becomes its own row** in the decision table (`결정`), continuing the same
  numbering (`D-04`, `D-05`, …) with the slice in the second column. One row per call, not one per
  agreement — a row whose rationale covers three unrelated things cannot be reversed individually.
- **What you said you are not doing goes into `이번 범위 아님`.** `simplify-review` cross-checks the
  implementation against that section, so a deferral that stays in the conversation turns a
  confirmed overreach back into a judgment call.

Shape and placement decisions that carry no alternative need no row; they are visible in the diff.
One SPEC keeps one decision record; there is no separate ADR directory. A decision made in S2 that
reverses one from S1 says so in its rationale column — that reversal is the most useful thing the
document will ever hold.

**After the agreement is approved, write the `.graphqls` first — before any Kotlin — then hand it to
the `mock-announce` subagent.** The order matters: the agreement settles the schema's shape in
prose, but `mock-announce` diffs the schema files on disk, so it has nothing to report until they
are written. Writing them first also means the client starts against the same text the
implementation will compile against.

The subagent starts the mock for this worktree and posts the notice — the url plus what changed,
field by field. The client then builds in parallel instead of waiting out the implementation. See
[the schema section](#the-schema-and-delivering-it-to-the-client).

### When the spec is wrong — fix the spec, not just the code

Requirements change while the work happens. Reflecting a changed requirement only in the code and
leaving the spec alone makes the spec meaningless within three features. So **the implementer edits
`spec.md` in the vault directly** — it is the one document here that this repo does not author.

When the spec does not cover a case or demands something technically impossible:

1. Edit `Specs/SPEC-XXXX/spec.md` (the vault), and record the decision in
   `problem/SPEC-XXXX-server.md`.
2. **Record the date, what changed, and why in the change history.** An edit without history is
   indistinguishable from drift.
3. Leave a line on the issue too — that is where the other repo working from this spec sees it.

Always list what you edited under **확인할 것** (To confirm) at the end of the turn. Quietly changing
a spec is the same failure as quietly implementing something different from it.

### Policies are recorder-owned — never edited here

`Specs/policies.md` is **a document several specs depend on at once.** If one implementer edits it,
the premises of other specs collapse silently, so do not edit it directly.

**Read it before deciding, every slice.** A policy binds this repo only if someone here opens the
catalogue. Read the sections your slice touches (it
is organised into six sections — auth, personal data, API contract, i18n, legal/platform, and
operations), cite the ids in the rationale column of `problem/SPEC-XXXX-server.md`, and treat a
requirement that contradicts a policy as a gap rather than a decision you may make.
`decision-authoring` carries the working rule.

To promote a new policy or revise an existing one, leave a request document **at the point the user
says the work is done**:

```
/Users/junha/git/ieuri/ieuri-recorder/problem/SPEC-XXXX-POLICY-<topic>.md
```

State what must change and why, plus **what the implementation actually settled on**. Where the
spec's candidate wording and the implementation result differ, spell out the difference — the
recorder promotes from this document, not from the spec.

### The schema, and delivering it to the client

The schema is written here, from the requirements and the decisions in
`problem/SPEC-XXXX-server.md`. Grow it to what the current slice needs and no further — a field no
acceptance criterion requires does not belong in `.graphqls` yet.

**The client builds against this schema, and this repo is what publishes it.** A schema drafted
outside this repo drifts from what the server actually serves, and its author cannot change code to
close the gap — so whoever holds the schema publishes it.

**Implementation takes far longer than settling the schema.** If the client waits for it, they idle
through the whole slice. So the moment the schema is settled — **before implementing it** — hand
them a mock server generated from that schema and let them build in parallel.

```bash
cd <this worktree> && /Users/junha/git/ieuri/ieuri-mock-api/mock.sh
```

It reads **this checkout's** `.graphqls`, picks a free port (avoiding 8080 and 8888), and reloads
itself when a schema file changes. Its three startup lines — url, branch, schema path — are what
gets handed to the client.

- **Run it from the worktree you are working in.** It never guesses which schema to serve: worktrees
  run in parallel, so "the most recently edited schema" would quietly serve another slice's shape,
  and a mock that lies is worse than no mock.
- **Responses are generated from the schema's types.** Never hand-write fixtures — they are a second
  copy of the schema that goes stale the moment a field changes, which is the `contract.graphql`
  failure over again.
- **Announcing is the job, not starting the process.** The `mock-announce` subagent starts the mock
  and posts the notice — the url plus what changed, field by field — as a comment on the client
  repo's issue for that SPEC (`HwangJunha/ieuri-k-trip-spot`, one issue per SPEC). "Check the live
  schema" makes the client re-read everything; naming the changed fields does not, and a notice that
  never leaves this session reaches nobody.
- **A schema change after the client has started is the expensive case.** Settle the shape before
  announcing, and if it must change, say so on the issue rather than letting them find out from a
  broken build.
- Introspection is on in `local` only; `dev` and `prod` keep it off on purpose. The mock does not
  change that — it is a separate process serving generated data, with no database behind it.

### Status

The recorder harness moves the spec to `implemented` when it records completion. Do not edit spec
status yourself.

### When a SPEC is done, re-check every memory

Persisted memories go stale the moment code moves, and a stale one is worse than no memory at all:
it sends you to a class that was renamed, cites a PR that already merged, or tells you to copy a
file that is now committed. You do not notice, because a memory reads as established fact — so the
wrong path gets taken confidently.

**A finished SPEC is exactly when they rot**, so check them then. Read every file in the memory
directory and, for each one, pull out its checkable claims — a path, a class name, a config value, a
count, a PR state — and **actually run the check**. Plausibility is not verification; the ones that
mislead are the ones that still sound right.

Then act on what you find:

- **Wrong** — delete it, or correct it with the evidence you just gathered.
- **Stale** — the claim was true and the situation moved (a merged PR, a cleared blocker, work that
  is now done). Rewrite it to today's state rather than deleting; the history is often the point.
- **A brittle number** — "780 files", "59 branches". Replace it with the conclusion it supported;
  counts drift and re-break every time.
- **Already in `CLAUDE.md`** — delete the memory. Two copies of one rule means one of them is
  silently wrong later, and this document is the authority.
- **A behaviour rule** ("always do X", "never do Y") — it does not belong in memory at all. Memory
  holds facts about this project; rules belong here, where they are read every turn.

Finish by checking the index and `[[links]]` still resolve — a pointer to a file you just deleted is
the same failure in miniature. Delegate the sweep to a subagent: the same bias that makes you trust
your own code makes you trust your own notes.
