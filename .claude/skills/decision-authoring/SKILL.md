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

**A gap is a fact about the service that only the user can supply.** Not a technical choice you find
hard — those are yours to make, and making them is the job. How long a hold lasts, what a user sees
when a thing is missing, whether two people may hold the same seat: nobody in this repo can derive
those, and guessing produces code that is wrong in a way no test will show.

So the test is not how confident the sentence sounds. Ask instead: **if I pick one, what tells me
later that I picked wrong?**

- **The constraints do** — a policy, an existing decision, the upstream's actual capability, a
  measurement. Then it is a decision. Write it, cite what decided it, and move on.
- **Only the user does** — the answer lives in how the service is supposed to behave, and no
  artifact in this repo contains it. Then it is a gap. Put it under 부족한 정보 and stop.

Hedging language is a symptom worth noticing — if the rationale wants to say "probably" or "seems",
you are usually in the second case. But **deleting the hedge does not make it a decision.** The
question is what would catch the mistake, not how the sentence reads.

A gap is not a failure of the document. An unfilled gap that gets implemented anyway is.

**This test governs what goes in *this* document, not whether the user is consulted at all.** Two
things are deliberately not settled here:

- **A decision you can justify may still be one the user wants to make.** Filling the rationale
  column proves the decision is defensible, not that it is theirs to skip. When a choice changes
  what the service does — what a user sees, what is stored about them, what a client must handle —
  put it in the 결정 table *and* raise it, rather than treating the table as the approval.
- **Each slice agrees its own design before it is written** (`CLAUDE.md`, "Agree the slice's design
  before writing it"). This document is written once, before any code, so it cannot answer questions
  that only appear once the shape is known. Those are not failures of this document; they are the
  next conversation, and they land in the same 결정 table as `D-04`, `D-05`, …

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

## Check the policy catalogue — every time

`$IEURI_VAULT/Specs/policies.md` holds 59 numbered platform policies (POL-001…) in six sections:
인증·계정 / 개인정보 / API 계약 / 다국어 / 법규·플랫폼 / 운영. **They bind this repo whether or not
anything reminds you of them.**

The planner's `spec.md` used to carry an "적용 정책" section listing which ones applied. It no longer
does, so **checking them is this document's job now.** Nothing else does it.

Read the sections your slice touches — not all 59. A query with paging touches API 계약 (POL-017
cursor, POL-018 page sizes and caps, POL-019 sort guarantees); anything storing member data touches
개인정보 (POL-013 no PII in logs, POL-014 retention); anything with coordinates touches POL-045.
When in doubt about a section, read its policy titles; they are one line each.

For each policy that bears on a decision:

- **It constrains the decision** → cite the id in the 근거 column. "POL-018: 콘텐츠형 상한 50" is a
  rationale you cannot invent, which is exactly what makes it worth citing.
- **The requirement conflicts with it** → that is a gap, not a decision you may make. Policies are
  shared by several specs at once, so overriding one here silently breaks the premises of others.
  Put it under 부족한 정보 and stop.
- **It needs to change, or a new one is needed** → still not yours to write.
  `Specs/policies.md` is recorder-owned (see `CLAUDE.md`, "Policies are recorder-owned"). Record what
  the implementation settled on and leave the promotion request where that section says.

A policy violation compiles, passes the suite, and is caught by nobody — POL-045 forbids putting a
timestamp on a location record, and no test in this repo will ever tell you that.

## Precedent, and the slice list

Both rules live in `CLAUDE.md` — the "no precedent" carve-out in the infrastructure ordering, and
"a slice is one acceptance criterion" under Issues and branches. Follow them there rather than
here; a second copy drifts from the first.

The one thing to add when *writing* the list: it is not final. After S1 is built the shape of S2
and S3 may change, and that is normal. Do not partition perfectly up front — the next one being
clear is enough.

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
