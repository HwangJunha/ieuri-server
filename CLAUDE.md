# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working with the user

- **Write to the user in Korean.** All questions, summaries, and explanations are in Korean. Code, identifiers, commit messages, and code comments follow their existing conventions (comments in this repo are Korean; commit subjects are Korean with conventional-commit prefixes).

- **End every turn with a summary and anything needing confirmation.** After finishing work, close with two things:
    1. **요약** — what changed and why, in a few lines. Not a file list; the reasoning that matters.
    2. **확인할 것** — decisions made under assumption, things the user must verify or run, risks accepted, and work deliberately left out. **If there is genuinely nothing to confirm, say so and move on** — do not manufacture items.

  Report faithfully: if something was skipped, blocked, or only partially verified, say it plainly here rather than burying it.

- **Ask before inventing.** When a decision is the user's to make — infrastructure choice with no precedent, a spec `[TBD]`, a tradeoff with no obviously right answer — stop and ask rather than guessing. Do everything that does not depend on the answer first, then ask once with the options and your recommendation.

## Overview

`ieuri` is a Spring Boot 4 application written in Kotlin, exposing a GraphQL API via Netflix DGS backed by PostgreSQL through Spring Data JPA, with Spring Security on the request path. It is currently a bare scaffold — only `IeuriApplication` (the `@SpringBootApplication` entry point) exists; there are no entities, resolvers, or schema yet.

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

- **Simple is Best — do not force-fit a pattern the data or upstream doesn't support.** The hexagonal/CQRS structure itself is deliberate and stays; this rule is about everything built *inside* it. Prefer the simplest implementation that satisfies the acceptance criteria, and let real capabilities — not the shape of a contract or a convention — drive the design. Signs you are over-building: emulating a capability an upstream/library doesn't natively provide (e.g. synthesizing an opaque cursor over an API that only offers `pageNo` offset paging, then re-fetching/merging pages every request to fake stability); adding config knobs, retry/backfill loops, or defensive branches for cases you have not actually observed; hand-rolling parsing/serialization a library already does. When a proposed contract (e.g. a planner's `contract.graphql`) implies such machinery, change the contract here instead — the repo owns the final schema (see [Contract](#contract)) — and record it on the issue. Deleting code that doesn't earn its keep is a feature, not a regression. Worked example: SPEC-0002 `spots` dropped a faked Relay cursor for plain offset `page`/`size` because TourAPI is offset-only and its data is live — the client stacks a page and asks for `page+1` on exhaustion. Run `simplify-review` after a slice to catch these.

- **Infrastructure: follow convention, never hand-roll.** When a slice needs an infrastructure capability (async execution, queueing, scheduling, caching, retries, transactions, events), resolve it in this order and **stop at the first hit**:
    1. **This project's existing precedent.** Grep for it first. If another domain already solves it, copy that shape — even if you would design it differently. Consistency beats local optimality.
    2. **The Spring Framework / Boot idiom**, or a purpose-built library already on the classpath. Spring almost always has one (`ApplicationEventPublisher`, `@Async`, `@Scheduled`, `@Transactional`, `TaskExecutor`, Spring Batch, Spring Cache).
    3. **Neither exists → stop and ask the user.** Do not invent infrastructure and do not proceed on a guess.

  Building your own is the last resort, not the first instinct. Worked example (SPEC-0004): write-behind was first implemented as a hand-built `ThreadPoolExecutor` queue with a custom port and a racy discard counter — when the auth domain already had `DomainEventPublisherPort` + `SpringDomainEventPublisher`, and Spring supplies `ApplicationEventPublisher` + `@Async` + `ThreadPoolTaskExecutor`. The precedent existed and was skipped; the rewrite deleted two classes and a whole test file. Note that Spring events are **synchronous by default** — the idiom is only half-applied without `@Async`.

- **Test the domain, not the framework.** Write only:
    - **Unit tests for domain and business logic** — pure JUnit5, no Spring context. Aggregates, value objects, specifications, and use-case services (with mocked ports).
    - **Architecture tests with ArchUnit** — dependency direction, domain purity, `@Entity` confinement, cross-domain isolation.

  **Do not write infrastructure tests.** No tests that boot a Spring context to prove wiring, no tests that hit a real database or Valkey, no tests that assert thread names, executor behavior, shutdown timing, or queue saturation. Those verify the framework rather than our logic, and they are slow and brittle. If an infrastructure concern seems to need pinning, that is a signal to use a well-trodden Spring idiom (see the rule above) whose behavior does not need re-proving. Verify infrastructure by running it, not by encoding it in the suite.

- **Design by domain, for future extraction.** Keep domain logic at the core and independent of delivery/persistence adapters, following hexagonal ports-and-adapters. All domains share this one application today, so structure each domain so it can be lifted out into its own application later without rewriting the core.

- **The domain model is never a JPA entity — always kept separate. This is a project-wide rule with no exceptions.** Every `domain/` type is pure Kotlin with no persistence concerns. JPA `@Entity` classes live only under `infrastructure/adapter/out/persistence`, with mappers converting between the domain model and the persistence model. This applies to *every* domain, including simple CRUD-heavy ones — not just rich domains. The rationale is domain purity and, above all, **code consistency**: one predictable shape across every domain makes the codebase far easier to read and reason about, and removes the per-domain "is this one separated or merged?" judgment. The accepted cost is mapping boilerplate; do not try to eliminate it by reusing JPA entities as domain models.

- **GraphQL, not REST.** The API layer is Netflix DGS (`graphql-dgs-spring-graphql-starter`), running on top of Spring for GraphQL. Define the schema in `src/main/resources/schema/*.graphqls` and wire resolvers with DGS annotations (`@DgsComponent`, `@DgsQuery`, `@DgsMutation`, `@DgsData`). Prefer DGS over hand-rolled MVC controllers unless a plain HTTP endpoint is genuinely needed. In hexagonal terms, DGS resolvers are inbound adapters — keep them thin and delegate to the application layer.

- **Don't write REST in GraphQL clothing — resolve per field, not per endpoint.** A root resolver must fetch only what its *own* fields need. Anything with a separate cost — another upstream call, another table, an aggregate count — belongs in a `@DgsData` field resolver so it runs **only when the client selects that field**. If field selection doesn't change which I/O happens, you've built a REST endpoint behind a GraphQL schema and thrown away the one thing GraphQL buys you. Corollaries:
    - **Arguments belong to the field that needs them, not the root.** If only `intro` requires `category`, it's `intro(category:)` — don't force every caller to pass it to the root.
    - **Cache per operation, not per response.** A single blob keyed by the root can't express "body cached, gallery not," so it silently defeats lazy fields. Key each upstream operation separately.
    - **Distinguish a cache miss from a cached null.** A field that legitimately resolves to null (`intro`) will hammer upstream on every request if absence is treated as a miss. Wrap the value.
    - **Generated types are output shape, not the resolution carrier.** DGS codegen makes non-null fields required constructor args, so a generated type can't be built with lazy fields left empty. Return the read DTO from the root and let field resolvers read it via `getSource`.
    - Lazy resolution is a *runtime* property — compilation proves nothing. Pin it at the **service level**: assert with mocked ports that selecting only the body triggers no gallery/intro/info calls. That is business logic (which I/O a use case performs) and belongs in a unit test; do not boot a DGS/Spring context to prove it. Worked example: SPEC-0003 `spotDetail` — `{ id title overview }` costs 1 upstream call; selecting `gallery`/`intro`/`infoItems`/`likeCount` adds one each.

- **JPA entities need the Kotlin all-open treatment, which is already configured.** The `kotlin("plugin.jpa")` and `allOpen` block in `build.gradle.kts` automatically open classes annotated with `@Entity`, `@MappedSuperclass`, and `@Embeddable` (JPA/Hibernate require non-final classes). Do not manually mark entities `open`. These constraints are exactly why entities stay out of `domain/`.

- **Compiler strictness.** `-Xjsr305=strict` makes Spring's nullability annotations enforce Kotlin null-safety at the boundary, and `-Xannotation-default-target=param-property` controls annotation targeting on constructor properties — keep JPA/validation annotations on `val`/`var` constructor params working as expected.

- **Persistence.** PostgreSQL is the only JDBC driver on the classpath (`runtimeOnly`). There is no datasource configured in `application.yaml` yet; add `spring.datasource.*` (and likely JPA/Hibernate) settings there before anything touching the DB will start.

- **Security is on the classpath.** `spring-boot-starter-security` is included, so once beans exist, endpoints are locked down by default. Add a security configuration to open up GraphQL / introspection as needed.

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
          persistence/               # JPA @Entity + repositories + domain<->entity mappers
          query/                     # QueryDSL projections → read-model DTOs
          client/                    # HTTP/gRPC clients
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

### Cross-domain rules (keeps extraction cheap)

- A domain never imports another domain's `domain`/`application` internals. Cross-domain interaction goes through the other domain's `port/in` or via published domain events.
- Anything genuinely shared goes in `common/`, kept deliberately thin — domain logic must not leak into it. A fat `common` is the real reason monoliths refuse to split later.
## Persistence & migrations

Schema is owned by **Flyway migrations**, never by Hibernate. The domain-model ≠ JPA-entity separation means entities are not the source of schema truth — migrations are.

- **`ddl-auto: validate` only.** Set `spring.jpa.hibernate.ddl-auto=validate` so Hibernate checks entities against the real schema but never creates or alters it. Never use `update`, `create`, or `create-drop` (they let Hibernate mutate schema behind Flyway's back and can drop data).
- **All schema changes are Flyway migrations** under `src/main/resources/db/migration/`, using Flyway's default naming `V{n}__{snake_case_description}.sql` (e.g. `V1__create_community_post.sql`). Flyway runs `migrate` automatically on app startup.
- **Applied migrations are immutable.** Flyway checksums each file; once a version is applied, never edit it — add a new `V{n+1}` file to change anything. Determine the next `n` from `flyway_schema_history`, never reuse a number.
- **One logical change per migration.** Keep them small and reviewable.
- **No cross-domain foreign keys.** A table in one domain must not declare a physical FK into another domain's tables — store the other side's id as a plain column and enforce integrity in the application layer. This is what keeps a domain extractable into its own app/DB later. FKs **within** a single domain are fine and encouraged.
- **Reference across aggregates by id, not object**, even inside a domain.
- **Conventions:** snake_case tables/columns; explicit PK; `created_at`/`updated_at` where the aggregate needs auditing; NOT NULL and constraints that encode real invariants.
- **DB access from tooling is read-only.** The postgres MCP is for inspecting the current schema and verifying after apply — never for running DDL. Schema changes go through migration files, reviewed before apply.

## Git strategy

**Work in a git worktree by default.** Several slices run concurrently, so each unit of work gets its own worktree instead of sharing the checkout — otherwise parallel sessions collide on the working tree, the Gradle build directory, and `.claude/last-test-status`. `worktree.bgIsolation` is set to `worktree` in `.claude/settings.local.json`; when a task starts in an existing session, call `EnterWorktree` rather than branching in place. One worktree per branch, named after it.

> A fresh worktree branches from `origin/<default-branch>` by default, **not** from your current HEAD. Since features cut from `dev`, verify the base before starting and rebase if it is wrong — otherwise the slice is built on stale code.

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

`problem/SPEC-*.md` files are produced by the planner. Do not edit them — a hook blocks it.
Other files under `problem/` are yours.

### Issues and branches

Each spec opens one issue per target repo, created by the planner. The issue carries the
background, a link to the spec, the acceptance conditions for this repo, and the branch name.

Branches are `feature/SPEC-XXXX-<short-kebab-name>`, cut from `dev` as before. The spec id is a
permanent identifier shared by the spec, the issue, the log, and the published post, so carrying
it in the branch makes the whole chain greppable. A step or plan number still does not belong
there — it stops meaning anything the moment the plan changes.

### When the spec is wrong

**Do not fix it in code.** If you hit a case the spec does not cover, a technical impossibility,
or a needed contract change:

1. Stop.
2. Comment on the issue: `[AMEND] SPEC-XXXX: <what you hit and why the spec cannot stand>`
3. Wait. The planner harness folds it back with `/amend`, and the spec is updated with a change
   history entry.

Implementing something different and mentioning it later is what makes a spec worthless within
three features. The temptation is strongest when the deviation is small.

### Contract

`Specs/SPEC-XXXX/contract.graphql` is a **proposal** from the planner, not a decision. This
repository owns the final schema.

Where the proposal is wrong, change it here and say so in the issue — that is a normal outcome,
not an amendment. Where you accept it, the `.graphqls` under `src/main/resources/schema/` must
end up matching it; a `contract-drift` hook compares them and blocks on divergence.

### Status

The recorder harness moves the spec to `implemented` when it records completion. Do not edit spec
status yourself.
