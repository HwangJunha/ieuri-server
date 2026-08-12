---
name: concurrency-review
description: Review ieuri transaction boundaries, domain events, and async execution — proxy self-invocation, AFTER_COMMIT semantics, @Async on listeners, per-domain executors, swallowed failures, and hand-rolled infrastructure where a Spring idiom exists. Use on a diff touching @Transactional, event publishing/listening, or @Async. Reports only.
license: MIT
metadata:
  author: junha
  version: "1.0"
allowed-tools: Read, Glob, Grep
context: fork
argument-hint: "<path or diff>"
---

# concurrency-review

Read `CLAUDE.md` first. These failures do not appear in tests — they appear under load, or as data
that quietly went missing.

## 1. Transactions

- **Never self-invoke `@Transactional`** from another method of the same class. The proxy is
  bypassed and the annotation does nothing at all. Extract a private helper both call.
  ArchUnit does not see this; it is silent and total.
- `@Transactional` belongs on the **use case**, not on a resolver or a repository adapter.
- `readOnly = true` on query services — it is a correctness signal as well as a hint.
- A transaction must not span a **remote call**. Holding a DB connection across HTTP ties the pool's
  lifetime to someone else's latency. Fetch first, then open the transaction.
- Bulk `update()`/`delete()` bypass the persistence context — check that nothing re-reads those
  entities in the same transaction.

## 2. Events as the domain seam

Domains talk through published events precisely so they can be split apart later. Review each event
as if the listener already lived in another process.

- **The listener must not assume the publisher's transaction or thread.** If it needs its own
  transaction, it declares one — `AFTER_COMMIT` listeners calling a write use case need
  `REQUIRES_NEW` (this one *is* covered by ArchUnit; do not re-report it).
- The event payload carries **ids and copied values**, not entities or aggregates. A lazy proxy
  crossing into a listener throws once the session closes, and an entity is not serializable
  across a process boundary later.
- **`@TransactionalEventListener(AFTER_COMMIT)` runs after commit — throwing there rolls nothing
  back.** It only pollutes the caller's stack. Failures must be logged and swallowed *deliberately*,
  and that decision belongs in a comment at the site.
- Note what a swallowed failure costs. Mail that fails after commit is simply lost; if the work
  cannot be lost, an event is the wrong mechanism and retry/outbox is a separate spec.
- Ordering between listeners must never be assumed.

## 3. Async execution

- **Spring events are synchronous by default.** A listener without `@Async` runs on the publisher's
  request thread — the idiom is only half-applied, and the caller waits for work meant to be
  deferred. This is the single most common miss.
- `@Async` requires the call to arrive **through the proxy** — self-invocation is silently synchronous,
  same as `@Transactional`.
- **Each domain uses its own executor** so one domain's backlog cannot starve another's requests.
  A listener borrowing another domain's executor, or falling back to the default `SimpleAsyncTaskExecutor`
  (unbounded thread creation), is a finding.
- Executors are **bounded** with an explicit rejection policy. An unbounded queue converts load into
  memory exhaustion instead of backpressure.
- No security or request context is assumed inside `@Async` — it does not propagate by default.

## 4. Do not hand-roll infrastructure

Resolve an infrastructure need in this order, stopping at the first hit: **existing precedent in
this repo → the Spring idiom → ask the user.** Never invent.

Flag a hand-rolled `ThreadPoolExecutor`, queue, scheduler, retry loop, or cache where
`ApplicationEventPublisher`, `@Async`, `@Scheduled`, `@Transactional`, `TaskExecutor`, Spring Batch,
or Spring Cache already applies. Precedent: SPEC-0004 hand-rolled an executor queue that
`ApplicationEventPublisher` + `@Async` already provided; the rewrite deleted two classes.

Also flag shared mutable state reachable from more than one thread without synchronization —
a mutable field on a singleton bean is the usual form.

## Output

`path:line — which section — what goes wrong and under what conditions — the fix`.
Say plainly when a failure is **silent** (self-invocation, missing `@Async`) — those are the ones
that will not surface on their own. If the diff touches none of this, say so in one line.
