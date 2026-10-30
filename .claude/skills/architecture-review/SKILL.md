---
name: architecture-review
description: Review ieuri code for architecture violations ArchUnit cannot catch — CQRS rails, mapper placement and purity, framework leaking below infrastructure, cross-domain coupling through events, common/ bloat, required values weakened to nullable, and (in Exposed-migrated contexts) aggregate roots as the only door, rules judged outside the domain, hidden pairing parameters, application/domain package parity, and adapter role packages. Use on a diff before a PR. Reports only.
license: MIT
metadata:
  author: junha
  version: "1.0"
allowed-tools: Read, Glob, Grep
context: fork
argument-hint: "<path or diff>"
---

# architecture-review

Read `CLAUDE.md` first — it is the contract; this mechanizes the parts a test cannot express.

## Do not re-check these — `LayeredArchitectureTest` already proves them

Dependency direction · domain purity (no Spring/DGS/Exposed/JDBC under `domain/`) · no DGS types,
generated types or table declarations in `application` · cross-context internal imports ·
duplicate bean simple-names · `AFTER_COMMIT` listeners calling `REQUIRES_NEW` · the service and
port shapes in `ConsistencyConventionTest` (`ModifyService`, no repository in a `QueryService`,
`QueryPort` only in read-only services, repositories used by their own aggregate).

If one of these is broken the build is red. Reporting it again adds nothing — spend the pass on
what follows.

## 1. CQRS rails

- Query service that **loads an aggregate** to read — the query adapter projects straight into a read model.
- Query returning a domain aggregate; command returning a read DTO. Both mix the sides.
- A write use case that returns a read model, or takes the request language to build one.
- Command mutating state directly instead of through an aggregate method (anemic aggregate).
- Read-DTO → GraphQL response mapping done in `application` instead of the resolver.
- One fat read method assembling parts with separate cost — one use-case method per
  independently-resolvable field.

## 2. Mappers (POL-033)

- `private fun toDomain(...)` / `toEntity(...)` declared inside an adapter or client. Conversion
  belongs in a `mapper` package as an **extension on the source type**, returning the domain model.
- Entity mappers and external-response mappers must live in **separate** packages
  (`persistence/mapper/` vs `client/mapper/`) so neither swells.
- **Mappers are pure.** Encryption, code resolution, anything needing config is passed *in* as a
  parameter. No business logic, no I/O.

## 3. Framework confinement

Frameworks change; domains outlive them. Spring/Exposed/DGS annotations and imports belong in
`adapter/`. ArchUnit blocks them under `domain/`, but **not** everywhere they don't belong:

- Framework imports in `application/` beyond what a use case genuinely needs. `@Transactional` and
  DI annotations are accepted there; DGS, Exposed, and HTTP types are not.
- A port signature typed in framework terms (`ResponseEntity`, `Page`, a `ResultRow`, a codegen type).
  A port speaks domain models and read DTOs — that is what makes the adapter replaceable.

## 4. Domain independence

Each domain (`auth`, `tourism`, `community`, `alarm`) must be liftable into its own application.

- Cross-domain calls go through the other domain's `provided` use case or a **published domain event** —
  never its internals (ArchUnit catches the import; it does not catch a *design* that assumes
  the other domain is in-process).
- An event listener that assumes synchronous execution or a shared transaction with the publisher.
  Events are the seam; treat the other side as remote.
- **`common/` bloat.** It holds base VOs, error types, and shared security/authz helpers — nothing
  domain-specific. A fat `common/` is the real reason monoliths refuse to split. Flag anything
  added there that only one domain uses.

## 5. Required values weakened

**A value the client always sends must not be nullable.** If the app cannot run without location
consent, coordinates are required. Opening them to null forces a branch for the missing case, and
that branch returns *wrong* data rather than no data. Check all three: the `.graphqls` field, the
`Command`, and the DB column. One of them left open is the path it comes in by.

## 6. Aggregate and package shape (contexts already moved to Exposed)

CLAUDE.md "Target shape" is the contract for a migrated context. Check what compiles but breaks it:

- **A child created or removed outside its root** — a `SpotAnswer.write(...)`, a repository insert
  the service calls without a root method in between. The root is the only door.
- **A domain rule judged in the application or in SQL.** A service that pre-validates, interprets a
  `Boolean`, or a bulk `UPDATE … WHERE` that re-states a rule the domain method already owns (the
  rule now lives twice). Per-item through the domain is the default; bulk SQL only for rows with no
  domain rule (likes, hides, blocks).
- **A hidden pairing** — a parameter the caller must match to another by hand (`owner = ANSWER` with
  a question id). The pairing belongs on a type; a fixed classification is a body `val` on the
  entity, not a constructor argument.
- **`application/` and `domain/` package lists differ.** A package on one side only — an
  orchestrator like `application/withdrawal`, or `application/photo` with no domain twin — is a
  finding. Aggregate-less capabilities go in `shared`; a cross-aggregate process is one listener
  method per aggregate calling that aggregate's use case.
- **An adapter directory mixing directions or technologies** — listeners beside publishers, Valkey
  beside Spring events, resolvers beside dataloaders. Technology first, then role.
- **A domain event named for its consumer** (`XxxNotificationTriggered`) or parked in `shared`
  instead of the aggregate that emits it. Events are facts: `MessageSent`, `SpotAnswerWritten`.
- **A child collection on a root** that is unbounded, or that no decision needs. Only small, bounded
  sets a rule reads as a whole belong on the root.

## Output

Group by file. Per finding: `path:line — which section — what breaks — the minimal fix`.
End with one line: PASS / PASS-with-warnings / BLOCK.
