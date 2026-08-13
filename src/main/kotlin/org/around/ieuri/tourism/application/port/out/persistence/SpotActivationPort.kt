package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 비관광 spot 판정 단계의 아웃바운드 포트(SPEC-0018). 적재 포트([SpotStorePort])와 분리한다 —
 * 적재는 활성 상태를 건드리지 않는 것이 R3의 성립 조건이라, 두 관심사가 한 포트에 섞이면
 * 나중에 누가 적재 SQL에 컬럼을 더한다.
 */
interface SpotActivationPort {
    /**
     * 판정 대상 전 행. **범위를 좁히지 않는다**(R19) — "이번에 적재된 행만" 같은 축소를 두면
     * 설정이 바뀌었을 때 기존 적재분이 재판정되지 않는다.
     *
     * 언어를 가르지 않는 이유는 언어 간 전파(R8)가 `thumbnail_image_url` 그룹 단위이고
     * 그 그룹이 언어를 가로지르기 때문이다.
     */
    fun findAllForJudgement(): List<SpotActivationRow>

    /**
     * [excludedSpotIds]는 비활성으로, **나머지 전부**는 활성으로 되돌리고 두 방향의 전환 건수를
     * 돌려준다.
     *
     * **제외 집합만 받는 이유**: 활성 대상은 제외 집합의 여집합이라 SQL의 `NOT IN`으로 표현된다.
     * 양쪽 id를 다 넘기면 `IN` 절이 전 행 수만큼 커지는데, 제외 대상은 실측 216건이라 한쪽만
     * 넘기면 파라미터가 입력 규모를 따라 늘지 않는다.
     *
     * 두 갱신 모두 `WHERE active <> ?`가 붙어 **같은 결과의 재판정은 건수에 잡히지 않는다**(R19).
     * 빈 집합이면 전 행이 활성으로 되돌아간다 — 키워드가 비었을 때의 정의된 동작이다(R12).
     */
    fun applyActivation(excludedSpotIds: Collection<Long>): SpotActivationCounts
}

/** 판정 반영 1회의 전환 건수. 값이 실제로 달라진 행만 센다(R19). */
data class SpotActivationCounts(
    val deactivated: Int,
    val reactivated: Int,
)

/** 판정에 필요한 최소 컬럼(read DTO). 제목·카테고리가 판정 입력이고 [groupKey]가 언어 간 연결 키다. */
data class SpotActivationRow(
    val spotId: Long,
    val title: String,
    val category: SpotCategory,
    val language: DomainLocale,
    /**
     * 같은 장소의 전 언어 행을 잇는 키 — `thumbnail_image_url`이다(spec §8.7).
     * `NOT NULL`이고 빈 문자열이 제약으로 막혀 있어 null·공백 처리가 필요 없다.
     */
    val groupKey: String,
)
