# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

- **Design by domain, for future extraction.** Keep domain logic at the core and independent of delivery/persistence adapters, following hexagonal ports-and-adapters. All domains share this one application today, so structure each domain so it can be lifted out into its own application later without rewriting the core.

- **The domain model is never a JPA entity — always kept separate. This is a project-wide rule with no exceptions.** Every `domain/` type is pure Kotlin with no persistence concerns. JPA `@Entity` classes live only under `infrastructure/adapter/out/persistence`, with mappers converting between the domain model and the persistence model. This applies to *every* domain, including simple CRUD-heavy ones — not just rich domains. The rationale is domain purity and, above all, **code consistency**: one predictable shape across every domain makes the codebase far easier to read and reason about, and removes the per-domain "is this one separated or merged?" judgment. The accepted cost is mapping boilerplate; do not try to eliminate it by reusing JPA entities as domain models.

- **GraphQL, not REST.** The API layer is Netflix DGS (`graphql-dgs-spring-graphql-starter`), running on top of Spring for GraphQL. Define the schema in `src/main/resources/schema/*.graphqls` and wire resolvers with DGS annotations (`@DgsComponent`, `@DgsQuery`, `@DgsMutation`, `@DgsData`). Prefer DGS over hand-rolled MVC controllers unless a plain HTTP endpoint is genuinely needed. In hexagonal terms, DGS resolvers are inbound adapters — keep them thin and delegate to the application layer.

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
- Write ports and read ports stay physically separate; never route reads through aggregates or the CQRS benefit is lost. The read `port/out/query` may return read DTOs directly — do not force domain-port symmetry on the read side.
- Start with a **per-query QueryDSL projection**. Introduce a DGS `DataLoader` only where nested associations actually cause N+1 — do not add DataLoaders preemptively.

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