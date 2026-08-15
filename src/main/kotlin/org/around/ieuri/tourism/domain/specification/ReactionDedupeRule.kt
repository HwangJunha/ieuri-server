package org.around.ieuri.tourism.domain.specification

import java.time.Instant

/**
 * 한 회원이 같은 장소에 남긴 반응이 여럿일 때 무엇이 현재 의사인지(SPEC-0019 R-10).
 *
 * 참조 축이 spot에서 카탈로그로 바뀌면서 언어별로 갈려 있던 반응이 하나로 모인다 —
 * 지원 언어가 4개이므로 **최대 4행**이 한 키로 충돌한다.
 *
 * **이 규칙을 적용하는 곳은 마이그레이션 V53이다**(운영 데이터가 없어 이전을 한 번에
 * 끝냈다). 여기 도메인에 두는 이유는 "어느 반응이 현재 의사인가"가 정책 판단이고, SQL로만
 * 존재하면 단위 테스트로 고정할 수 없기 때문이다. 축이 정착한 뒤에는
 * `UNIQUE(member_id, catalog_id)`가 충돌 자체를 막는다.
 *
 * **규칙이 결정적이어야 한다** — 중단했다 재실행해도 같은 행이 남아야 한다.
 * 비결정적 규칙은 재실행 시 다른 행이 남아 좋아요가 뒤바뀐다.
 */
object ReactionDedupeRule {

    /**
     * 충돌하는 반응들 중 남길 하나.
     *
     * `updated_at`이 최신인 것이 현재 의사다 — LIKE/DISLIKE가 엇갈리면 마지막 의사표시를
     * 채택하며, 재평가가 이미 덮어쓰기(이력 없음)로 동작하는 것과 일관된다.
     *
     * **동률이면 `id`가 큰 행을 남긴다.** 임의 선택이지만 결정적이다.
     */
    fun survivor(conflicting: List<ReactionRow>): ReactionRow {
        require(conflicting.isNotEmpty()) { "dedupe 대상이 비어 있습니다." }
        return conflicting.maxWith(compareBy<ReactionRow> { it.updatedAt }.thenBy { it.id })
    }

    /**
     * 같은 `(memberId, catalogId)`끼리 묶어 남길 행과 지울 행을 가른다.
     *
     * 충돌하지 않는 행(그룹 크기 1)은 [ReactionDedupePlan.survivors]에만 담기고 지워지지 않는다.
     */
    fun plan(rows: List<ReactionRow>): ReactionDedupePlan {
        val grouped = rows.groupBy { it.memberId to it.catalogId }
        val survivors = grouped.values.map(::survivor)
        val survivorIds = survivors.mapTo(mutableSetOf()) { it.id }

        return ReactionDedupePlan(
            survivors = survivors,
            deletedIds = rows.map { it.id }.filterNot { it in survivorIds },
        )
    }
}

/** dedupe 판정에 필요한 값만 담은 반응 한 행. */
data class ReactionRow(
    val id: Long,
    val memberId: Long,
    val catalogId: Long,
    val updatedAt: Instant,
)

/** dedupe 결과. [deletedIds]를 지우면 `(memberId, catalogId)` 중복이 남지 않는다. */
data class ReactionDedupePlan(
    val survivors: List<ReactionRow>,
    val deletedIds: List<Long>,
)
