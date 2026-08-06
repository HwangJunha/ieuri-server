---
name: solid-review
description: Use this skill before implementing or refactoring an ieuri solution. It reviews responsibilities, extension points, contracts, interface size, dependency direction, and overengineering risk using SOLID principles, aligned with the project's DDD + hexagonal + CQRS boundaries. Reviews only — does not rewrite code.
license: MIT
metadata:
  author: junha
  version: "1.0"
allowed-tools: Read, Glob, Grep      # review-only: no Edit/Write/Bash, so the skill cannot modify code
---

# SOLID Review Skill

## Goal

Review an ieuri design before implementation or refactoring.

Do not rewrite code immediately. First produce a review.

Read the repository `CLAUDE.md` first — its DDD + hexagonal + CQRS rules take precedence over anything here. SOLID is applied *within* those boundaries, never against them (e.g. do not "simplify" away a port or the domain↔entity separation; those are deliberate).

## Review Checklist

### 1. SRP: Single Responsibility

Question: Which classes or modules have more than one reason to change?

Check:
- validation mixed with business logic
- mapping mixed with orchestration (mappers translate shape only)
- data access mixed with response formatting
- policy logic mixed with service orchestration

Output: classes with multiple responsibilities, recommended split.

### 2. OCP: Open/Closed

Question: Which rules may need new implementations later?

Check:
- domain policies that belong in `domain/strategy` or behind a `specification`
- resolver logic that will branch as new query types arrive

Output: policies that should become strategy/specification vs those that can stay simple for now.

### 3. LSP: Substitution Safety

Question: Can interchangeable policies/adapters be used through the same contract?

Check:
- consistent input/output
- no hidden mutation
- clear error behavior
- outbound adapters honoring their `port/out` contract exactly

Output: the contract each policy/adapter type must uphold.

### 4. ISP: Small Interfaces

Question: Does any interface force unrelated responsibilities?

Check:
- fat `port/in` or `port/out` interfaces
- unused methods on a port
- command and query concerns mixed on one interface (should be split — CQRS)

Output: smaller interface suggestions.

### 5. DIP: Dependency Direction

Question: Does an inner layer depend on a concrete outer detail?

Check:
- `application` constructing concrete adapters instead of depending on `port/out`
- hard-coded concrete classes where a port should be injected
- any inward violation of `infrastructure → application → domain`
- difficulty injecting a fake port for tests (a DIP smell)

Output: dependencies to invert/inject; ones that can remain direct for now.

### 6. Overengineering Check (Simple is Best)

Question: Is the design larger than the current problem requires?

Check:
- abstractions with only one implementation *and no extraction plan* (distinct from ports, which are intentional seams)
- factories without selection logic
- plugin systems without plugin requirements
- unnecessary layers *beyond* the deliberate hexagonal ones

Output: keep now / defer until needed / remove from current implementation.

## Final Output Format

Return:

1. SOLID review summary
2. Required changes before implementation
3. Things that are acceptable to keep simple
4. Test cases needed to verify the design (route to the `test-strategy` skill for the per-layer shape)

## Boundaries

- Report issues only; do not modify code.
- Never recommend collapsing a deliberate architectural seam (port, domain↔entity mapper, CQRS split) in the name of SOLID or simplicity.
