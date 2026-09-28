---
name: architecture-review
description: Review ieuri code for architecture violations ArchUnit cannot catch — CQRS rails, mapper placement and purity, framework leaking below infrastructure, cross-domain coupling through events, common/ bloat, and required values weakened to nullable. Use on a diff before a PR. Reports only.
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

Dependency direction · domain purity (no JPA/DGS/Spring/QueryDSL under `domain/`) · `@Entity`
confined to persistence adapters · no DGS types in `application` · cross-domain internal imports ·
duplicate bean simple-names · `AFTER_COMMIT` listeners calling `REQUIRES_NEW`.

If one of these is broken the build is red. Reporting it again adds nothing — spend the pass on
what follows.

## 1. CQRS rails

- Query service that **loads an aggregate** to read — should project via QueryDSL into a read DTO.
- Query returning a domain aggregate; command returning a read DTO. Both mix the sides.
- Read path importing `port/out/persistence`, or write path importing `port/out/query`.
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

Frameworks change; domains outlive them. Spring/JPA/DGS annotations and imports belong in
`infrastructure`. ArchUnit blocks them under `domain/`, but **not** everywhere they don't belong:

- Framework imports in `application/` beyond what a use case genuinely needs. `@Transactional` and
  DI annotations are accepted there; DGS, JPA, and HTTP types are not.
- A port signature typed in framework terms (`ResponseEntity`, `Page`, an entity, a codegen type).
  A port speaks domain models and read DTOs — that is what makes the adapter replaceable.

## 4. Domain independence

Each domain (`auth`, `tourism`, `community`, `alarm`) must be liftable into its own application.

- Cross-domain calls go through the other domain's `port/in` or a **published domain event** —
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

## Output

Group by file. Per finding: `path:line — which section — what breaks — the minimal fix`.
End with one line: PASS / PASS-with-warnings / BLOCK.
