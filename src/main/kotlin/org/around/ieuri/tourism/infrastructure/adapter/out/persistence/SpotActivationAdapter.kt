package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationCounts
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationRow
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 비관광 spot 판정의 읽기·갱신 어댑터(SPEC-0018). 배치 전용 경로다.
 *
 * 활성 상태를 쓰는 유일한 지점이며, `SpotUpsertJdbcAdapter`의 적재 SQL은 이 컬럼을 건드리지
 * 않는다 — 그 분리가 R3(비활성 보존)을 성립시킨다.
 */
@Component
class SpotActivationAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotActivationPort {

    private val spot = QSpotEntity.spotEntity

    /**
     * 판정 입력 4컬럼만 프로젝션한다. 전 행이지만 하루 한 번이고, 애그리거트를 거치지 않아
     * 행당 비용이 컬럼 4개 읽기에 머문다.
     */
    @Transactional(readOnly = true)
    override fun findAllForJudgement(): List<SpotActivationRow> =
        queryFactory
            .select(spot.id, spot.title, spot.category, spot.language, spot.thumbnailImageUrl)
            .from(spot)
            .fetch()
            .map { row ->
                SpotActivationRow(
                    spotId = requireNotNull(row.get(spot.id)),
                    title = requireNotNull(row.get(spot.title)),
                    category = requireNotNull(row.get(spot.category)),
                    language = requireNotNull(row.get(spot.language)),
                    groupKey = requireNotNull(row.get(spot.thumbnailImageUrl)),
                )
            }

    /**
     * **벌크 갱신이라 영속성 컨텍스트를 우회한다.** 이 트랜잭션은 조회 한 번과 갱신 두 번이
     * 전부이고 갱신 뒤에 이 행들을 다시 읽지 않으므로 안전하다.
     *
     * **제외 집합만 파라미터로 나간다.** 활성 대상은 그 여집합이라 `notIn`으로 표현되므로,
     * 파라미터 수가 전 행 수가 아니라 제외 건수(실측 216)를 따라간다.
     *
     * `active.ne(...)` 조건이 R19를 성립시킨다 — 값이 이미 같은 행은 UPDATE에 걸리지 않아
     * 반환 건수가 곧 전환 건수다.
     *
     * **빈 집합이면 `IN`/`NOT IN`을 쓰지 않는다.** QueryDSL의 빈 `notIn`은 조건을 통째로
     * 떨어뜨리거나 항상 거짓이 되는 등 구현에 따라 갈려, 전 행 활성화가 조용히 사라질 수 있다.
     */
    @Transactional
    override fun applyActivation(excludedSpotIds: Collection<Long>): SpotActivationCounts {
        val excluded = excludedSpotIds.distinct()

        val deactivated = if (excluded.isEmpty()) {
            0
        } else {
            queryFactory.update(spot)
                .set(spot.active, false)
                .where(spot.id.`in`(excluded), spot.active.isTrue)
                .execute().toInt()
        }

        val reactivated = queryFactory.update(spot)
            .set(spot.active, true)
            .where(
                if (excluded.isEmpty()) null else spot.id.notIn(excluded),
                spot.active.isFalse,
            )
            .execute().toInt()

        return SpotActivationCounts(deactivated = deactivated, reactivated = reactivated)
    }
}
