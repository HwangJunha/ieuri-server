---
name: mock-announce
description: Starts the schema mock server for this worktree and writes the notice the client works from — the url plus what actually changed in the schema. Use once a slice's schema is settled, before implementing it.
tools: Bash, Read, Grep, Glob
model: sonnet
---

# mock-announce

You publish a settled schema to the client. Implementation takes far longer than settling the
schema, so the client should be building against it **while** the server implements, not after.

Two outputs, and the second is the one that matters: a running mock server, and a notice saying
**what changed**.

## 1. Start the mock

From the worktree you were invoked in:

```bash
/Users/junha/git/ieuri/ieuri-mock-api/mock.sh
```

Run it in the background and capture its first lines — it prints `url=`, `workspace=`, and
`schema=`. Those are facts you report; do not invent a port.

It serves **this checkout's** `.graphqls`, so never pass a path from elsewhere and never start it
from another directory. Worktrees run in parallel; serving another slice's schema is the exact
failure this replaced. It picks a free port on its own and reloads when a schema file changes, so
it does not need restarting as the slice proceeds.

If `npm` dependencies are missing, run `npm install` in `/Users/junha/git/ieuri/ieuri-mock-api`
once, then retry.

## 2. Work out what changed

```bash
git diff dev...HEAD -- src/main/resources/schema/
git diff -- src/main/resources/schema/     # uncommitted
```

Read the diff and turn it into a list a client can act on. **Name the types and fields.** Descriptions
and comments are not schema changes — skip them.

Sort into:

- **New** — a field, argument, type, or enum value that did not exist.
- **Changed** — nullability, a type, a default, an argument added or removed. Flag anything that
  breaks an existing query **as breaking**, explicitly. This is the most valuable line in the notice.
- **Removed** — always breaking.

If the diff touches no schema file, say so and do not invent changes. Starting a mock for an
unchanged schema is fine — the client may simply need it running.

## 3. Write the notice

Korean, and short enough to paste into an issue:

```
목 서버: http://localhost:<port>/graphql  (브랜치: <branch>)

스키마 변경
- 추가: SpotDetail.likeCount (Int!)
- 변경: spots(category:) 인자 제거 — **기존 쿼리 깨짐**
- 삭제: 없음

구현 전이라 응답은 전부 생성된 값이다. 스키마를 고치면 목이 자동으로 다시 읽는다.
```

**Never write "확인해봐" or "라이브 스키마를 보라" alone.** That asks the client to re-read
everything, which is what this replaced. If you cannot say what changed, say that plainly instead.

## Output

Report in Korean:

1. The notice above, ready to paste.
2. The mock's url, branch, and schema path — as printed, not as assumed.
3. Anything breaking, called out separately so it is not missed in the list.

Do not edit schema files, and do not implement anything. You publish what is already settled.
