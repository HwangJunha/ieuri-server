> spec: SPEC-0030(확장) · 요청: `problem/SPEC-0030-SERVER-relation-map.md` · status: draft
> 작성: 2026-08-22 · 서버 설계

# 관계 지도 — 서버 설계

클라이언트 요청서에 대한 서버 측 설계다. 요구사항과 결정된 규칙은 요청서에 있고,
이 문서는 **그것을 어떤 구조로 구현할지**만 담는다.

---

## 1. 무엇이 새로 생기는가

지금 공유는 **단방향 스냅샷**이다 — 링크를 연 사람만 겹침을 보고, 주인은 누가 열었는지도
모른다. 관계를 저장하면 그것이 양방향이 된다.

```
지금:  map_share_link (토큰) → sharedMap 조회 → 끝. 아무것도 남지 않는다
이후:  sharedMap 조회 → [등록] → map_relation 행 → 양쪽이 서로를 목록에서 본다
```

**공유 링크는 그대로 둔다.** 관계는 링크 위에 얹히는 별개 층이며, 링크는 여전히 비로그인
열람을 지원한다(R12). 관계 등록만 로그인을 요구한다.

---

## 2. 저장 구조

```sql
CREATE TABLE tour.map_relation (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 이 행을 자기 목록에서 보는 사람
    owner_id       bigint      NOT NULL,
    -- 목록에 나타나는 상대
    counterpart_id bigint      NOT NULL,

    created_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_map_relation UNIQUE (owner_id, counterpart_id),
    CONSTRAINT ck_map_relation_not_self CHECK (owner_id <> counterpart_id)
);

CREATE INDEX idx_map_relation_owner ON tour.map_relation (owner_id);
```

### 왜 방향이 있는 두 행인가

요청서의 **"삭제는 각자 자기 목록만"**이 이 구조를 강제한다. 등록 한 번에 **행 두 개**를
만든다:

```
A가 B의 링크로 등록  →  (owner=A, counterpart=B)
                        (owner=B, counterpart=A)

A가 B를 지움         →  (owner=A, counterpart=B) 만 삭제
                        B의 목록에는 A가 그대로 남는다
```

무방향 한 행(`least(a,b), greatest(a,b)`)으로 두면 한쪽이 지울 때 상대 화면까지 사라진다 —
요청서가 명시적으로 배제한 동작이다. 행을 둘로 두면 각자의 목록이 각자의 소유가 된다.

**`member_id`에 FK를 걸지 않는다**(컨텍스트 간 FK 금지). 탈퇴 시 삭제는 애플리케이션이 하며,
`owner_id`·`counterpart_id` **양쪽 모두**를 지워야 한다 — 상대 목록에 남은 탈퇴자도 사라져야
하기 때문이다.

### 겹침을 저장하지 않는다

요청서의 **"볼 때마다 재계산"**이 이유다. 겹친 칸 수·id를 컬럼으로 두면 서로 여행을 더
다녀도 숫자가 그대로라 기능이 한 번 쓰고 버려진다.

재계산은 이미 있는 것을 재사용한다 — `sharedMap`의 겹침 계산과 같은 경로다.

---

## 3. 겹침 재계산의 비용

이것이 이 설계의 유일한 성능 쟁점이다.

관계 목록 한 페이지(20명)를 그리려면 **각 상대의 방문 칸 집합**이 필요하다. 순진하게 짜면
상대마다 `member_district_visit` 조회 + 칸 매핑이 붙어 40왕복이 된다.

**한 번의 질의로 끝낸다.**

```sql
-- 내 방문 칸과 상대들의 방문 칸을 한 문장에서 교집합
WITH mine AS (
    SELECT DISTINCT mcd.cell_id
    FROM tour.member_district_visit v
    JOIN tour.map_cell_district mcd ON mcd.district_code = v.district_code
    WHERE v.member_id = ?
),
theirs AS (
    SELECT r.counterpart_id, mcd.cell_id
    FROM tour.map_relation r
    JOIN tour.member_district_visit v ON v.member_id = r.counterpart_id
    JOIN tour.map_cell_district mcd ON mcd.district_code = v.district_code
    WHERE r.owner_id = ?
    GROUP BY r.counterpart_id, mcd.cell_id
)
SELECT t.counterpart_id,
       count(*) FILTER (WHERE m.cell_id IS NOT NULL) AS overlap_count,
       array_agg(t.cell_id) FILTER (WHERE m.cell_id IS NOT NULL) AS overlap_cell_ids,
       count(*)                                                  AS their_total
FROM theirs t
LEFT JOIN mine m ON m.cell_id = t.cell_id
GROUP BY t.counterpart_id
```

한 사람의 방문은 최대 252행이고 칸은 161개다. 관계가 수백 명이어도 전체가 수만 행을 넘지
않아 인메모리 집계로 충분하다.

**측정 없이 캐시를 넣지 않는다.** 요청서가 "볼 때마다 재계산"을 요구했고, 캐시를 넣으면
그 요구를 되돌리는 셈이다. 느려지면 그때 실측하고 결정한다.

---

## 4. 계약

```graphql
extend type Query {
    """
    내 관계 목록. 겹친 칸은 **조회 시점에 재계산한 값**이다 — 서로 더 다니면 늘어난다.

    인증 필수(UNAUTHENTICATED).
    """
    mapRelations(first: Int = 20, after: String): MapRelationConnection!
}

extend type Mutation {
    """
    공유 링크로 관계를 맺는다. **양쪽 목록에 서로 추가된다.**

    이미 맺어진 상대면 오류가 아니라 그대로 성공한다(멱등).

    오류: SHARE_LINK_NOT_FOUND · SHARE_LINK_BLOCKED · CANNOT_RELATE_SELF
    """
    createMapRelation(input: CreateMapRelationInput!): CreateMapRelationPayload!

    """
    **내 목록에서만** 지운다. 상대 목록의 나는 남는다.

    없는 관계의 삭제는 오류가 아니다.
    """
    removeMapRelation(input: RemoveMapRelationInput!): RemoveMapRelationPayload!
}

type MapRelation {
    "상대 회원 id."
    memberId: ID!
    "상대 닉네임. 이메일 등 다른 개인정보는 제공하지 않는다."
    nickname: String!
    "겹친 칸 수. 조회 시점 재계산값이다."
    overlappingCount: Int!
    "겹친 칸 id 목록. 화면에서 지도에 표시한다."
    overlappingCellIds: [ID!]!
    "상대가 방문한 전체 칸 수. 겹침 비율 표시에 쓴다."
    theirVisitedCount: Int!
    "관계를 맺은 시각. ISO-8601."
    createdAt: String!
}
```

`input`은 `CreateMapRelationInput { shareUrl: String! }` — 공유 링크를 그대로 받아 서버가
토큰을 꺼낸다(기존 `revokeMapShareLink`와 같은 규약).

`RemoveMapRelationInput { memberId: ID! }` — 목록에서 본 상대를 지우므로 회원 id가 자연스럽다.

### 겹친 칸의 이름 (요청 §4)

요청서는 `sharedMap`이 칸 id만 줘서 "어디가 겹쳤는지" 말할 수 없다고 했다. **`sharedMap`에
`overlappingCells: [MapCell!]`를 추가한다** — id 목록과 중복이지만, 비로그인 열람자는
`mapCells`를 갖고 있지 않아 이름을 얻을 방법이 없다.

id 목록(`overlappingCellIds`)은 그대로 둔다. 이름이 필요 없는 화면이 그것만 고르면 칸 조회가
일어나지 않는다 — `@DgsData`로 지연시킨다.

`MapRelation`에도 같은 이유로 이름이 필요하지만, 관계 목록은 **인증된 조회**라 클라이언트가
`mapCells`를 이미 갖고 있다. id만 주고 클라이언트가 매핑하게 둔다.

---

## 5. 권한과 실패

| 상황 | 처리 |
|---|---|
| 자기 링크로 등록 | `CANNOT_RELATE_SELF` — DB CHECK가 아니라 서비스가 먼저 막는다 |
| 폐기·없는 토큰 | `SHARE_LINK_NOT_FOUND` (기존과 동일, 구분하지 않는다) |
| 차단 관계 | `SHARE_LINK_BLOCKED` — 등록 시점에 `MapViewerBlockPort`로 확인 |
| 이미 맺어진 관계 | 오류 아님. `ON CONFLICT DO NOTHING`으로 멱등 |
| 없는 관계 삭제 | 오류 아님 |

**차단은 등록 시점에만 본다**(결정 2026-08-22). 나중에 차단해도 관계는 목록에 남으며 각자
지운다 — 자동으로 빼면 "차단하면 상대 화면이 바뀐다"가 되어 삭제 비대칭 원칙과 충돌한다.

---

## 6. 탈퇴 처리

`MemberWithdrawn` 리스너가 `map_relation`에서 **`owner_id`와 `counterpart_id` 양쪽 모두**를
지운다. 한쪽만 지우면 상대 목록에 이름 없는 관계가 남고, 닉네임 조회가 null이라 화면이
깨진다.

기존 `eraseMemberMapData`에 합친다 — 같은 트랜잭션에서 방문 기록·공유 링크와 함께 사라진다.

---

## 7. 단위 테스트가 붙는 지점

애플리케이션·도메인 계층만 대상이다(CLAUDE.md §Test scope).

| 대상 | 검증 |
|---|---|
| 관계 등록 | 양쪽에 행이 생긴다 / 자기 링크는 거절된다 / 두 번 등록해도 하나다 |
| 관계 삭제 | 내 행만 사라지고 상대 행은 남는다 / 없는 관계 삭제는 오류가 아니다 |
| 겹침 계산 | 두 방문 집합의 교집합이 칸 단위로 나온다 / 겹침 0이면 빈 목록이다 |
| 차단 | 차단 관계면 등록이 거절된다 |
| 탈퇴 | 양방향 행이 모두 지워진다 |

교집합 SQL의 실제 성능은 인프라로만 관찰 가능하며 테스트를 작성하지 않는다.

---

## 8. 이 설계가 건드리지 않는 것

- **공유 링크** — 발급·조회·폐기 그대로. 관계는 그 위에 얹힌다
- **방문 기록** — 저장 구조 변경 없음. 겹침은 읽어서 계산할 뿐이다
- **`mapCells`·`mapCell`** — 변경 없음
- **차단** — community 소유이며 기존 포트로 묻기만 한다

---

## 9. 결정된 사항 (2026-08-22)

**정렬은 `created_at` 내림차순 + id tie-breaker다.** 겹침 수 내림차순이 화면에는 자연스럽지만
그 값은 조회할 때마다 바뀌므로(재계산이 요구사항이다) 커서의 정렬 키가 될 수 없다 — 페이지를
넘기는 사이 순위가 바뀌면 항목이 중복되거나 누락된다. 겹침 수로 보고 싶다면 클라이언트가
받은 페이지 안에서 정렬한다.

**차단은 등록 시점만 본다**(§5).

**관계 수 상한을 두지 않는다.** 관찰하지 않은 케이스에 미리 방어하지 않는다. 필요해지면
그때 정한다.

**App Link 설정은 나중에 한다.** `assetlinks.json` / `apple-app-site-association` 호스팅이
없으면 링크를 눌러도 앱이 열리지 않아 이 흐름의 실사용 입구가 닫혀 있다. 서버 코드 범위
밖이며, 그것과 무관하게 API는 먼저 만들어 둔다 — 클라이언트가 토큰을 어떤 경로로 얻든
(테스트 중에는 직접 붙여넣더라도) 동작한다.

---

## 10. 구현하며 바뀐 것 (2026-08-22)

설계와 다르게 간 지점과 이유. 문서를 코드에 맞춘 기록이다.

**겹침 SQL은 `LEFT JOIN`이다.** §3의 초안은 내부 조인이었는데, 그러면 **방문이 0인 상대가
페이지에서 통째로 사라진다** — 관계는 있는데 아무것도 그려지지 않는다. `LEFT JOIN`으로
바꿔 `theirVisitedCount = 0`, 겹침은 빈 목록으로 나오게 했다.

`their_total`은 `count(DISTINCT cell_id)`다. 지금은 위 CTE들이 이미 중복을 지워 `count`와
같은 값이지만, 그렇게 두면 이 수가 조인 다중도에 의존하게 된다.

**페이지 경계는 닉네임을 거르기 전에 정한다.** 탈퇴 직후처럼 닉네임을 못 얻은 상대는 목록에서
빼는데, 거른 뒤의 개수로 다음 페이지 존재를 판정하면 **한 명만 섞여도 `first + 1`이 `first`로
줄어 "다음 없음"이 나가고 그 뒤 관계가 영영 보이지 않는다.** `endCursor`도 같은 이유로 살아남은
마지막이 아니라 읽어 온 마지막에서 뽑는다. 리뷰에서 잡혔고 회귀 테스트를 붙였다.

**인덱스의 tie-breaker는 `counterpart_id`다.** 커서가 `(created_at, counterpart_id)`인데
인덱스를 `(owner_id, created_at DESC, id DESC)`로 두면 같은 시각의 커서 조건을 인덱스가 풀지
못한다. V80을 직접 고쳤다(이 슬라이스의 미병합 마이그레이션).

**`sharedMap`이 생성 타입 대신 읽기 DTO를 반환한다.** 요청 문서 §4의 `overlappingCells`를
지연 필드로 두려면 그래야 한다 — 생성 타입은 그 필드를 생성자 필수 인자로 요구한다.

**등록만 생 SQL이고 삭제는 파생 쿼리다.** 멱등성이 `ON CONFLICT DO NOTHING`에서 나오는 등록과
달리 삭제에는 그런 사정이 없다. 대신 생 SQL로 넣은 행은 영속성 컨텍스트에 보이지 않으므로
같은 트랜잭션에서 맺고 다시 읽는 호출자를 만들면 안 된다(어댑터 KDoc에 적었다).
