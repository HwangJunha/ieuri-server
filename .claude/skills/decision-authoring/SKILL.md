---
name: decision-authoring
description: Write the server-side design document for a SPEC — decisions with rationale, the gaps that block them, and the slice list. Use when turning a planner spec into problem/SPEC-XXXX-server.md, or when a gap appears mid-implementation and you need to phrase the question. Writes only under problem/.
license: MIT
metadata:
  author: junha
  version: "1.0"
allowed-tools: Read, Glob, Grep
---

# decision-authoring

The planner supplies requirements. **How they are realised is decided here**, and the decision is
recorded before any code exists. This skill is the rule for writing that record.

Read `CLAUDE.md` first — the architecture rules are the constraints your rationale draws on.
The output shape is `problem/_TEMPLATE-server.md`; copy it, do not restate it here.

## What you are writing, and what you are not

You are writing decisions and their rationale. You are **not** writing schema, code, or migrations.

Schema is the judgment "what shape should this requirement be exposed in", and that judgment
belongs to whoever implements it. Producing a schema here and handing it over turns the
implementer into a transcriber — they stop deciding and start copying, which is the exact failure
this reform exists to remove. Name the domain, the aggregate boundary, and the command/query
split. Stop before the field list.

## When to stop and ask

Write the decision when you can fill the rationale column from the constraints and the existing
decisions alone. That is the whole test.

If the rationale would read "probably", "likely", or "seems", you do not have a decision — you
have a gap. Put it under 부족한 정보 and stop.

A gap is not a failure of the document. An unfilled gap that gets implemented anyway is.

## How to phrase a gap

The user answers service questions, not technical ones. Never ask which mechanism to use; ask for
the fact you are missing.

- ✗ "Optimistic or pessimistic locking?"
- ✓ "How long does a hold last before it is released?"
- ✗ "Should this be cursor-paginated?"
- ✓ "How many results should one request return at most?"

If you cannot phrase it without naming an implementation, you are asking the user to design.
Rephrase.

The mechanism is then **your** decision, derived from their answer, and it goes in the 결정 table
with their answer as its rationale. That is what makes the record worth keeping: it shows the
service fact the mechanism came from, so when the fact changes the mechanism is revisited instead
of defended.

## Read the decision report before filling 규모 가정

The issue carries a decision-report URL alongside the spec path. The report holds what `미정` items
remain, which requirements they block, and **where the user's answer conflicted with a legal or
industry source**. None of that is in `spec.md` — only settled values land there, and why they
settled that way lives in the report.

So: read the report before you fill a scale assumption. A number you invent that the report already
records a conflict over is the one that gets reverted after implementation.

## Precedent, and what is not precedent

`CLAUDE.md` says to grep for existing precedent first. That rule has a hole right now: SPEC
documents written before SPEC-0035 carry no decision record, and the code currently being
refactored is not precedent either. If a grep turns up only refactoring targets, treat it as **no
precedent** and make a fresh decision — then record it, so the next SPEC has one.

Following code that is on its way out is worse than having no example at all: it launders a
decision nobody made into one that looks settled.

## The slice list

One slice is one acceptance criterion. A commit is too small — refactoring and config commits mix
in — and a whole SPEC is too large. Matching the unit of implementation to the unit of verification
means "why is this here" always has exactly one answer.

The list is not final. After S1 is built, the shape of S2 and S3 may change, and that is normal.
Do not try to partition perfectly up front — the next one being clear is enough.

## 이번 범위 아님 is a mechanism, not a courtesy

Everything deferred goes in that section by name. `simplify-review` cross-checks the implementation
against it, and a match there is a **confirmed overreach rather than a judgment call** — which is
what makes the reviewer's finding arguable on facts instead of taste.

An empty section is suspicious on any SPEC with more than one slice.

## Output

Write `problem/SPEC-XXXX-server.md` from the template. Write nothing else — not `src/`, not
`*.graphqls`, not a migration.

End by stating, in Korean:
- which decisions you recorded (ids and one line each),
- what is under 부족한 정보, phrased as service questions, and
- whether implementation may begin (it may not, if 부족한 정보 is non-empty).
