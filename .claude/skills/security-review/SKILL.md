---
name: security-review
description: Review ieuri code for authorization gaps, sensitive-data exposure, and unsafe boundary handling — every mutation and member-scoped query authorized, personal data masked in logs and encrypted at rest, no credential literals, no error message leaking internals. Use on a diff that touches resolvers, auth, or anything handling member data. Reports only.
license: MIT
metadata:
  author: junha
  version: "1.0"
allowed-tools: Read, Glob, Grep
context: fork
argument-hint: "<path or diff>"
---

# security-review

Read `CLAUDE.md` first. Security failures are the ones a passing test suite is least likely to
reveal: the code works, the data is just visible to the wrong person.

## 1. Authorization — the default is deny

Spring Security is on the classpath, so endpoints are locked by default, but GraphQL is one HTTP
endpoint. **Per-field authorization is ours to write**, and the engine will not remind us.

Check every resolver the diff adds or changes:

- **Every mutation** is authorized. There is no such thing as a mutation any caller may run.
- **Every query returning member-scoped data** verifies the caller *is* that member, or holds a role
  that may act for them. Accepting a `memberId` argument and trusting it is the classic hole —
  the caller's identity comes from the token via `CurrentMemberProvider`, never from an argument.
- Authorization lives at the **inbound adapter or the use case**, consistently with the neighbours.
  A resolver that skips a check its siblings perform is the finding, even if nothing exploits it yet.
- Ownership checks compare against the **authenticated** id, not one re-read from the input.

## 2. Personal data

- **Values encrypted at rest are never logged in plaintext.** Email is stored encrypted; a log line
  holding it plainly makes the encryption pointless, and logs are retained longer and read more
  widely than the database. Mask via `common/logging/maskEmail`.
- Masking is a **pure function outside the call site** — an adapter-private helper is never unit
  tested. It must not throw on any input; losing a log line to a masking error is the worse trade.
- Do not put personal data in exception messages, GraphQL errors, or trace/span attributes — they
  travel further than logs.
- Identifiers derived by HMAC (`provider_user_id` for EMAIL members) cannot be reversed **or
  rotated** — treat the pepper as permanent and never log the input.

## 3. Credentials and config

- **No credential literal, ever** — not as a default, not in a comment, not in a test fixture.
  Injected through environment variables only.
- A missing credential must not be given a working fallback. Choose deliberately between failing
  fast and degrading, and say which: for mail, absence lets the app boot and only sending fails;
  for an encryption key, booting without it would corrupt data and must fail.
- **`${VAR}` vs `${VAR:}`** — the first breaks startup when unset, the second defaults to empty.
  Both are valid; using the wrong one silently changes failure behaviour.
- TLS is required, not merely enabled — `starttls.enable` alone downgrades to plaintext when the
  server offers no STARTTLS, sending credentials in the clear. Set `starttls.required` too.

## 4. Input and errors

- Format validation at the inbound adapter or `Command` construction; business invariants in the
  domain. A value crossing into `application` unvalidated is a finding.
- Error messages returned to clients carry **no internals** — no SQL, no stack traces, no upstream
  response bodies, no whether-the-account-exists distinction on login or password reset.
- Never build SQL by string concatenation of user input, including inside the PostGIS and upsert
  adapters where raw SQL is allowed. Bind parameters.
- Pagination inputs are bounded — an unbounded `limit` is a denial-of-service vector.

## Output

`path:line — which section — what an attacker or a leak achieves — the fix`.
Rank by exploitability: an unauthorized mutation outranks a verbose error message.
If the diff touches no security-relevant surface, say so in one line.
