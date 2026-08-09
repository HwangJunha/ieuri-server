package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * spot 전문 검색 아웃바운드 포트(SPEC-0013 R1-R9).
 *
 * 계약은 "질의 문자열을 받아 순위가 매겨진 spot을 돌려준다"까지다. BM25·토크나이저·인덱스 이름은
 * 이 시그니처에 나타나지 않는다 — 그래야 수용 기준을 인프라 없이 검증할 수 있다(단위 테스트는
 * 이 포트를 스텁해 관련도 순서를 임의로 지정한다).
 *
 * **질의 검증은 이 포트를 호출하기 전에 끝나 있어야 한다**(AC-8.1) — 빈 질의로 여기까지 오면 안 된다.
 */
interface SpotSearchPort {
    /**
     * 반경 안에서 [SpotSearchQuery.text]에 일치하는 spot 한 페이지.
     * 관련도(거리 가중) 내림차순 + id 오름차순으로 정확히 [SpotSearchQuery.limit]건까지.
     *
     * 다음 페이지 존재는 호출자가 [count]와 오프셋으로 판정한다 — 한 건 더 읽는 방식을 쓰지
     * 않는 이유는 어차피 전체 건수를 세고 있어 같은 사실을 두 경로로 구하게 되기 때문이다.
     */
    fun search(query: SpotSearchQuery): List<SpotSearchHit>

    /** [query]와 같은 조건(커서 제외)에 걸리는 전체 건수. */
    fun count(query: SpotSearchQuery): Int

    /**
     * 반경 안에서 제목이 [prefix]로 시작하는 spot(R9). 제목 오름차순으로 [limit]건까지.
     * 관련도가 아니라 접두 일치라 BM25 점수를 돌려주지 않는다.
     */
    fun suggest(prefix: String, language: DomainLocale, center: Coordinates, radiusMeters: Int, limit: Int): List<SpotSuggestionHit>
}

/**
 * 검색 조건.
 *
 * **오프셋 방식이다.** BM25 관련도는 검색 엔진이 계산하는 값이라 "이 점수보다 뒤"를 조건절로
 * 표현할 수 없다(`pdb.score()`는 SELECT 전용). 반면 OFFSET은 인덱스 안으로 밀려 들어가
 * top-(limit+offset) 힙으로 처리되므로, 여기서는 오프셋이 정직한 계약이다.
 *
 * [limit]·[offset]은 호출자가 이미 상한을 적용한 값이다.
 */
data class SpotSearchQuery(
    val text: String,
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val category: SpotCategory?,
    val limit: Int,
    val offset: Int,
)

/**
 * 검색 결과 1건. [relevance]는 거리 가중까지 끝난 최종 정렬 점수이며, 커서에 그대로 실린다 —
 * BM25 원점수가 아니다. 둘이 갈리면 커서가 가리키는 위치와 실제 정렬 순서가 어긋난다.
 */
data class SpotSearchHit(
    val spot: SpotSearchResult,
    val relevance: Double,
)

/** 자동완성 결과 1건. 제안 목록에 필요한 최소 필드만 읽는다. */
data class SpotSuggestionHit(
    val spotId: Long,
    val title: String,
    val category: SpotCategory,
)
